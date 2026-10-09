#!/usr/bin/env python3
"""One-time verified cleanup of old merged MegaRepo pull-request branches.

No auto-discovery, no force, no deletion outside the explicit allowlist.
Destructive mode is enabled only by a push to the production master branch.
"""
import argparse
import json
import os
import subprocess
import sys
from urllib.parse import quote

REPO = "admknight/CloudstreamExtensions"
# Pin the exact branch head that GitHub recorded when each PR was merged.
COMPLETED = {
    10: ("feature/read-only-extension-explorer", "8f82bdf095ad1988442c08dc6f4cde2645e5c3b2"),
    11: ("feature/link-personal-bundle-builder", "0a913b72c794d5e83f265410bf6a9f41dfee9a6d"),
    12: ("fix/refresh-upstream-metadata-every-3h", "0a431c886ecde847a04e13458def54d80a8d7178"),
    13: ("feature/read-only-package-integrity-audit", "1260a88985003c36c2c79b80c993c2fd9d80514c"),
    14: ("docs/clear-user-journeys", "7b42d49ccea9bc4606fef3fc62b752e8796cc452"),
    15: ("docs/focused-achievement-wording", "d811d367fa3277794d93f569263f1a3305ef5552"),
    19: ("feature/integrity-incident-monitor-20261009", "3eb820fbf8e638fb7023705c68d50a231baaf073"),
    21: ("fix/audit-origin-verification-20261009", "475b80de191dcb07359dba17a85571e707391be3"),
    35: ("docs/upstream-integrity-evidence-20261009", "49d7a65d21c97d5ca03b03ea1bac69f26c67ebe0"),
    36: ("docs/upstream-maintainer-requests-20261009", "d74df47ef52f415419473dda8ba1ad0accee148b"),
    41: ("feature/automated-readonly-integrity-20261009", "bfcaf822c414c1d21e14ab3f276fcb5434d61d93"),
    42: ("ci/verify-integrity-review-on-master-push-20261009", "5ce115ae5eaa8f3c9b0c8cac6a461e7eb4c71359"),
    48: ("feature/automated-full-scan-incidents-20261009", "f1db32fdfe2c3bc9144a5b8f7a0ceda7a31cfeed"),
    50: ("feature/immutable-binary-recovery-index-20261009", "0a58d1b71f3e76e61d9bf8a8d6d401a1f7b68afb"),
    51: ("feature/consume-immutable-fallbacks-20261009", "e48adf5de708823de5d3e04c655607eae0c928fe"),
    52: ("feature/guarded-catalog-release-candidate-20261009", "ad1ee667587beda6cab974ede89785064892194a"),
    53: ("release/guarded-production-aggregation-20261009", "bd75178f5467d9bbfe63279486b5bbb206e32a01"),
    54: ("feature/verified-integrity-incident-resolution-20261009", "96ea519a2c30655d8b5bcff874e1f8e8f02f3a53"),
    55: ("automation/recovery-readiness-guarded-20261010", "10af87318625bcc3de9603b6257844b9733660db"),
    57: ("automation/postpublish-integrity-reconcile-20261010", "d865de4675398836402445cc2232ebda4d074e31"),
    58: ("automation/prevent-premature-integrity-closure-20261010", "dfc33b39d59a46de2c1f3415b45cf4a861e0ba7a"),
    59: ("fix/verified-immutable-local-recovery-20261010", "b95f66941f42fbf92736deac56fd84ba65806609"),
    60: ("fix/netmovie-verified-immutable-onboarding-20261010", "8aa61ad0cce8f52570c2bf078b217b187166a794"),
}
# The cleanup PR's own branch may be deleted after that PR is merged.
# This one is constrained by its exact merged-PR head rather than a
# hardcoded SHA, since creating this file changes that SHA.
SELF_PR = 61
SELF_BRANCH = "maintenance/verified-one-time-branch-cleanup-20261010"
PRESERVE = {
    "master", "builds", "custom-builds", "health-history",
    "audit/provider-runtime-contract", "fix/provider-link-contract",
    "backup/guarded-recovery-stage2-before-reconcile-20261009",
    "feature/reconciled-integrity-reviews-20261009",
    "feature/per-plugin-integrity-selection-20261009",
    "feature/guarded-recovery-stage2-20261009",
    "feature/guarded-self-repair-20261009",
}


