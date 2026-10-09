#!/usr/bin/env python3
"""Upsert deduplicated integrity incidents; only full passing audits close them."""
import argparse
import json
import subprocess
from pathlib import Path
from plan_integrity_recovery import plan

REPO = "admknight/CloudstreamExtensions"
MARKER = "<!-- megarepo-integrity:managed -->"
TITLE_PREFIX = "[Integrity] "


def run(command):
    return subprocess.run(command, capture_output=True, text=True, check=True,
                          timeout=60).stdout.strip()


def _body(row):
    encoded = json.dumps(row["findings"], sort_keys=True, indent=2, ensure_ascii=True)
    if len(encoded) > 20000:
        encoded = encoded[:20000] + "\n... truncated; see the audit artifact"
    return (
        MARKER + "\n\nIncident: " + row["key"] + "\n\n"
        + "Affected plugin/source: " + json.dumps(str(row["plugin"])) + "\n\n"
        + "Categories: " + ", ".join(row["categories"]) + "\n\n"
        + "Audit evidence (JSON):\n\n" + encoded + "\n\n"
        + "This is NOT approval to change binaries, checksums, or metadata. "
          "Confirm the intended release or review a local override."
    )


def sync(report, runner=run):
    overview = plan(report)
    known = json.loads(runner([
        "gh", "issue", "list", "--repo", REPO, "--state", "all",
        "--limit", "500", "--json", "number,title,body,state"
    ]))
    if not isinstance(known, list):
        raise ValueError("GitHub CLI issue list response is not an array")
    managed = {row.get("title"): row for row in known
               if isinstance(row, dict)
               and MARKER in (row.get("body") or "")
               and str(row.get("title") or "").startswith(TITLE_PREFIX)}
    results = []
    for incident in overview["incidents"]:
        key = incident["key"]
        title = TITLE_PREFIX + key
        body = _body(incident)
        found = managed.get(title)
        if found:
            number = str(found["number"])
            if found.get("state") == "CLOSED":
                runner(["gh", "issue", "reopen", number, "--repo", REPO])
            if found.get("body") != body:
                runner(["gh", "issue", "edit", number, "--repo", REPO, "--body", body])
                action = "updated"
            else:
                action = "unchanged"
            results.append({"key": key, "action": action, "number": number})
        else:
            url = runner(["gh", "issue", "create", "--repo", REPO,
                          "--title", title, "--body", body])
            results.append({"key": key, "action": "created", "url": url})
            managed[title] = {"title": title, "body": body, "state": "OPEN"}
    scan = report.get("scan") or {}
    full_pass = (
        overview["auditPassed"] and not overview["incidents"]
        and scan.get("mode") == "all" and (report.get("publishedCount") or 0) >= 50
        and not report.get("metadataDrift") and not report.get("sourceErrors")
        and not report.get("packageProblems")
    )
    if full_pass:
        for issue in managed.values():
            if issue.get("state") == "OPEN" and issue.get("number"):
                runner(["gh", "issue", "close", str(issue["number"]), "--repo", REPO,
                        "--reason", "completed"])
                results.append({"key": issue["title"], "action": "closed"})
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, required=True)
    args = parser.parse_args()
    results = sync(json.loads(args.audit.read_text(encoding="utf-8")))
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
