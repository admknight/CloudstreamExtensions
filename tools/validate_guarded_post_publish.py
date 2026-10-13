#!/usr/bin/env python3
"""Independently check post-publication full integrity audit against declared holds.

Known deferred updates are NOT treated as repaired. Novel package/metadata
mismatches, source index errors, incomplete scans and suspicious count changes
make the guarded publication verification FAIL, preserving audit evidence.
"""
import argparse
import json
from pathlib import Path


def validate_post_publish(audit, report, plugins, provenance):
    if not all(isinstance(x, dict) for x in (audit, report)):
        raise ValueError("Missing independent audit / production report")
    if not all(isinstance(x, list) for x in (plugins, provenance)):
        raise ValueError("Published catalog/provenance must be lists")
    from audit_package_integrity import plugin_identity
    names = [plugin_identity(p) for p in plugins]
    sources = [
        str(r.get("plugin") or r.get("originalName") or "").strip().casefold()
        for r in provenance if isinstance(r, dict)
    ]
    if (
        len(plugins) != len(provenance) or len(sources) != len(provenance)
        or not all(isinstance(p, dict) for p in plugins)
        or not all(names) or not all(sources)
        or len(set(names)) != len(names) or len(set(sources)) != len(sources)
        or set(names) != set(sources)
    ):
        raise ValueError("Published plugin identities and provenance are inconsistent")
    names = set(names)
    if (
        report.get("releaseEligible") is not True
        or report.get("publicationMethod") != "guarded_per_plugin_compatibility"
        or report.get("uniquePlugins") != len(plugins)
        or report.get("changes", {}).get("removed") != 0
    ):
        raise ValueError("Publication is not a complete guarded release")
    allowed = {
        str(item.get("plugin") or "").casefold()
        for item in report.get("deferredUnverified", [])
    }
    if not allowed.issubset(names) or "" in allowed:
        raise ValueError("Invalid list of held upstream exceptions")
    prior_pinned = {
        str(item.get("plugin") or "").casefold()
        for item in report.get("quarantineIncidents", [])
        if item.get("disposition") == "retained_immutable_previous"
        and item.get("fallbackVerified") is True
    }
    if not prior_pinned.issubset(names) or "" in prior_pinned:
        raise ValueError("Invalid pinned prior binary disposition")
    # A trusted policy pin may differ from the mutable index only in URL.
    # Never classify a package failure, a version/hash/size/status drift,
    # or an unmatched pinned/original URL as an expected policy change.
    policy_pins = {}
    catalog_by_key = {plugin_identity(p): p for p in plugins}
    origin_by_key = {
        str(r.get("plugin") or r.get("originalName") or "").strip().casefold(): r
        for r in provenance
    }
    from urllib.parse import urlparse
    import re
    for entry in report.get("trustedPolicyUpgrades", []):
        if not isinstance(entry, dict):
            raise ValueError("Invalid trusted publisher release evidence")
        key = str(entry.get("plugin") or "").casefold()
        package = catalog_by_key.get(key, {})
        source = origin_by_key.get(key, {})
        url = str(entry.get("url") or "")
        path = urlparse(url).path.split("/")
        if (not key or key in policy_pins or key in allowed
                or entry.get("authorizationKind") != "trusted_upstream_policy_v1"
                or entry.get("pinnedIndexVerified") is not True
                or entry.get("separateBytesVerified") is not True
                or urlparse(url).scheme != "https"
                or urlparse(url).netloc != "raw.githubusercontent.com"
                or len(path) < 5
                or re.fullmatch(r"[a-fA-F0-9]{40}", path[3]) is None
                or path[3].lower() != str(entry.get("immutableCommit") or "").lower()
                or package.get("url") != url
                or package.get("version") != entry.get("version")
                or package.get("fileSize") != entry.get("fileSize")
                or str(package.get("fileHash") or "").lower()
                   != str(entry.get("fileHash") or "").lower()
                or source.get("sourceId") != entry.get("sourceId")
                or source.get("packageUrl") != url):
            raise ValueError("Invalid trusted policy pin in production: " + key)
        policy_pins[key] = entry
    integrity = report.get("integrityHealth", {})
    if integrity.get("unverifiedPreviousCarried") != len(allowed):
        raise ValueError("Deferred upstream exception accounting mismatch")
    if (
        audit.get("publishedCount") != len(plugins)
        or audit.get("scan", {}).get("mode") != "all"
        or audit.get("scan", {}).get("checked") != len(plugins)
    ):
        raise ValueError("Full-published-catalog audit did not complete")
    if not isinstance(audit.get("sourceErrors"), list):
        raise ValueError("Post-publication audit lacks source-error evidence")
    if audit["sourceErrors"]:
        raise ValueError("An upstream index was unavailable at post-publication audit")
    scan = audit.get("scan") or {}
    problems = audit.get("packageProblems")
    if (
        type(scan.get("failed")) is not int
        or not isinstance(problems, list)
        or scan["failed"] != len(problems)
    ):
        raise ValueError("Full-audit package-failure count does not reconcile with evidence")
    unexpected = []
    known = []
    for field in ("metadataDrift", "packageProblems"):
        evidence = audit.get(field)
        if not isinstance(evidence, list):
            raise ValueError("Full audit lacks field " + field)
        for entry in evidence:
            if not isinstance(entry, dict):
                raise ValueError("Invalid audit entry")
            key = str(entry.get("plugin") or "").casefold()
            approved_pin = policy_pins.get(key)
            exact_url_only = (
                field == "metadataDrift"
                and approved_pin is not None
                and entry.get("problem") == "stale_metadata"
                and isinstance(entry.get("differences"), dict)
                and set(entry["differences"]) == {"url"}
                and entry["differences"]["url"].get("published") == approved_pin["url"]
                and entry["differences"]["url"].get("upstream") == approved_pin["originalUrl"]
            )
            if key in allowed or (field == "metadataDrift" and key in prior_pinned) or exact_url_only:
                known.append({"plugin": key, "category": field})
            else:
                unexpected.append({"plugin": key, "category": field})
    if unexpected:
        raise ValueError("Unexpected integrity anomalies: " + json.dumps(unexpected[:30]))
    if audit.get("pass") is True and known:
        raise ValueError("Full audit claimed success despite recorded mismatches")
    if audit.get("pass") is not True and not known:
        raise ValueError("Full audit failed with no attributable known deferred exceptions")
    return {
        "result": "GUARDED_PUBLICATION_VERIFIED_WITH_KNOWN_OPEN_EXCEPTIONS"
                  if allowed else "GUARDED_PUBLICATION_VERIFIED_NO_DEFERRED_EXCEPTIONS",
        "publishedCount": len(plugins),
        "heldUpstreamExceptions": sorted(allowed),
        "trustedPolicyUpgrades": sorted(policy_pins),
        "knownAuditAnomalyCount": len(known),
        "unexpectedAuditAnomalyCount": 0,
        "upstreamProblemsFullyResolved": not bool(allowed or prior_pinned),
        "fullAuditPassed": audit.get("pass") is True,
        "warning": (
            "Known exceptions are not repaired, and old mutable package URLs "
            "are not cryptographically verified backups. Full audit evidence "
            "is retained regardless of verification outcome."
        ),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--plugins", type=Path, required=True)
    parser.add_argument("--provenance", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    def load(path):
        return json.loads(path.read_text(encoding="utf-8"))
    try:
        result = validate_post_publish(
            load(args.audit), load(args.report),
            load(args.plugins), load(args.provenance))
        code = 0
    except Exception as error:
        result = {
            "result": "FAILED_GUARDED_POST_PUBLICATION_VERIFICATION",
            "reason": f"{type(error).__name__}: {error}",
        }
        code = 1
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return code


if __name__=="__main__":
    raise SystemExit(main())
