#!/usr/bin/env python3
"""Safely sync full-catalog candidate integrity exceptions to existing GH issues.

The full reviewer provides JSON evidence, not instructions. No automatic issue
closure, binary approval, workflow dispatch, or catalog changes occur here.
The updater changes only a delimited machine-managed section of issue bodies.
"""
import argparse
import json
import subprocess
from pathlib import Path

from plan_integrity_recovery import issue_key

REPO = "admknight/CloudstreamExtensions"
MANAGED = "<!-- megarepo-integrity:managed -->"
BEGIN = "<!-- megarepo-candidate-evidence:start -->"
END = "<!-- megarepo-candidate-evidence:end -->"


def validate(review, verification):
    if not isinstance(review, dict) or not isinstance(verification, dict):
        raise ValueError("Evidence must be a JSON object")
    candidate_count = review.get("integrityHealth", {}).get("candidateChecked")
    blocked_count = review.get("integrityHealth", {}).get("changedOrFailedCandidates")
    blocked = verification.get("blocked")
    if (
        review.get("releaseAuthorized") is not False
        or review.get("candidateStatus") != "REVIEW ONLY - NOT APPROVED FOR PUBLICATION"
        or review.get("previewPolicy") != "compatibility"
        or type(candidate_count) is not int or candidate_count < 50
        or verification.get("checked") != candidate_count
        or type(blocked_count) is not int
        or blocked_count != verification.get("blockedCount")
        or not isinstance(blocked, list)
        or len(blocked) != blocked_count
    ):
        raise ValueError("Incomplete or inconsistent full integrity review artifact")
    if review.get("sourceHealth", {}).get("failed") != 0:
        raise ValueError("Source index was not healthy")
    incidents = review.get("quarantineIncidents")
    if not isinstance(incidents, list):
        raise ValueError("Missing source quarantine evidence")
    by_key = {}
    for finding in blocked:
        if not isinstance(finding, dict):
            raise ValueError("Malformed blocked plugin record")
        key = str(finding.get("plugin") or "").strip().casefold()
        if not issue_key(key) or key in by_key:
            raise ValueError("Duplicate/invalid blocked plugin identity")
        if not finding.get("sourceId") or not finding.get("url"):
            raise ValueError("Blocked plugin has no source or download URL")
        by_key[key] = finding
    selection_keys = {
        str(row.get("plugin") or "").strip().casefold()
        for row in incidents if row.get("disposition") != "previous_absent_from_candidate"
    }
    if set(by_key) != selection_keys:
        raise ValueError("Candidate blockers do not match selection incidents")
    return sorted(by_key.values(), key=lambda row: row["plugin"])


def _evidence(finding, run_id):
    source = str(finding.get("sourceId") or "")
    plugin = str(finding.get("plugin") or "")
    evidence = {
        "sourceId": source,
        "plugin": plugin,
        "candidateVersion": finding.get("version"),
        "candidateUrl": finding.get("url"),
        "declaredFileSize": finding.get("expectedFileSize"),
        "actualFileSize": finding.get("actualFileSize"),
        "observedActualSHA256": finding.get("actualFileHash"),
        "verificationStatus": finding.get("verification"),
        "classification": finding.get("reason"),
    }
    data = json.dumps(evidence, indent=2, ensure_ascii=True, sort_keys=True)
    if len(data) > 12000:
        raise ValueError("Oversize candidate evidence; refusing to publish unbounded text")
    return (
        BEGIN + "\n\n"
        + "### Latest candidate-integrity observation (unverified release)\n\n"
        + "Source: [" + str(run_id) + "](https://github.com/" + REPO
        + "/actions/runs/" + str(run_id) + ")\n\n"
        + "```json\n" + data.replace("```", "` ` `")
        + "\n```\n\n"
        + "A matching observed checksum is NOT approval of a changed upstream "
          "binary. This record is generated from a read-only full-catalog review; "
          "published metadata and previous plugin entries are unchanged.\n\n"
        + END
    )


def _upsert_block(body, block):
    body = str(body or "")
    b = body.find(BEGIN)
    e = body.find(END)
    if (b == -1) != (e == -1):
        raise ValueError("Malformed existing issue evidence boundaries")
    if b >= 0:
        if body.find(BEGIN, b+len(BEGIN)) != -1 or body.find(END, e+len(END)) != -1:
            raise ValueError("Duplicate evidence boundaries")
        if e < b:
            raise ValueError("Malformed evidence section order")
        return body[:b] + block + body[e+len(END):]
    return body.rstrip() + ("\n\n" if body.strip() else "") + block


def run(command):
    return subprocess.run(
        command, text=True, capture_output=True, timeout=60, check=True
    ).stdout.strip()


def sync(review, verification, run_id, runner=run):
    if type(run_id) is not int or run_id <= 0:
        raise ValueError("Invalid workflow run id")
    findings = validate(review, verification)
    issues = json.loads(runner([
        "gh", "issue", "list", "--repo", REPO, "--state", "all",
        "--limit", "500", "--json", "number,title,body,state"
    ]))
    if not isinstance(issues, list):
        raise ValueError("GitHub issue list must be a JSON array")
    known = {
        str(row.get("title") or ""): row
        for row in issues if isinstance(row, dict) and
        MANAGED in str(row.get("body") or "")
    }
    result = []
    for finding in findings:
        identity = issue_key(finding["plugin"])
        if not identity:
            continue
        title = "[Integrity] " + identity
        evidence = _evidence(finding, run_id)
        existing = known.get(title)
        if existing:
            number = str(existing["number"])
            original = str(existing.get("body") or "")
            replacement = _upsert_block(original, evidence)
            if existing.get("state") == "CLOSED":
                runner(["gh", "issue", "reopen", number, "--repo", REPO])
            if replacement != original:
                runner(["gh", "issue", "edit", number, "--repo", REPO, "--body", replacement])
                action = "updated"
            else:
                action = "unchanged"
            result.append({"plugin": finding["plugin"], "issue": number, "action": action})
        else:
            body = (
                MANAGED + "\n\n**Affected plugin:** " + finding["plugin"]
                + "\n\n**Incident:** " + identity
                + "\n\n" + evidence
            )
            url = runner(["gh", "issue", "create", "--repo", REPO,
                          "--title", title, "--body", body])
            result.append({"plugin": finding["plugin"], "action": "created", "url": url})
            known[title] = {"number": None, "title": title, "body": body, "state": "OPEN"}
    # A passing candidate preview cannot close published integrity issues.
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--review", type=Path, required=True)
    parser.add_argument("--verification", type=Path, required=True)
    parser.add_argument("--run-id", type=int, required=True)
    args = parser.parse_args()
    review = json.loads(args.review.read_text(encoding="utf-8"))
    verification = json.loads(args.verification.read_text(encoding="utf-8"))
    changes = sync(review, verification, args.run_id)
    print(json.dumps({"processed": len(changes), "changes": changes}, indent=2))
    return 0


if __name__ == "__main__":
    main()
