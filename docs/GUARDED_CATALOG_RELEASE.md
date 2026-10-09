# Guarded MegaRepo Release Candidate

**Status: review-only publication prototype. Not activated in `build.yml`.**

The candidate finalizer makes a complete, internally consistent plugin catalog
and accompanying source provenance, release diff, status page, release notes,
README and installer repository manifest from an independently verified
per-plugin selection. It **never pushes Git commits, changes upstream URLs,
approves new binary fingerprints or removes existing plugin identities**.

## Preserving availability without inventing binary trust

The policy accepts only the results of `verify_candidate_integrity.py`.
An untrusted changed package is not promoted. If a previously published
artifact has been reverified at an immutable commit, the selector can retain
the identical old package using its pinned URL. Otherwise the previously
published metadata is copied **unchanged**, explicitly reported as deferred
and **unverified**; the old mutable URL may serve changed bytes and therefore
is NOT a safe binary backup. No new checksum is claimed.

The candidate is automatically refused if:

- any existing plugin identity would disappear;
- upstream source-index health is degraded or selection evidence does not match;
- the installer-facing `repo.json` changes unexpectedly;
- no exact plugin/provenance match is available;
- the deferred unverified exception count is above 24;
- a suspicious number of plugin metadata changes is proposed; or
- the independent full-catalog verifier and selected provenance fail to reconcile.

A current count of 543 plugins is the observed baseline, not a hardcoded
selection assumption. A later catalog is evaluated against its own pinned
previous `builds` snapshot.

## Exact release files

The staged candidate regenerates exactly the existing eight operational
artifact types: `plugins.json`, `provenance.json`, `repo.json`,
`merge-report.json`, `release-diff.json`, `STATUS.md`,
`RELEASE_NOTES.md`, and `README.md`. It retains the existing README's
CloudStream installation instructions and commercial/public branding. It adds
an explicit binary-integrity section that distinguishes URL reachability,
checksum matches, legacy size-only metadata, and upstream updates deferred for
review. An extra `guarded-release-assurance.json` is emitted **only in
staging**, with input/output digest evidence and `actualPublicationPerformed:
false`.

## Activation gate

The development PR runs regressions and a complete read-only live-catalog
candidate build. **That alone does not publish it.** Before switching
production `build.yml` to the guarded files, verify exact file schemas,
category/source counts, all old identities, immutable package availability
and that source/fingerprint trust remains enforced. Perform a supervised
publish, then confirm installer link responses and run the complete independent
integrity audit. The real audit must not be marked green simply because
deferred issues were excluded from the candidate.

The release must continue to keep open legitimate upstream integrity
incidents; it cannot repair missing upstream checksums or source binaries
that were not preserved in an immutable location.
