# Locally verified, immutable CloudStream recovery

This is a scoped downstream correction, not a claim that the upstream maintainers have repaired their manifests or issued signed releases.

## Mechanism

- `local_verified_plugins.json` contains twenty-four selected plugins from their **original upstream GitHub/GitLab repositories**, each pinned to an exact 40-character Git commit and including the SHA-256 and byte length of that binary.
- `sources.json` gives these reviewed entries selection priority over same-version upstream records. `tools/merge_upstreams.py` reads the checked-out local source so pull-request reviews and production builds use exactly the approved file revision.
- `trusted_binary_approvals.json` contains **exact-match, scoped review records** for these twenty-four entries, with source, version, URL, digest, size, reviewer identification and immutable evidence link. The existing `verify_candidate_integrity.py` gate independently re-downloads and validates every selected package before publication.
- If a source updates to an unapproved binary or a pinned package disappears, the existing fail-closed, no-removal safety rules still apply. No arbitrary URL or unreviewed checksum is accepted.

## Evidence

**GitHub immutable files:** ShakzzCutie build revision `7c6272bc25262d78b7046f8754ee77db72fb0ae1` (GDIndex, Extractors, OnlineMoviesHinditProvider); CloudX-V2 build revision `c73809693bc8406a6f8cffd98039e8278e92a95a` (Sarangfilm); Nonton Indo build revision `ff22707cca6b1292d327a9aa9dcc60de5ee12492` (Donghub). The downloaded binary contents were independently SHA-256 hashed and matched the pinned entries.

**NetMovie original-release evidence:** Desi builds revision `0af83282ff9d36e0cc7447582b7152cc948fd1cc`, plugin `NetMovie` v1, 32,088 bytes, SHA-256 `27320e91111d0a371614373dcd747baee5c4d3c5c4a6202de93c45cf9e44d576`, matching the original commit's manifest and the guarded candidate audit. The first release is eligible for publication only through the existing exact-reviewed approval and guarded verification steps.

**GitLab immutable files:** Cloudstream Vietnamese revision `05b8e0b8c7b3aa43665fc57c477862cc0888f917` (StremioProvider, ViStreamProvider, XtreamIPTVProvider, IPTVProvider), checked by the read-only [GitLab evidence workflow](https://github.com/admknight/CloudstreamExtensions/actions/runs/37988142853). Pinned bytes, revision and hashes were independently verified in CI.

**StreamHubOne v63:** Desi immutable GitHub build revision `0af83282ff9d36e0cc7447582b7152cc948fd1cc`, 1,099,831 bytes, SHA-256 `d099a75fe33a918eea0500b08bdbeff34566b10664b9c84cb7c2e53e472b9a57`. The original committed upstream manifest and actual ZIP binary agree. This replaces only the older immutable v62 entry after guarded approval. Evidence: [read-only original-source probe](https://github.com/admknight/CloudstreamExtensions/actions/runs/37994777840).

**IPTVProvider v9:** Cloudstream Vietnamese GitLab revision `05b8e0b8c7b3aa43665fc57c477862cc0888f917`, 32,311 bytes, SHA-256 `8760295a88dd29011add8fa04e542209412100631dee948c18559e9e1acc565a`. The upstream index declares 30,703 bytes, so local recovery overrides only that incorrect size, adds the measured hash, and uses the original commit-pinned package URL while preserving the original upstream metadata. The prior single-plugin exclusion in `sources.json` is removed. Evidence: [read-only original-source probe](https://github.com/admknight/CloudstreamExtensions/actions/runs/37994777840).

**Raghav newer releases and first-time plugins:** Raghav original immutable build revision `bbd6dcf1318d7c76bbf8c853ec120b857dd7df22`, with exact upstream manifest metadata and independently recomputed ZIP SHA-256 in [read-only evidence run](https://github.com/admknight/CloudstreamExtensions/actions/runs/37995159232): `AnimeTH` v1 (51,648 bytes, SHA-256 `588900960340944467b3dcd9895425aca0db65df75f65b680be446293c0a7b2d`), `Anv` v1 (59,454 bytes, `94a1716c3677bc0c2877d89634e7c5da66c39bc306b3dd34758f0e5a06f4c443`), `JustPlay` v12 (421,355 bytes, `31d865e11f19b984e39baa48fa6234b9dd24f70c4a8ff9a1ca178a0dc56966e5`), `TorrentsV1` v22 (123,332 bytes, `083368334ae440237fc0d799ea3dd087c782f46b177915b477267f7f326a05a1`). These are exact per-plugin pins and records, not blanket approval of future upstream changes.

**Legacy checksum upgrade cohort (nine byte-identical original files):** The read-only [GitHub Actions evidence run 37997574506](https://github.com/admknight/CloudstreamExtensions/actions/runs/37997574506) independently checked all nine selected packages against both immutable original `.cs3` files and the existing published mutable URLs. It confirmed byte-for-byte equality, valid ZIPs, and identical original manifest release versions, statuses and file sizes before calculating SHA-256 hashes. The sources were Gian-Fr ItalianProvider (2), Cinephile (2), AniyomiCompatExtension (1), self-similarity MegaRepo (1) and Kim20598 CloudStream extensions (3). `local_verified_plugins.json` and `trusted_binary_approvals.json` record the exact checked Git revisions, hashes and scoped downstream reviews. One provider (`UltimaBeta`) remains in its original disabled status. This is a replacement of mutable URLs and size-only metadata with verifiable immutable original bytes, **not** a new binary version or a playback certification.

## Release validation

The [guarded candidate workflow](https://github.com/admknight/CloudstreamExtensions/actions/runs/37988648669) retained **543/543 existing identities** and reported **zero deferred unsafe updates** with **one independently verified old-byte fallback**. This historical eight-plugin verification was a test-only build; the production branch later published those eight fixes. The ninth NetMovie pin was subsequently published and independently audited in [NetMovie production verification](https://github.com/admknight/CloudstreamExtensions/actions/runs/37991262319). The six StreamHubOne, IPTVProvider, AnimeTH, Anv, JustPlay and TorrentsV1 corrections were published and independently audited in [production verification run 37995531300](https://github.com/admknight/CloudstreamExtensions/actions/runs/37995531300). The nine legacy upgrades below must pass their own guarded publication and independent audit before being labeled deployed.

## Scope of the assurance

A commit-addressed GitLab/GitHub package URL and matching SHA-256 prove the selected bytes are stable and match recorded evidence. They do **not** prove publisher signature, provider functionality inside CloudStream, or the legality/availability of third-party streaming sources. No CloudStream installation/playback testing was performed.

## Maintenance

Only add or replace a pinned entry after an owner-authorized source review: obtain the original repository's exact Git commit; verify the actual binary size and SHA-256 at that commit; retain the evidence URL; update the exact approval record; and require the existing CI and guarded release checks to pass. Do not infer trust from HEAD checks or a mutable branch manifest.
