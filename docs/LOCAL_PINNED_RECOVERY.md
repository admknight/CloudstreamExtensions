# Locally verified, immutable CloudStream recovery

This is a scoped downstream correction, not a claim that the upstream maintainers have repaired their manifests or issued signed releases.

## Mechanism

- `local_verified_plugins.json` contains only eight selected plugins from their **original upstream GitHub/GitLab repositories**, each pinned to an exact 40-character Git commit and including the SHA-256 and byte length of that binary.
- `sources.json` gives these reviewed entries selection priority over same-version upstream records. `tools/merge_upstreams.py` reads the checked-out local source so pull-request reviews and production builds use exactly the approved file revision.
- `trusted_binary_approvals.json` contains **exact-match, scoped review records** for these eight entries, with source, version, URL, digest, size, reviewer identification and immutable evidence link. The existing `verify_candidate_integrity.py` gate independently re-downloads and validates every selected package before publication.
- If a source updates to an unapproved binary or a pinned package disappears, the existing fail-closed, no-removal safety rules still apply. No arbitrary URL or unreviewed checksum is accepted.

## Evidence

**GitHub immutable files:** ShakzzCutie build revision `7c6272bc25262d78b7046f8754ee77db72fb0ae1` (GDIndex, Extractors, OnlineMoviesHinditProvider); CloudX-V2 build revision `c73809693bc8406a6f8cffd98039e8278e92a95a` (Sarangfilm); Nonton Indo build revision `ff22707cca6b1292d327a9aa9dcc60de5ee12492` (Donghub). The downloaded binary contents were independently SHA-256 hashed and matched the pinned entries.

**GitLab immutable files:** Cloudstream Vietnamese revision `05b8e0b8c7b3aa43665fc57c477862cc0888f917` (StremioProvider, ViStreamProvider, XtreamIPTVProvider), checked by the read-only [GitLab evidence workflow](https://github.com/admknight/CloudstreamExtensions/actions/runs/37988142853). Pinned bytes, revision and hashes were independently verified in CI.

## Release validation

The [guarded candidate workflow](https://github.com/admknight/CloudstreamExtensions/actions/runs/37988648669) retained **543/543 existing identities** and reported **zero deferred unsafe updates** with **one independently verified old-byte fallback**. This was a test-only build; the production branch requires its own post-publication verification.

## Scope of the assurance

A commit-addressed GitLab/GitHub package URL and matching SHA-256 prove the selected bytes are stable and match recorded evidence. They do **not** prove publisher signature, provider functionality inside CloudStream, or the legality/availability of third-party streaming sources. No CloudStream installation/playback testing was performed.

## Maintenance

Only add or replace a pinned entry after an owner-authorized source review: obtain the original repository's exact Git commit; verify the actual binary size and SHA-256 at that commit; retain the evidence URL; update the exact approval record; and require the existing CI and guarded release checks to pass. Do not infer trust from HEAD checks or a mutable branch manifest.
