#!/usr/bin/env python3
"""Conservative action plan for hourly audit reports; does not authorize binary changes.

A matching trusted digest can request guarded aggregation. Other mismatches go
to GitHub Issues for review. Never infer trust from URL reachability, file size,
or a changed checksum alone.
"""
import argparse
import json
import re
import sys
from pathlib import Path

IDENTITY = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,100}$")


def issue_key(plugin):
    raw = str(plugin or "").strip().casefold()
    if not IDENTITY.fullmatch(raw):
        return ""
    return "megarepo-integrity:" + raw


def plan(audit, published, upstream=None):
    problems = []
    seen = set()
    for group, field in [("packageProblems", "package"), ("metadataDrift", "metadata"), ("sourceErrors", "source")]:
        for finding in audit.get(group, []):
            if not isinstance(finding, dict):
                continue
            name = str(finding.get("plugin") or finding.get("sourceId") or "")
            key = issue_key(name)
            if not key:
                continue
            dedup = (key, group)
            if dedup in seen:
                continue
            seen.add(dedup)
            problems.append({"key": key, "kind": field, "plugin": name, "finding": finding})
    # Do not automatically dispatch merely because the audit passed: a repaired
    # catalog still needs an independently authenticated upstream checksum.
    return {"pass": bool(audit.get("pass")), "problems": problems,
            "dispatch": False, "requiresReview": bool(problems)}


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        audit = json.loads(args.audit.read_text(encoding="utf-8"))
        result = plan(audit, None)
    except Exception as e:
        result = {"pass": False, "problems": [], "dispatch": False,
                  "requiresReview": True, "fatalError": f"{type(e).__name__}: {e}"}
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k:v for k,v in result.items() if k != "problems"}, sort_keys=True))
    return 0 if "fatalError" not in result else 1


if __name__ == "__main__":
    sys.exit(main())
