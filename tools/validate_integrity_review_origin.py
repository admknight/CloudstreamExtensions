#!/usr/bin/env python3
"""Ensure privileged issue-sync consumes only successful trusted master reviews."""
import argparse
import json
from pathlib import Path

WORKFLOW_NAME = "Full Catalog Integrity Review (Read Only)"
WORKFLOW_PATH = ".github/workflows/full-integrity-review.yml"
ALLOWED_EVENTS = {"schedule", "push", "workflow_dispatch"}


def validate_origin(metadata, repository, run_id):
    if not isinstance(metadata, dict) or not isinstance(repository, str):
        raise ValueError("Malformed workflow origin")
    if type(run_id) is not int or run_id <= 0:
        raise ValueError("Invalid run ID")
    checks = {
        "repository": (metadata.get("repository") or {}).get("full_name") == repository,
        "head_repository": (metadata.get("head_repository") or {}).get("full_name") == repository,
        "run_id": metadata.get("id") == run_id,
        "workflow_name": metadata.get("name") == WORKFLOW_NAME,
        "workflow_path": metadata.get("path") == WORKFLOW_PATH,
        "branch": metadata.get("head_branch") == "master",
        "status": metadata.get("status") == "completed",
        "conclusion": metadata.get("conclusion") == "success",
        "event": metadata.get("event") in ALLOWED_EVENTS,
    }
    bad = [key for key, passed in checks.items() if not passed]
    if bad:
        raise ValueError("Rejecting untrusted review origin: " + ", ".join(bad))
    return {"repository":repository,"runId":run_id,"event":metadata["event"]}


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-json", required=True, type=Path)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", required=True, type=int)
    args = parser.parse_args(argv)
    result = validate_origin(
        json.loads(args.run_json.read_text(encoding="utf-8")),
        args.repository,args.run_id
    )
    print(json.dumps({"verified": result}, sort_keys=True))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
