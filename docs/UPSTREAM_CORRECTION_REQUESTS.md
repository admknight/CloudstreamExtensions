# Upstream maintainer correction requests — prepared, not submitted

**Prepared:** October 9, 2026  
**Owner:** MegaRepo downstream integrity review  
**Status:** **Not submitted to upstream repositories**

The connected GitHub integration returned `403 Resource not accessible by integration` for **each** attempt to create issues in ShakzzCutie, CloudX-V2, and Desi. No upstream maintainer has been contacted through those attempts. This is a repository permission boundary, not evidence that an upstream developer has rejected the request.

These are ready-to-copy issue requests. A repository contributor with permission to create issues can open the linked upstream Issues form, paste the corresponding title and body, and record the resulting public issue URL against the related MegaRepo issue. **Do not submit duplicates** if the maintainer has already resolved the problem or another report exists.

The [14-plugin integrity remediation register](INTEGRITY_REMEDIATION_REGISTER.md) is the supporting evidence record. **No checksum, manifest, binary or production catalog change is authorized by this document.**

## 1. ShakzzCutie — GDIndex and Extractors

**Upstream new issue:** https://github.com/Shakzz890/ShakzzCutie/issues/new  
**Submission status:** Prepared, not submitted  
**MegaRepo incidents:** [GDIndex #18](https://github.com/admknight/CloudstreamExtensions/issues/18) and [Extractors #30](https://github.com/admknight/CloudstreamExtensions/issues/30)

**Suggested title:**

> builds/plugins.json: GDIndex and Extractors fileSize differs from uploaded .cs3 files

**Copy-ready body:**

Hi! I maintain a downstream CloudStream extension catalog and noticed two metadata mismatches in the `builds` branch. I compared the manifest and binary records at **the same immutable Git commit**, so this is not based solely on a temporary download result.

Source snapshot: https://github.com/Shakzz890/ShakzzCutie/tree/7c6272bc25262d78b7046f8754ee77db72fb0ae1

| Plugin | `plugins.json` fileSize | Actual file size at same commit | Version |
|---|---:|---:|---:|
| GDIndex | 15,937 bytes | **17,206 bytes** | 4 |
| Extractors | 39,206 bytes | **39,894 bytes** | 67 |

Neither record declares a `fileHash`. The package files were changed in [commit 36cce70](https://github.com/Shakzz890/ShakzzCutie/commit/36cce705ca18d2037179b47c226f51beeff9c9a6), while the manifest was edited subsequently.

Could you please **confirm the intended releases and align the `fileSize` metadata with the actual intended binaries**, or advise which binary versions should be used? Publishing the intended binary's SHA-256 checksum in each manifest entry would also help downstream repositories verify updates. These are metadata/release-verification requests, not allegations about binary safety.

Downstream evidence: [GDIndex #18](https://github.com/admknight/CloudstreamExtensions/issues/18), [Extractors #30](https://github.com/admknight/CloudstreamExtensions/issues/30).

Thank you for maintaining the repository!

---

## 2. CloudX-V2 — Sarangfilm

**Upstream new issue:** https://github.com/Asm0d3usX/CloudX-V2/issues/new  
**Submission status:** Prepared, not submitted  
**MegaRepo incident:** [Sarangfilm #26](https://github.com/admknight/CloudstreamExtensions/issues/26)

**Suggested title:**

> Sarangfilm.cs3 file size differs from builds/plugins.json at same revision

**Copy-ready body:**

Hi! While validating CloudX-V2's `builds` catalog for a downstream repository, I found a reproducible `fileSize` discrepancy in one immutable revision.

Source snapshot: https://github.com/Asm0d3usX/CloudX-V2/tree/c73809693bc8406a6f8cffd98039e8278e92a95a

- `plugins.json` declares **Sarangfilm v1** and `fileSize: 29763`.
- The checked-in `Sarangfilm.cs3` file has a Git-recorded size of **28,666 bytes** (blob `a3afe60238fcd7a6717d854b7c767e5d4e3a3460`).
- The package URL in the manifest still references `Asm0d3usX/CloudX` rather than `CloudX-V2`. Please confirm whether that older redirect is intentional.

Could you confirm which binary and URL represent the intended release, correct the declared size if appropriate, and ideally provide a checksum for the intended package? A stable release or commit-pinned reference would allow downstream verification. I am not requesting any change to the provider's runtime behavior.

Downstream evidence: [MegaRepo issue #26](https://github.com/admknight/CloudstreamExtensions/issues/26).

Thanks for maintaining this project!

---

## 3. Desi — StreamHubOne unchanged version, changed binary

**Upstream new issue:** https://github.com/Faisal0786/Desi/issues/new  
**Submission status:** Prepared, not submitted  
**MegaRepo incident:** [StreamHubOne #29](https://github.com/admknight/CloudstreamExtensions/issues/29)

**Suggested title:**

> StreamHubOne package bytes change between builds while version remains 62

**Copy-ready body:**

Hi! I noticed that StreamHubOne is rebuilt on the `builds` branch with different binary bytes but the **same declared plugin version 62**. This makes it difficult for downstream catalogs to distinguish an intentional new release from an unexpected replacement at a mutable download URL.

Two commit-pinned snapshots from October 9, 2026:

| Builds commit | Version | Manifest fileSize | Manifest SHA-256 |
|---|---:|---:|---|
| [34e3969](https://github.com/Faisal0786/Desi/tree/34e3969040b482dd0f9ed5f58e780175fc7f6d84) | 62 | 1,059,746 | `49953ff3524e56e8f9fc51acac4efd8e3de1f08c64014f36b4f3d75f84d9c364` |
| [bbcc450](https://github.com/Faisal0786/Desi/tree/bbcc450cc9ed591c9515bb988d82cf24040963d0) | 62 | 1,046,291 | `91bc0884893da138cf73b69fb3a708a882051b999d1e0e62c082591465d3cbd9` |

At each individual commit, the declared size is consistent with its checked-in binary's length. **This report does not claim either package is invalid.** It flags the release identity changing without a version increment.

Could you confirm whether these same-version updates are intentional? If possible, please increment the version when the binary changes, or expose a stable immutable release artifact/commit-based package URL, so downstream consumers can pin or review intentional changes.

Downstream evidence: [MegaRepo issue #29](https://github.com/admknight/CloudstreamExtensions/issues/29).

Thank you for your work!

---

## Submission and verification checklist

1. Check each upstream repository for an existing matching issue or a newer correction before posting.
2. Submit the corresponding request using an account with Issues write access and record its actual URL on the linked MegaRepo issue. Never record a placeholder URL as submitted.
3. Await maintainers' replies or a verified source commit. **No expected delivery date or reply is assumed.**
4. On a relevant upstream change, compare one pinned `plugins.json` with its binary in the **same** revision, validate declared length and SHA-256 against independently downloaded bytes, and document the release provenance.
5. Only after trust checks pass, test a guarded MegaRepo candidate and a full post-publication audit. Leave [Phase 2 PR #20](https://github.com/admknight/CloudstreamExtensions/pull/20) in draft until its existing 14 blockers are addressed.

**Additional unresolved sources:** CNCVerse's six changed versions still require release trust review, GitLab's three size mismatches require a pinned revision comparison, and the manually curated OnlineMoviesHinditProvider entry lacks size/hash metadata. See the full [remediation register](INTEGRITY_REMEDIATION_REGISTER.md).
