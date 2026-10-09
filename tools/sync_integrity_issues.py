#!/usr/bin/env python3
"""Upsert managed issues for audit problems; never close candidate incidents here.

Only the independent guarded post-publication resolver may close individual
verified integrity incidents. A full published audit cannot prove that an
unpublished candidate binary has become trustworthy.
"""
import argparse
import json
import subprocess
from pathlib import Path
from plan_integrity_recovery import plan

REPO = "admknight/CloudstreamExtensions"
MARKER = "<!-- megarepo-integrity:managed -->"
TITLE_PREFIX = "[Integrity] "
PUBLISHED_BEGIN = "<!-- megarepo-published-audit:start -->"
PUBLISHED_END = "<!-- megarepo-published-audit:end -->"


def run(command):
    return subprocess.run(
        command, capture_output=True, text=True, check=True, timeout=60
    ).stdout.strip()


def issue_body(row):
    evidence = json.dumps(row["findings"], sort_keys=True, indent=2, ensure_ascii=True)
    if len(evidence) > 20000:
        evidence = evidence[:20000] + "\n... truncated; see the audit artifact"
    return (
        MARKER + "\n\nIncident: " + row["key"] + "\n\n"
        + "Affected plugin/source: " + json.dumps(str(row["plugin"])) + "\n\n"
        + "Categories: " + ", ".join(row["categories"]) + "\n\n"
        + "Audit evidence (JSON):\n\n" + evidence + "\n\n"
        + "This is NOT approval to change binaries, checksums, or metadata. "
          "Confirm the intended release or review an explicit local override."
    )


def _update_report_preserving_manual_notes(existing, generated):
    """Never wipe previously authored issue evidence or candidate-scan notes."""
    if existing == generated:
        return existing
    report_section = (
        PUBLISHED_BEGIN + "\n"
        + generated.removeprefix(MARKER).strip()
        + "\n" + PUBLISHED_END
    )
    start = existing.find(PUBLISHED_BEGIN)
    stop = existing.find(PUBLISHED_END)
    if (start == -1) != (stop == -1):
        raise ValueError("Malformed published-audit evidence boundaries")
    if start >= 0:
        if stop < start or existing.find(PUBLISHED_BEGIN, start+len(PUBLISHED_BEGIN)) >= 0:
            raise ValueError("Duplicate/misordered published-audit evidence blocks")
        return existing[:start] + report_section + existing[stop+len(PUBLISHED_END):]
    return existing.rstrip() + "\n\n" + report_section


def sync(report, runner=run):
    overview = plan(report)
    known = json.loads(runner(
        ["gh", "issue", "list", "--repo", REPO, "--state", "all",
         "--limit", "500", "--json", "number,title,body,state"]
    ))
    if not isinstance(known, list):
        raise ValueError("GitHub CLI issue list response is not an array")
    managed = {
        row.get("title"): row for row in known if isinstance(row, dict)
        and MARKER in (row.get("body") or "")
        and str(row.get("title") or "").startswith(TITLE_PREFIX)
    }
    changes = []
    for item in overview["incidents"]:
        title = TITLE_PREFIX + item["key"]
        body = issue_body(item)
        existing = managed.get(title)
        if existing and existing.get("number") is not None:
            number = str(existing["number"])
            if existing.get("state") == "CLOSED":
                runner(["gh", "issue", "reopen", number, "--repo", REPO])
            merged = _update_report_preserving_manual_notes(
                str(existing.get("body") or ""), body
            )
            if merged != existing.get("body"):
                runner(["gh", "issue", "edit", number, "--repo", REPO,
                        "--body", merged])
                action = "updated"
            else:
                action = "reopened" if existing.get("state") == "CLOSED" else "unchanged"
            changes.append({"key": item["key"], "action": action, "number": number})
        else:
            url = runner(["gh", "issue", "create", "--repo", REPO,
                          "--title", title, "--body", body])
            changes.append({"key": item["key"], "action": "created", "url": url})
            managed[title] = {"title": title, "body": body, "state": "OPEN"}
    # Never close issues based solely on a published audit. That could
    # incorrectly close candidate-only incidents for never-published plugins.
    # The independently gated post-publication resolver handles closure.
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads(args.audit.read_text(encoding="utf-8"))
    print(json.dumps(sync(report), indent=2))


if __name__ == "__main__":
    main()
