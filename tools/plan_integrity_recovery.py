#!/usr/bin/env python3
"""Read-only classification of published CloudStream package-integrity evidence.

This module never approves binaries or edits a published manifest.
"""
import hashlib
import argparse
import sys
from pathlib import Path
from audit_package_integrity import plugin_identity
import json
import re


def issue_key(identity):
    raw = str(identity or "").strip().casefold()
    if not raw or len(raw) > 400:
        return ""
    slug = re.sub(r"[^a-z0-9._-]+", "-", raw).strip("._-")[:48] or "item"
    suffix = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:10]
    return "megarepo-integrity:" + slug + "-" + suffix


def plan(audit):
    if not isinstance(audit, dict):
        raise ValueError("Audit report must be a JSON object")
    grouped = {}
    for field, category in (("packageProblems", "package"),
                            ("metadataDrift", "metadata"),
                            ("sourceErrors", "source")):
        findings = audit.get(field) or []
        if not isinstance(findings, list):
            raise ValueError(field + " must be an array")
        for finding in findings:
            if not isinstance(finding, dict):
                continue
            plugin = finding.get("plugin")
            source_id = finding.get("sourceId")
            label = plugin or ("source:" + str(source_id or "unknown"))
            key = issue_key(label)
            if not key:
                continue
            row = grouped.setdefault(
                key, {"key": key, "plugin": str(label),
                      "categories": [], "findings": []}
            )
            if category not in row["categories"]:
                row["categories"].append(category)
            row["findings"].append({"category": category, "evidence": finding})
    if not grouped and (audit.get("fatalError") or audit.get("pass") is not True):
        label = "audit-system"
        grouped[issue_key(label)] = {
            "key": issue_key(label),
            "plugin": label,
            "categories": ["audit-system"],
            "findings": [{"category": "audit-system", "evidence": {
                "fatalError": str(audit.get("fatalError") or "Audit failed without structured findings")
            }}],
        }
    scan = audit.get("scan") or {}
    full_pass = (
        audit.get("pass") is True
        and not grouped
        and isinstance(scan, dict)
        and scan.get("mode") == "all"
        and isinstance(audit.get("publishedCount"), int)
        and audit["publishedCount"] >= 50
        and scan.get("checked") == audit["publishedCount"]
    )
    return {
        "incidents": sorted(grouped.values(), key=lambda row: row["key"]),
        "fullVerifiedPass": full_pass,
        "auditPassed": audit.get("pass") is True,
        "sourceErrors": len(audit.get("sourceErrors") or []),
        "packageFailures": len(audit.get("packageProblems") or []),
        "metadataDrift": len(audit.get("metadataDrift") or []),
        "candidatePreflightNeeded": bool(audit.get("metadataDrift")),
    }


def _index(entries):
    result = {}
    if not isinstance(entries, list):
        raise ValueError("Catalog is not a JSON list")
    for plugin in entries:
        if not isinstance(plugin, dict):
            raise ValueError("Invalid plugin in catalog")
        key = plugin_identity(plugin)
        if not key or key in result:
            raise ValueError("Missing or duplicate plugin identity: " + str(key))
        result[key] = plugin
    return result


def _sources(provenance):
    if not isinstance(provenance, list):
        raise ValueError("Provenance is not a JSON list")
    return {str(p.get("plugin") or p.get("originalName") or "").strip().casefold():
            p.get("sourceId") for p in provenance if isinstance(p, dict)}


def decide_dispatch(audit, gate, candidate, previous, candidate_provenance,
                    candidate_report):
    """Accept only a real independently verified and previously trusted delta."""
    if not (isinstance(audit, dict) and isinstance(gate, dict) and isinstance(candidate_report, dict)):
        return {"dispatch": False, "reason": "invalid evidence"}
    if not audit.get("metadataDrift"):
        return {"dispatch": False, "reason": "no metadata-drift incident"}
    if candidate_report.get("candidateStatus") != "READY" or candidate_report.get("sourceHealth", {}).get("failed") != 0:
        return {"dispatch": False, "reason": "candidate source or publication safety gate not ready"}
    if not gate.get("passed") or gate.get("blockedCount") or gate.get("fatalError"):
        return {"dispatch": False, "reason": "full binary-integrity preflight did not pass",
                "blockedCount": gate.get("blockedCount", "unknown")}
    if gate.get("checked") != len(candidate):
        return {"dispatch": False, "reason": "incomplete candidate binary verification"}
    try:
        new = _index(candidate)
        old = _index(previous)
        provenance = _sources(candidate_provenance)
    except (ValueError, TypeError) as error:
        return {"dispatch": False, "reason": "invalid candidate snapshot: " + str(error)}
    matching = []
    for finding in audit["metadataDrift"]:
        if not isinstance(finding, dict):
            continue
        key = str(finding.get("plugin") or "").strip().casefold()
        source_id = finding.get("sourceId")
        if key and key in new and key in old and provenance.get(key) == source_id and new[key] != old[key]:
            matching.append(key)
    if not matching:
        return {"dispatch": False, "reason": "no independently changed candidate matching audit provenance"}
    digest = hashlib.sha256(json.dumps(candidate, ensure_ascii=False,
                                     sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()
    return {"dispatch": True, "reason": "all candidate binaries pass trusted gate",
            "matchingPlugins": sorted(set(matching)), "candidateDigest": digest}


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--gate", type=Path)
    parser.add_argument("--candidate", type=Path)
    parser.add_argument("--previous", type=Path)
    parser.add_argument("--candidate-provenance", type=Path)
    parser.add_argument("--candidate-report", type=Path)
    parser.add_argument("--decision", type=Path)
    args = parser.parse_args(argv)
    audit = json.loads(args.audit.read_text(encoding="utf-8"))
    initial = plan(audit)
    args.plan.write_text(json.dumps(initial, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.decision:
        if all((args.gate, args.candidate, args.previous,
                args.candidate_provenance, args.candidate_report)):
            decision = decide_dispatch(
                audit, json.loads(args.gate.read_text(encoding="utf-8")),
                json.loads(args.candidate.read_text(encoding="utf-8")),
                json.loads(args.previous.read_text(encoding="utf-8")),
                json.loads(args.candidate_provenance.read_text(encoding="utf-8")),
                json.loads(args.candidate_report.read_text(encoding="utf-8")),
            )
        else:
            decision = {"dispatch": False, "reason": "preflight files unavailable"}
        args.decision.write_text(json.dumps(decision, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"incidents": len(initial["incidents"]),
                      "candidatePreflightNeeded": initial["candidatePreflightNeeded"]}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
