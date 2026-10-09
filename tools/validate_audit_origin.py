#!/usr/bin/env python3
"""Validate a completed published-integrity audit before using its artifact.

Audits are read-only, but incident automation runs with issues:write.
Never trust an arbitrary workflow_run or manually supplied run ID.
"""
import argparse
import json
from pathlib import Path

WORKFLOW = ".github/workflows/audit-package-integrity.yml"
WORKFLOW_NAME = "Audit Published Plugin Integrity"
ALLOWED_EVENTS = frozenset({"schedule", "push", "workflow_dispatch"})


def verify_origin(run, repository, run_id):
    if not isinstance(run, dict):
        raise ValueError("Run metadata must be a JSON object")
    if not isinstance(repository, str) or repository.count("/") != 1:
        raise ValueError("Invalid expected repository")
    if not str(run_id).isdigit() or int(run_id) < 1:
        raise ValueError("Invalid run ID")
    # GitHub returns repository-relative workflow paths (WITHOUT leading slash).
    checks = (
        (run.get("id") == int(run_id), "run_id"),
        (run.get("name") == WORKFLOW_NAME, "workflow_name"),
        (run.get("path") == WORKFLOW, "workflow_path"),
        (run.get("head_branch") == "master", "head_branch"),
        (run.get("status") == "completed", "status"),
        (run.get("event") in ALLOWED_EVENTS, "event"),
        ((run.get("head_repository") or {}).get("full_name") == repository,
         "head_repository"),
        ((run.get("repository") or {}).get("full_name") == repository,
         "repository"),
    )
    failed = [field for ok, field in checks if not ok]
    if failed:
        raise ValueError("Rejected audit workflow_run metadata: " + ", ".join(failed))
    return {"runId": int(run_id), "repository": repository,
            "event": run["event"], "workflowPath": WORKFLOW}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-json", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    payload = json.loads(args.run_json.read_text(encoding="utf-8"))
    print("Verified audit origin:", json.dumps(
        verify_origin(payload, args.repository, args.run_id), sort_keys=True
    ))


if __name__ == "__main__":
    main()
