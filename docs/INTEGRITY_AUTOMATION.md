# Automated full-catalog integrity reviews

**Status:** GitHub Actions read-only verification. It does not publish plugins.

The workflow [Full Catalog Integrity Review (Read Only)](../.github/workflows/full-integrity-review.yml)
runs daily at **02:11 UTC**, on demand through GitHub Actions, and in relevant pull requests.
The existing three-hour aggregation and hourly incident monitor remain independent.

## What it does

1. Checks out the reviewing commit and the current `builds` branch as two separate
   read-only working trees.
2. Collects a fresh candidate from configured upstream source indexes without
   publishing or changing production files.
3. Downloads the candidates, checking declared length, SHA-256 where present and
   the current trusted-release approval policy.
4. Produces per-plugin dispositions for accepted, deferred, withheld and
   immutable-fallback entries. Every selected record retains the matching
   upstream or previous provenance.
5. Generates two independently reconciled, **non-publishable** report bundles:
   - `review-compatibility`: retains exact older metadata for existing entries
     without an approved replacement, **clearly marked unverified**;
   - `review-quarantine`: leaves those unresolved entries out of this *preview*
     to quantify the potential loss of availability.
6. Checks all counts, source coverage, plugin and provenance identities,
   snapshot digests, release-diff summaries and status output and uploads
   diagnostic artifacts.

## What the results mean

- An accepted hash-verified candidate matches the metadata available to the
  verifier. It does **not** by itself prove third-party release authenticity or
  playback functionality.
- `legacySizeOnlyCandidates` counts unchanged entries where **no checksum** was
  available; they remain a separate risk class.
- `unverifiedPreviousCarried` entries preserve the old **metadata**, not
  previously downloaded package bytes. Most upstream raw `builds` links are
  mutable and must **not** be described as safe frozen fallbacks.
- A CI check passing means **the read-only review was generated consistently**.
  It does not authorize publishing either catalog.
- Any failed run indicates the review itself could not safely complete. Do not
  hide that error or replace the previous production catalog.

## Separation from production

This workflow declares `permissions: contents: read` only and never invokes
the build publication workflow. No `git push`, branch mutation, GitHub issue
change or external maintainer request is performed by this workflow.

The existing published catalog and its update process are not rewritten by
this addition. The stricter automatic self-healing proposal remains a separate
draft until there is an approved release policy for unverified old entries and
a tested post-publication full integrity audit.

A source correction must still pass independent byte verification and
the release-approval rules before any real plugin metadata is changed.
