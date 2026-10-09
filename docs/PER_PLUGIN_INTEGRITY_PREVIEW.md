# Per-plugin integrity selection — review prototype

**Status: development preview, not integrated with the production aggregator.**

This branch experiments with selecting CloudStream plugins independently after
the strict binary verifier downloads and checks candidate packages. It does
**not** alter production branches, package URLs, repo.json, release notes,
dashboard pages or GitHub issues. The strict publication gate in
[draft Phase 2 PR #20](https://github.com/admknight/CloudstreamExtensions/pull/20)
remains unmerged.

## Independent outcomes

| Outcome | Meaning | Proposed catalog |
|---|---|---|
| accepted_candidate | Existing verifier accepted candidate using the *current* trust policy, which includes unchanged size-only legacy entries | Preserve exact candidate plugin and source provenance |
| retained_immutable_previous | Candidate blocked, but previous package has a full immutable GitHub commit URL, declared length and SHA-256, and independently matching downloaded bytes | Retain exact previous plugin and previous provenance in the preview |
| quarantined_existing | Candidate blocked, and there is no verified immutable previous binary | Exclude existing entry from the preview **only**; production stays unchanged, release approval required |
| withheld_new | New candidate is not verified/trusted | Exclude new entry from preview |
| previous_absent_from_candidate | Previously published entry disappeared from candidate | Report removal for explicit review; do not silently approve it |

A mutable GitHub URL ending in /builds/Plugin.cs3 is **not** a
last-known-good fallback, even when the old manifest has a hash. This is the
problem observed with StreamHubOne. An immutable GitHub commit raw URL is
accepted as a fallback only when the previously declared length and SHA-256
also match freshly downloaded package bytes. GitLab refs are not yet
recognized as immutable fallback pins because additional origin validation
is required.

## Preview-only outputs

The development workflow rebuilds current upstream candidates read-only and
runs the per-plugin selector against a single consistent builds snapshot. The
tool creates these four files **in a separate output directory**:

- preview.plugins.json — reviewed *proposal*; not a ready-to-publish catalog
- preview.provenance.json — corresponding, unmodified selected provenance
- integrity-selection.json — decision counts and quarantine/hold reasons
- verification-report.json — all candidate package checks from the strict gate

The optional --fail-on-quarantine mode returns nonzero when any manual review
is required. By default, a successful exit means the preview was generated,
**not** that a catalog publication was approved.

## Mandatory release safeguards

1. Reconcile all input candidates, approved candidates, immutable fallbacks,
   quarantined entries, new entries held and previous entries removed.
2. Preserve original records and provenance for every selected plugin.
   Do not infer release authenticity merely from a matching upstream hash.
3. Do **not** copy the preview into builds. If quarantine/removal is
   explicitly approved, derive repo.json, README, STATUS, release diffs,
   plugin counts and provenance atomically from the same selected set.
4. Require an explicit decision on loss of availability of a quarantined
   existing plugin. Until such a policy is approved, production is untouched.
5. Run full post-publication integrity verification and runtime tests
   separately before declaring a new release healthy.

**Trust limitation:** The existing gate accepts *unchanged* legacy entries
with size-only verification. These are not cryptographically authenticated
releases. The selector preserves that policy for compatibility but reports
legacy count rather than describing those entries as verified by SHA-256.
