#!/usr/bin/env python3
"""Read-only classification of published CloudStream package-integrity evidence.

This module never approves binaries or edits a published manifest.
"""
import hashlib
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
    }
