#!/usr/bin/env python3
"""Conservatively recover missing GitHub scheduled runs without changing catalog trust."""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import sys
from urllib import error, parse, request

REPOSITORY = "admknight/CloudstreamExtensions"
BRANCH = "master"
# Never dispatch other workflows or change these IDs from untrusted event data.
WORKFLOWS = (
    ("build.yml", "Guarded aggregator", timedelta(hours=7), timedelta(hours=12)),
    ("audit-package-integrity.yml", "Read-only package audit", timedelta(minutes=150), timedelta(hours=12)),
    ("immutable-package-recovery.yml", "Read-only recovery inventory", timedelta(hours=36), timedelta(hours=48)),
)
API = "https://api.github.com"


def utc_datetime(value: str) -> datetime:
    date = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if date.tzinfo is None:
        raise ValueError("Run timestamps must have a UTC offset")
    return date.astimezone(timezone.utc)


def latest_default_branch_run(runs: list[dict]) -> dict | None:
    matches = [r for r in runs
               if r.get("head_branch") == BRANCH
               and r.get("event") in ("schedule", "workflow_dispatch", "push", "workflow_run")
               and r.get("status") in ("queued", "in_progress", "waiting", "pending", "completed")]
    if not matches:
        return None
    return max(matches, key=lambda r: utc_datetime(r["created_at"]))


def decision(runs: list[dict], now: datetime, stale_after: timedelta,
             retry_after_failure: timedelta) -> tuple[bool, str]:
    latest = latest_default_branch_run(runs)
    if latest is None:
        return False, "no trusted default-branch run baseline; manual investigation required"
    age = now - utc_datetime(latest["created_at"])
    if age < timedelta(0):
        return False, "timestamp in future; skip"
    if latest["status"] != "completed":
        return False, "workflow is already queued or running"
    if age < stale_after:
        return False, f"last run {age} ago; within tolerance"
    if latest.get("conclusion") != "success" and age < retry_after_failure:
        return False, "last run failed; cooldown prevents repeated ineffective retries"
    return True, f"no default-branch run for {age}; gap exceeds tolerance"


def github_api(path: str, token: str, *, method: str = "GET", body: dict | None = None) -> dict:
    url = API + path
    payload = json.dumps(body).encode("utf-8") if body is not None else None
    req = request.Request(url, method=method, data=payload, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + token,
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "megarepo-guarded-schedule-backstop",
        **({"Content-Type": "application/json"} if payload else {}),
    })
    try:
        with request.urlopen(req, timeout=25) as response:
            content = response.read()
            return json.loads(content) if content else {}
    except error.HTTPError as exc:
        raise RuntimeError(f"GitHub API HTTP {exc.code} while calling {method} {path}") from exc


def emit_summary(lines: list[str]) -> None:
    for line in lines:
        print(line)
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with Path(target).open("a", encoding="utf-8") as stream:
            stream.write("\n".join(lines) + "\n")


def main(argv: list[str] | None = None) -> int:
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--execute", action="store_true", help="Dispatch only after guarded staleness verification")
    args = cli.parse_args(argv)
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    event = os.environ.get("GITHUB_EVENT_NAME", "")
    token = os.environ.get("GH_TOKEN", "")
    if repo != REPOSITORY:
        raise SystemExit("Refusing execution outside explicitly allowlisted production repository")
    if args.execute and event not in ("schedule", "workflow_run"):
        raise SystemExit("Dispatch is only allowed on trusted schedule/workflow_run events")
    if not token:
        raise SystemExit("Missing GitHub Actions token")
    now = datetime.now(timezone.utc)
    report = ["### Guarded schedule recovery (independent of package trust)",
              "No checksum approvals, release modifications or direct catalog writes are performed.", ""]
    for filename, label, stale_after, retry_after_failure in WORKFLOWS:
        path = f"/repos/{REPOSITORY}/actions/workflows/{parse.quote(filename)}/runs?branch=master&per_page=100"
        records = github_api(path, token).get("workflow_runs", [])
        eligible, why = decision(records, now, stale_after, retry_after_failure)
        if eligible and args.execute:
            github_api(f"/repos/{REPOSITORY}/actions/workflows/{filename}/dispatches",
                       token, method="POST", body={"ref": BRANCH})
            action = "BACKSTOP DISPATCHED"
        elif eligible:
            action = "DRY RUN: dispatch would be eligible"
        else:
            action = "NO ACTION"
        report.append(f"- **{label}** ({filename}): {action}; {why}")
    emit_summary(report)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError) as exc:
        print(f"Backstop verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
