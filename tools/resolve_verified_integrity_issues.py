#!/usr/bin/env python3
"""Resolve only independently verified, per-plugin fixed MegaRepo integrity issues.

Invoked by a downstream issues:write job only after a successful guarded
post-publication verification. All input files come from that SAME audited
catalog snapshot and its own workflow artifact. A different issue type,
missing SHA-256, still-deferred source update, failed binary, source outage,
partial audit, absent plugin, or unverified release cannot be auto-closed.
Issue bodies are never overwritten; a limited closure comment preserves links.
"""
import argparse
import json
import subprocess
from pathlib import Path

from audit_package_integrity import DIGEST_RE, plugin_identity
from plan_integrity_recovery import issue_key

REPO = "admknight/CloudstreamExtensions"
MANAGED = "<!-- megarepo-integrity:managed -->"


def _identity_index(items, *, provenance=False):
    if not isinstance(items, list):
        raise ValueError("Published catalog / provenance must be lists")
    found = {}
    for row in items:
        if not isinstance(row, dict):
            raise ValueError("Malformed plugin/source row")
        key = (str(row.get("plugin") or row.get("originalName") or "").strip().casefold()
               if provenance else plugin_identity(row))
        if not key or key in found:
            raise ValueError("Duplicate or missing catalog identity")
        found[key] = row
    return found


def validate_proven_resolution(audit, guarded_verdict, report, plugins, provenance):
    """Return a list of plugin identities that meet independent resolution gates."""
    if not all(isinstance(x, dict) for x in (audit, guarded_verdict, report)):
        raise ValueError("Post-publication decision files must be objects")
    entries = _identity_index(plugins)
    origins = _identity_index(provenance, provenance=True)
    if set(entries) != set(origins) or len(entries) < 50:
        raise ValueError("Published catalog and provenance coverage differ")
    if (
        guarded_verdict.get("result") not in (
            "GUARDED_PUBLICATION_VERIFIED_WITH_KNOWN_OPEN_EXCEPTIONS",
            "GUARDED_PUBLICATION_VERIFIED_NO_DEFERRED_EXCEPTIONS"
        )
        or guarded_verdict.get("unexpectedAuditAnomalyCount") != 0
        or report.get("releaseEligible") is not True
        or report.get("publicationMethod") != "guarded_per_plugin_compatibility"
        or report.get("uniquePlugins") != len(entries)
        or report.get("changes", {}).get("removed") != 0
        or audit.get("publishedCount") != len(entries)
        or audit.get("scan", {}).get("mode") != "all"
        or audit.get("scan", {}).get("checked") != len(entries)
        or audit.get("sourceErrors")
    ):
        raise ValueError("Incomplete, failed or unguarded publication audit")
    held = report.get("deferredUnverified")
    if not isinstance(held, list):
        raise ValueError("No complete deferred-source list")
    deferred = set()
    for row in held:
        if not isinstance(row, dict):
            raise ValueError("Invalid deferred source entry")
        key = str(row.get("plugin") or "").strip().casefold()
        if not key or key in deferred or key not in entries:
            raise ValueError("Invalid deferred plugin identity")
        deferred.add(key)
    if guarded_verdict.get("heldUpstreamExceptions") != sorted(deferred):
        raise ValueError("Guarded verdict and published deferred list differ")
    if report.get("integrityHealth", {}).get("unverifiedPreviousCarried") != len(deferred):
        raise ValueError("Deferred entry total changed between checks")

    prior_pinned = {
        str(item.get("plugin") or "").strip().casefold()
        for item in report.get("quarantineIncidents", [])
        if isinstance(item, dict)
        and item.get("disposition") == "retained_immutable_previous"
        and item.get("fallbackVerified") is True
    }
    if "" in prior_pinned or not prior_pinned.issubset(set(entries)):
        raise ValueError("Invalid pinned previous-release exception identities")
    if report.get("integrityHealth", {}).get("pinnedPreviousRecovered") != sum(
        1 for item in report.get("quarantineIncidents", [])
        if isinstance(item, dict) and item.get("fallbackThroughRecoveryLock") is True
    ):
        raise ValueError("Pinned previous release count does not reconcile")
    # The independent post-publication verifier has already checked exact
    # publisher-policy immutable pins. Recheck their published/underlying URLs
    # before treating only their URL drift as documented (never a binary error).
    trusted = report.get("trustedPolicyUpgrades", [])
    if not isinstance(trusted, list):
        raise ValueError("Invalid trusted publisher policy evidence")
    policy = {}
    for row in trusted:
        if not isinstance(row, dict):
            raise ValueError("Malformed policy evidence")
        key = str(row.get("plugin") or "").strip().casefold()
        if (not key or key in policy or key not in entries
                or key in deferred or row.get("authorizationKind") != "trusted_upstream_policy_v1"
                or row.get("pinnedIndexVerified") is not True
                or row.get("separateBytesVerified") is not True
                or row.get("url") != entries[key].get("url")
                or row.get("url") != origins[key].get("packageUrl")
                or row.get("sourceId") != origins[key].get("sourceId")
                or row.get("version") != entries[key].get("version")
                or row.get("fileSize") != entries[key].get("fileSize")
                or str(row.get("fileHash") or "").lower()
                   != str(entries[key].get("fileHash") or "").lower()):
            raise ValueError("Policy pin failed independent resolution corroboration")
        policy[key] = row
    if guarded_verdict.get("trustedPolicyUpgrades", []) != sorted(policy):
        raise ValueError("Guarded verdict and publisher-policy upgrade identities differ")
    anomalies = set()
    all_problems = []
    for field in ("metadataDrift", "packageProblems"):
        rows = audit.get(field)
        if not isinstance(rows, list):
            raise ValueError("Missing complete independent audit findings")
        for row in rows:
            if not isinstance(row, dict):
                raise ValueError("Malformed integrity finding")
            key = str(row.get("plugin") or "").strip().casefold()
            if not key or key not in entries:
                raise ValueError("Audit contains unknown/ambiguous plugin")
            documented_policy_url_drift = (
                field == "metadataDrift" and key in policy
                and row.get("problem") == "stale_metadata"
                and isinstance(row.get("differences"), dict)
                and set(row["differences"]) == {"url"}
                and row["differences"]["url"].get("published") == policy[key]["url"]
                and row["differences"]["url"].get("upstream") == policy[key]["originalUrl"]
            )
            if not (key in deferred or
                    (field == "metadataDrift" and key in prior_pinned)
                    or documented_policy_url_drift):
                raise ValueError("Unexpected unapproved post-publication discrepancy")
            anomalies.add(key)
            all_problems.append((field,key))
    if (
        guarded_verdict.get("knownAuditAnomalyCount") != len(all_problems)
        or guarded_verdict.get("upstreamProblemsFullyResolved") != (not bool(deferred or prior_pinned))
        or guarded_verdict.get("fullAuditPassed") != (audit.get("pass") is True)
    ):
        raise ValueError("Guarded audit verdict does not match underlying findings")
    if audit.get("pass") is True and (anomalies or deferred):
        raise ValueError("Passing audit contains known unresolved problems")

    eligible = []
    for key, entry in entries.items():
        if key in anomalies or key in deferred:
            continue
        digest = entry.get("fileHash")
        length = entry.get("fileSize")
        if not isinstance(digest, str) or not DIGEST_RE.fullmatch(digest):
            continue
        if type(length) is not int or length <= 0:
            continue
        source = origins[key]
        if not source.get("sourceId") or source.get("packageUrl") != entry.get("url"):
            continue
        # Full audit checked ALL downloaded packages. A checksum-bearing entry
        # without a packageProblem had matching size and SHA-256 at audit time.
        eligible.append(key)
    return sorted(eligible)


