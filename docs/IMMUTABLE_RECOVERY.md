# Immutable Recovery Inventory for MegaRepo

The **Immutable Published Package Recovery Revisions** workflow produces an auditable list of previously published `.cs3` bytes that can still be downloaded at a commit-specific GitHub URL and that match their **already published** SHA-256 and `fileSize`.

This is **not** an alternate package repository, an upstream auto-approval list, or a deployed rollback system. Its first release is strictly READ ONLY.

## Verification model

1. Checkout one immutable `builds` snapshot; its `plugins.json` and `provenance.json` must reconcile exactly.
2. Use only the previously published checksum/length as a fingerprint. Do not manufacture hashes or accept a newly introduced upstream digest as a preapproved release.
3. Resolve `raw.githubusercontent.com/OWNER/REPO/REF/path.cs3` to the full Git commit for `REF`.
4. Fetch that specific commit-pinned file; independently compare its exact length and SHA-256 against the already published record.
5. If the current ref points to different bytes, search a **bounded** portion of the same package's commit history and verify immutable prior revisions until one matches.
6. Retain one lock entry only upon an actual content match. Log all missing hashes, unsupported URLs, inaccessible source commits, network failures, and unmatched history separately. The complete index is retained as a GitHub Actions artifact.

Each verified entry records the original URL, full commit SHA, immutable raw URL, original package source, version, size and published checksum. Its trust class is `matches_previously_published_sha256_and_size`, and `releaseAuthorVerified` is always `false`.

## Why this matters

Mutable `builds/Plugin.cs3` references can change at any time. Retaining the old catalog metadata alone does **not** preserve its original bytes. An immutable, reverified link can preserve an *identical previously published package* without automatically accepting any new upstream binary.

**Limits:** A matched checksum proves identical bytes relative to existing MegaRepo metadata, not cryptographic release authorship, malware absence, or working playback. GitHub repository deletion or artifact unavailability can still make the pinned URL inaccessible later. Entries without a valid published SHA-256/size cannot be reconstructed safely from file lengths alone. Other origins, including GitLab and direct host URLs, require separately implemented and tested immutable-addressing rules.

## Execution

The job runs daily at **05:23 UTC**, on relevant `master` source changes, and can be dispatched manually. It carries `contents: read` GitHub permissions only, never modifies `builds`, never pushes a branch, and never dispatches the aggregator. It has a bounded 16-commit fallback search for changed packages and at most six concurrent package requests.

Artifacts contain `immutable-recovery.json` with `immutableMatches`, `matchedCurrentCommit`, `matchedPriorCommit`, `unmatchedReasons` and complete verified entries. The output includes `autoPublicationAuthorized: false`.

## Before allowing automatic rollback

A separate publication change must validate that a recovery lock matches the same package identity, original URL, source provenance, published SHA-256, length and version from one consistent catalog snapshot. It must freshly download the pinned artifact to verify bytes, preserve the user-visible plugin identity and be tested inside the CloudStream repository installer. Publication metadata, source counts, release diff, health status, and post-build integrity auditing must be reconciled atomically before deployment. **Do not use this index to approve a different/new binary.**

The still-unresolved upstream incidents and the draft [guarded recovery PR #20](https://github.com/admknight/CloudstreamExtensions/pull/20) remain separate.

## Byte-identical fallback consumption in catalog reviews

A second, separately tested component, `tools/verified_immutable_fallback.py`, can
consume the recovered fingerprints **in read-only catalog previews**. It requires
that the inventory's published plugin and provenance SHA-256 snapshots match
exactly the previous catalog used for the merge. Every lock is checked against
the same original package URL, repository, plugin identity, source, version,
fileSize and pre-existing fileHash; a mismatched or stale lock is rejected.

For a new candidate that fails the normal trust gate, the selection algorithm
may substitute a **full commit-pinned URL to the byte-identical previous
binary**, provided that binary is independently downloaded and verified again
in the current run. No new/different upstream package is thereby approved.
The corresponding provenance packageUrl is changed to the same commit URL,
while authors, original source attribution, internal plugin identity and
version remain unchanged. The reconciler records the **URL change** rather
than pretending that this selected plugin is unchanged.

The daily read-only `full-integrity-review.yml` job now creates the immutable
index against its own pinned `builds` checkout, consumes it during per-plugin
selection, and generates both strict quarantine and compatibility previews.
The outputs still say `releaseAuthorized: false`. **No update to published
plugin URLs, manifests, source lists or CloudStream installers is made by this
workflow.** A rollback is not deployed until a separate release gate confirms
runtime install behavior and grants publication permissions.
