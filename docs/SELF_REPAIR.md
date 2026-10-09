# MegaRepo guarded integrity recovery

This runbook describes the review-branch integrity controls. It does NOT authorize automatic acceptance of third-party CloudStream binaries.

## Safety and event flow

1. Audit Published Plugin Integrity checks published metadata, selected upstream index data, and downloaded package bytes. Every failed or passing run emits an audit-report.json artifact.
2. Track integrity incidents and safely recover trusted upstream corrections accepts only completed master-branch audit runs. It independently checks the run origin and reads the audit artifact.
3. The issue tracker maintains one issue per affected plugin or source. Repeated identical evidence does not create duplicate issues. Only a successful FULL integrity audit can close outstanding managed incidents.
4. If upstream metadata drift is present, the recovery controller separately fetches current upstream catalogs, builds a fresh candidate from a pinned production snapshot, and verifies every candidate package.
5. It dispatches the existing guarded aggregation only if the independent full binary-integrity gate passes AND an audited plugin/source has a matching changed candidate. A per-candidate UTC-day cache marker suppresses repeat dispatches of the same state.
6. A successful dispatched aggregation triggers a FULL follow-up integrity audit. Audit success verifies metadata and bytes, not third-party playback or end-user compatibility.

## Reviewed approval schema

The trusted_binary_approvals.json file starts as an empty list. Every approval requires evidence and review. Illustrative non-authoritative record:

    [
      {
        "plugin": "ExampleProvider",
        "sourceId": "example-source",
        "url": "https://raw.githubusercontent.com/example/repo/builds/ExampleProvider.cs3",
        "version": 4,
        "fileSize": 12345,
        "fileHash": "sha256-0000000000000000000000000000000000000000000000000000000000000000",
        "reviewedBy": "github-reviewer",
        "reviewedAt": "2026-10-09",
        "evidenceUrl": "https://github.com/example/repo/releases",
        "reason": "Reviewed intended upstream release and verified binary fingerprint."
      }
    ]

The candidate manifest MUST independently declare the exact approved fileSize and fileHash. The gate downloads the binary and computes its digest independently. An approval record cannot overwrite incorrect upstream metadata. For a source without trustworthy metadata, request an upstream fix first. A reviewed local metadata override would require a separately audited implementation before use.

A hash newly inserted by upstream is not independently trusted. Only a previous trusted hash for the same URL and provenance, or a reviewed exact approval, can authorize a new publication.

## Legacy unpinned packages

Unchanged, formerly published entries without fileHash can continue only if a positive declared fileSize matches the downloaded bytes. This is size-only legacy verification and does NOT prevent equal-length binary substitution. New or changed unpinned entries remain blocked until verified and approved.

## Full catalog dry run -- October 9, 2026

The review-branch scan checked 543 candidates:
- 432 checksum matches against metadata.
- 104 unchanged size-only legacy entries.
- 14 blocked candidates: six binary-size mismatches, seven untrusted metadata changes, and one package without a declared length.

Among the blockers, GDIndex declares 15,937 bytes upstream but its downloadable package has been observed at 17,206 bytes. Do not accept that difference without upstream release evidence. The 14 blockers are outstanding at time of drafting; production has NOT been altered.

## Release acceptance

- Audit, gate, incident-management and decision unit tests must pass.
- GitHub Actions YAML must parse and the full catalog must be checked.
- Resolve material blockers before merging the fail-closed publication gate, to avoid preventing legitimate scheduled updates.
- Confirm a supervised production run of issue creation/reopening, trusted recovery dispatch, full post-build audit, and dashboard transitions.
- Preserve artifacts and failure reports. Never equate URL reachability with a verified package or functioning playback.

## Escalation cases

- Transient network/source failures: keep previous production and retry with bounded backoff.
- Unknown or changed binary: maintain issue and block untrusted publication.
- Trusted upstream correction: independent preflight, guarded aggregation, complete integrity recheck.
- Post-repair audit failure: remain red and retain evidence.