def run(command):
    return subprocess.run(
        command, text=True, capture_output=True, check=True, timeout=60
    ).stdout.strip()


def resolve(audit, guarded_verdict, report, plugins, provenance, *,
            verifier_run_id, runner=run):
    if type(verifier_run_id) is not int or verifier_run_id <= 0:
        raise ValueError("A genuine completed audit workflow run ID is required")
    eligible = validate_proven_resolution(
        audit, guarded_verdict, report, plugins, provenance
    )
    allowed = set(eligible)
    raw = runner([
        "gh", "issue", "list", "--repo", REPO, "--state", "all",
        "--limit", "500", "--json", "number,title,body,state"
    ])
    issues = json.loads(raw)
    if not isinstance(issues, list):
        raise ValueError("Issue API response was not a list")
    actions = []
    seen = set()
    for row in issues:
        if not isinstance(row, dict):
            continue
        number = row.get("number")
        title = str(row.get("title") or "")
        body = str(row.get("body") or "")
        if MANAGED not in body or row.get("state") != "OPEN":
            continue
        for key in allowed:
            if title != "[Integrity] " + issue_key(key):
                continue
            if type(number) is not int or number <= 0 or number in seen:
                raise ValueError("Duplicate/malformed managed issue ID")
            seen.add(number)
            note = (
                "**Automated static integrity-resolution evidence:** "
                f"The complete post-publication audit [run {verifier_run_id}]"
                f"(https://github.com/{REPO}/actions/runs/{verifier_run_id}) "
                "checked every published package and source index. This plugin "
                "is no longer on the guarded deferred list, no metadata or "
                "binary discrepancy is reported for it, and its published "
                "SHA-256 and file size were independently verified. The "
                "original investigation and release evidence remain intact. "
                "**This closes only this static catalog-integrity incident; "
                "runtime playback and third-party release authorship are "
                "not certified.**"
            )
            runner([
                "gh", "issue", "comment", str(number), "--repo", REPO,
                "--body", note
            ])
            runner([
                "gh", "issue", "close", str(number), "--repo", REPO,
                "--reason", "completed"
            ])
            actions.append({"issue": number, "plugin": key, "action": "closed_verified"})
    return {"eligibleVerifiedPlugins": len(eligible), "closedIssues": actions,
            "openIncidentsNotClosed": True, "runId": verifier_run_id}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("audit", "guarded-verdict", "report", "plugins", "provenance"):
        parser.add_argument("--" + arg, type=Path, required=True)
    parser.add_argument("--run-id", type=int, required=True)
    args = parser.parse_args()
    def read(path):
        return json.loads(path.read_text(encoding="utf-8"))
    result = resolve(
        read(args.audit), read(args.guarded_verdict), read(args.report),
        read(args.plugins), read(args.provenance),
        verifier_run_id=args.run_id
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