def targets():
    return [(number, branch, sha) for number, (branch, sha) in sorted(COMPLETED.items())] + [
        (SELF_PR, SELF_BRANCH, None)
    ]


def is_eligible(branch_name, expected_sha, branch, pr, open_heads):
    if branch_name in PRESERVE or branch_name in open_heads:
        return False
    if not isinstance(branch, dict) or not isinstance(pr, dict):
        return False
    branch_sha = (branch.get("commit") or {}).get("sha")
    head = pr.get("head") or {}
    base = pr.get("base") or {}
    if (not branch_sha or branch.get("name") != branch_name
            or branch.get("protected") is not False
            or (expected_sha is not None and branch_sha != expected_sha)
            or pr.get("state") != "closed" or not pr.get("merged_at")
            or head.get("ref") != branch_name or head.get("sha") != branch_sha
            or (head.get("repo") or {}).get("full_name") != REPO
            or base.get("ref") != "master"
            or (base.get("repo") or {}).get("full_name") != REPO):
        return False
    return True


def gh_json(path):
    result = subprocess.run(["gh", "api", path], capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GitHub read failed for " + path + ": " + result.stderr[:300])
    return json.loads(result.stdout)


def confirm_context():
    if (os.environ.get("GITHUB_REPOSITORY") != REPO
            or os.environ.get("GITHUB_EVENT_NAME") != "push"
            or os.environ.get("GITHUB_REF") != "refs/heads/master"):
        raise RuntimeError("Refusing branch deletion outside a master-branch push")
    current_sha = os.environ.get("GITHUB_SHA")
    if not current_sha or len(current_sha) != 40:
        raise RuntimeError("Missing GitHub push commit")
    head = gh_json(f"repos/{REPO}/git/ref/heads/master")
    if (head.get("object") or {}).get("sha") != current_sha:
        raise RuntimeError("Master has advanced since this cleanup run started")


def audit_and_prune(apply=False):
    if apply:
        confirm_context()
    open_prs = gh_json(f"repos/{REPO}/pulls?state=open&per_page=100")
    if not isinstance(open_prs, list) or len(open_prs) == 100:
        raise RuntimeError("Cannot establish complete active PR inventory")
    open_heads = {
        (pr.get("head") or {}).get("ref")
        for pr in open_prs
        if ((pr.get("head") or {}).get("repo") or {}).get("full_name") == REPO
    }
    results = []
    for number, name, sha in targets():
        encoded = quote(name, safe="/")
        try:
            branch = gh_json(f"repos/{REPO}/branches/{encoded}")
        except RuntimeError as err:
            if "HTTP 404" in str(err):
                results.append({"branch": name, "result": "already_absent"})
                continue
            raise
        pr = gh_json(f"repos/{REPO}/pulls/{number}")
        if not is_eligible(name, sha, branch, pr, open_heads):
            results.append({"branch": name, "result": "retained_guard_not_satisfied"})
            continue
        if not apply:
            results.append({"branch": name, "result": "eligible_dry_run"})
            continue
        # Re-read the branch just before deleting; any changed head fails closed.
        fresh = gh_json(f"repos/{REPO}/branches/{encoded}")
        if not is_eligible(name, sha, fresh, pr, open_heads):
            results.append({"branch": name, "result": "retained_head_changed"})
            continue
        deletion = subprocess.run(
            ["gh", "api", "-X", "DELETE", f"repos/{REPO}/git/refs/heads/{name}"],
            capture_output=True, text=True, check=False,
        )
        if deletion.returncode != 0:
            raise RuntimeError("Deletion rejected for " + name + ": " + deletion.stderr[:300])
        results.append({"branch": name, "result": "deleted"})
    return {
        "repository": REPO, "mode": "apply" if apply else "dry_run",
        "protectedBranchNames": sorted(PRESERVE),
        "targetsChecked": len(targets()), "results": results,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Requires an authenticated master push and exact verified merged PRs")
    args = parser.parse_args()
    result = audit_and_prune(args.apply)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    sys.exit(main())
