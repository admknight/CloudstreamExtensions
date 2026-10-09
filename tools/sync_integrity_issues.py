#!/usr/bin/env python3
"""Create or update tracked GitHub issues from independent integrity audit evidence."""
import argparse
import json
import subprocess
from pathlib import Path
from plan_integrity_recovery import plan

REPO = "admknight/CloudstreamExtensions"
MARKER = "<!-- megarepo-integrity:managed -->"

def run(args):
    return subprocess.run(args, capture_output=True, text=True, check=True, timeout=60).stdout.strip()

def sync(report, runner=run):
    planned = plan(report, None)
    known = json.loads(runner(["gh", "issue", "list", "--repo", REPO, "--state", "all",
                               "--limit", "200", "--json", "number,title,body,state"]))
    results = []
    for problem in planned["problems"]:
        key = problem["key"]
        title = "[Integrity] " + key
        evidence = json.dumps(problem["finding"], sort_keys=True, indent=2, ensure_ascii=True)
        body = (MARKER + "\n\n**Affected:** " + key + "\n\n**Category:** "
                + problem["kind"] + "\n\nEvidence (JSON):\n\n"
                + evidence + "\n\nRequires verification; do not approve unexpected binaries.")
        found = next((x for x in known if x.get("title") == title and MARKER in (x.get("body") or "")), None)
        if found:
            number = str(found["number"])
            if found.get("state") == "CLOSED":
                runner(["gh", "issue", "reopen", number, "--repo", REPO])
            runner(["gh", "issue", "edit", number, "--repo", REPO, "--body", body])
            results.append({"key": key, "action": "updated", "number": number})
        else:
            url = runner(["gh", "issue", "create", "--repo", REPO,
                          "--title", title, "--body", body])
            results.append({"key": key, "action": "created", "url": url})
    return results

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, required=True)
    args = parser.parse_args()
    result = sync(json.loads(args.audit.read_text(encoding="utf-8")))
    print(json.dumps(result, indent=2))

if __name__ == "__main__":
    main()
