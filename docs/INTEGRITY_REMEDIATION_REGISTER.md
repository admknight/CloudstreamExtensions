# MegaRepo — 14-plugin integrity remediation register

**Evidence snapshot:** 2026-10-09 14:57 UTC; review-branch [full-catalog dry run](https://github.com/admknight/CloudstreamExtensions/actions/runs/37948028591) ([ZIP evidence](https://github.com/admknight/CloudstreamExtensions/actions/runs/37948028591/artifacts/11624038934)). Compared with published **builds** catalog generated 2026-10-09 10:37:11 UTC. All data below are historical observations, not live install/playback checks.

**Result:** 543 candidate packages checked; **14 blocked**: 6 downloaded size mismatches, 7 changed/untrusted binary metadata cases, and 1 missing declared package size. No checksum or size values below constitute automatic permission to change a binary. `hash_verified` in the gate means matching *declared* hash, not independently authenticated publisher intent.

## Complete exception register

| # | Plugin | Source | Type | Version published → candidate | Size: published → candidate declared → downloaded (bytes) | Required action |
|---:|---|---|---|---|---|---|
| 1 | **CricifyProvider** | [cnc](https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension) | TRUST REVIEW | 70 → 71 | 116,844 → 117,026 → **117,026** | Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest |
| 2 | **Extractors** | [shakzz-recovery](https://github.com/Shakzz890/ShakzzCutie) | SIZE MISMATCH | 67 → 67 | 39,206 → 39,206 → **39,894** | Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck |
| 3 | **GDIndex** | [shakzz-recovery](https://github.com/Shakzz890/ShakzzCutie) | SIZE MISMATCH | 4 → 4 | 15,937 → 15,937 → **17,206** | Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck |
| 4 | **LivXowProvider** | [cnc](https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension) | TRUST REVIEW | 19 → 20 | 127,046 → 127,205 → **127,205** | Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest |
| 5 | **OnlineMoviesHinditProvider** | [manual-recovery](https://github.com/admknight/CloudstreamExtensions) | MISSING SIZE | 6 → 6 | **not declared** → **not declared** → **12,001** | Review curated recovery entry and upstream binary, then supply declared size + trusted digest through source/approved process |
| 6 | **PlayFyProvider** | [cnc](https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension) | TRUST REVIEW | 13 → 14 | 106,767 → 106,982 → **106,982** | Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest |
| 7 | **PlayZTVProvider** | [cnc](https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension) | TRUST REVIEW | 41 → 42 | 117,089 → 117,275 → **117,275** | Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest |
| 8 | **Sarangfilm** | [cloudx](https://github.com/Asm0d3usX/CloudX-V2) | SIZE MISMATCH | 1 → 1 | 29,763 → 29,763 → **28,666** | Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck |
| 9 | **SKTechProvider** | [cnc](https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension) | TRUST REVIEW | 57 → 58 | 125,041 → 125,231 → **125,231** | Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest |
| 10 | **SportzxProvider** | [cnc](https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension) | TRUST REVIEW | 25 → 26 | 122,953 → 123,166 → **123,166** | Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest |
| 11 | **StreamHubOne** | [desi](https://github.com/Faisal0786/Desi) | TRUST REVIEW | 62 → 62 | 1,059,729 → 1,059,746 → **1,059,746** | Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest |
| 12 | **StremioProvider** | [tearrs-vietnamese](https://gitlab.com/tearrs/cloudstream-vietnamese) | SIZE MISMATCH | 7 → 7 | 109,635 → 109,635 → **131,077** | Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck |
| 13 | **ViStreamProvider** | [tearrs-vietnamese](https://gitlab.com/tearrs/cloudstream-vietnamese) | SIZE MISMATCH | 35 → 35 | 313,377 → 313,377 → **312,346** | Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck |
| 14 | **XtreamIPTVProvider** | [tearrs-vietnamese](https://gitlab.com/tearrs/cloudstream-vietnamese) | SIZE MISMATCH | 1 → 1 | 28,540 → 28,540 → **29,257** | Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck |

## Fingerprints and original source links

These are measured **downloaded package** SHA-256 fingerprints from the captured dry-run artifact. They are *not* proof that the package is authorized or safe. Previously published hashes are shown where available; an absent value means no previous `fileHash` was declared.

### 1. CricifyProvider
- **Source index:** [CNCVerse (GitHub)](https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/plugins.json) (source ID: `cnc`)
- **Package URL:** https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/CricifyProvider.cs3
- **Published / candidate version:** 70 / 71
- **Published / candidate declared / downloaded length:** 116,844 / 117,026 / 117,026 bytes
- **Previously published SHA-256:** `sha256-38ab7771b21deec043c7a495875c977571b803aefbdd57e3c618306bb27033b9`
- **Downloaded SHA-256:** `sha256-c36b78630420569a3fdeb912963c9de47f5df1fe2b99a5a2bd7db51e8822d6ec`
- **Exception:** TRUST REVIEW — Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest

### 2. Extractors
- **Source index:** [Shakzz Recovery (GitHub)](https://raw.githubusercontent.com/Shakzz890/ShakzzCutie/builds/plugins.json) (source ID: `shakzz-recovery`)
- **Package URL:** https://raw.githubusercontent.com/Shakzz890/ShakzzCutie/builds/Extractors.cs3
- **Published / candidate version:** 67 / 67
- **Published / candidate declared / downloaded length:** 39,206 / 39,206 / 39,894 bytes
- **Previously published SHA-256:** `not declared`
- **Downloaded SHA-256:** `sha256-b6c73e188efa1d49796b7beec71e1ab3cadda8f308c987dbe23e9e183efb4abd`
- **Exception:** SIZE MISMATCH — Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck

### 3. GDIndex
- **Source index:** [Shakzz Recovery (GitHub)](https://raw.githubusercontent.com/Shakzz890/ShakzzCutie/builds/plugins.json) (source ID: `shakzz-recovery`)
- **Package URL:** https://raw.githubusercontent.com/Shakzz890/ShakzzCutie/builds/GDIndex.cs3
- **Published / candidate version:** 4 / 4
- **Published / candidate declared / downloaded length:** 15,937 / 15,937 / 17,206 bytes
- **Previously published SHA-256:** `not declared`
- **Downloaded SHA-256:** `sha256-06347df7ead05921e1e32da0c0103e800bea14b0ca186a611e0ae20ec018c679`
- **Exception:** SIZE MISMATCH — Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck

### 4. LivXowProvider
- **Source index:** [CNCVerse (GitHub)](https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/plugins.json) (source ID: `cnc`)
- **Package URL:** https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/LivXowProvider.cs3
- **Published / candidate version:** 19 / 20
- **Published / candidate declared / downloaded length:** 127,046 / 127,205 / 127,205 bytes
- **Previously published SHA-256:** `sha256-5e879136115dcdb5c4ed6d335c67e6c7dbb2df75af6dc299e02d3551cd587405`
- **Downloaded SHA-256:** `sha256-1c078966138490b7da7f48f7eb36d4c20b538c311016a5b7341ef1fbf91d0086`
- **Exception:** TRUST REVIEW — Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest

### 5. OnlineMoviesHinditProvider
- **Source index:** [MegaRepo curated recovery](https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/recovery_plugins.json) (source ID: `manual-recovery`)
- **Package URL:** https://raw.githubusercontent.com/Shakzz890/ShakzzCutie/builds/OnlineMoviesHinditProvider.cs3
- **Published / candidate version:** 6 / 6
- **Published / candidate declared / downloaded length:** **not declared** / **not declared** / 12,001 bytes
- **Previously published SHA-256:** `not declared`
- **Downloaded SHA-256:** `sha256-5f8a774e0cd991f885642ce72459d856c6ca122d6298fe337a2fea816b08f93e`
- **Exception:** MISSING SIZE — Review curated recovery entry and upstream binary, then supply declared size + trusted digest through source/approved process

### 6. PlayFyProvider
- **Source index:** [CNCVerse (GitHub)](https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/plugins.json) (source ID: `cnc`)
- **Package URL:** https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/PlayFyProvider.cs3
- **Published / candidate version:** 13 / 14
- **Published / candidate declared / downloaded length:** 106,767 / 106,982 / 106,982 bytes
- **Previously published SHA-256:** `sha256-e9d1baf9121201b6c673fc6ecb2aec340d571994e4e8c930e341c69a166c45a3`
- **Downloaded SHA-256:** `sha256-365edb434209a559a3a7dfd7b6744b28831c137f2ef098fa418b08bfc1390250`
- **Exception:** TRUST REVIEW — Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest

### 7. PlayZTVProvider
- **Source index:** [CNCVerse (GitHub)](https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/plugins.json) (source ID: `cnc`)
- **Package URL:** https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/PlayZTVProvider.cs3
- **Published / candidate version:** 41 / 42
- **Published / candidate declared / downloaded length:** 117,089 / 117,275 / 117,275 bytes
- **Previously published SHA-256:** `sha256-633ca983c6b9dea4fd8a6f314058875f5c15f65180d32826acae4b9e6d738e3f`
- **Downloaded SHA-256:** `sha256-e6411013cca732e3f597f60493798b2c3841ed7d5d8ecdffa84a3248fae75b4c`
- **Exception:** TRUST REVIEW — Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest

### 8. Sarangfilm
- **Source index:** [CloudX V2 (GitHub)](https://raw.githubusercontent.com/Asm0d3usX/CloudX-V2/builds/plugins.json) (source ID: `cloudx`)
- **Package URL:** https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Sarangfilm.cs3
- **Published / candidate version:** 1 / 1
- **Published / candidate declared / downloaded length:** 29,763 / 29,763 / 28,666 bytes
- **Previously published SHA-256:** `not declared`
- **Downloaded SHA-256:** `sha256-9be9cb884a6c83fc441be4406cd1fe3a1b026813f818b5b19ceab7662fa210e7`
- **Exception:** SIZE MISMATCH — Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck

### 9. SKTechProvider
- **Source index:** [CNCVerse (GitHub)](https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/plugins.json) (source ID: `cnc`)
- **Package URL:** https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/SKTechProvider.cs3
- **Published / candidate version:** 57 / 58
- **Published / candidate declared / downloaded length:** 125,041 / 125,231 / 125,231 bytes
- **Previously published SHA-256:** `sha256-b58b9b7befc3bd3e1ed0053404db79c81228b4fda059293d40cd3fa179a0778b`
- **Downloaded SHA-256:** `sha256-f7605368aff29d108c0975d23bddf4f98b3d6dae0662c5db3f5be0c76ab9150d`
- **Exception:** TRUST REVIEW — Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest

### 10. SportzxProvider
- **Source index:** [CNCVerse (GitHub)](https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/plugins.json) (source ID: `cnc`)
- **Package URL:** https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/SportzxProvider.cs3
- **Published / candidate version:** 25 / 26
- **Published / candidate declared / downloaded length:** 122,953 / 123,166 / 123,166 bytes
- **Previously published SHA-256:** `sha256-fd8f3dc9d41912202a23a68106eca29e6ce68b4bbe99cbf60f17de77dcf4b7c2`
- **Downloaded SHA-256:** `sha256-62d24fa711dd5a9c0dec3e60b67ae5eaf2e3f7d71e6b5687039b65e70367b451`
- **Exception:** TRUST REVIEW — Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest

### 11. StreamHubOne
- **Source index:** [Desi (GitHub)](https://raw.githubusercontent.com/Faisal0786/Desi/builds/plugins.json) (source ID: `desi`)
- **Package URL:** https://raw.githubusercontent.com/Faisal0786/Desi/builds/StreamHubOne.cs3
- **Published / candidate version:** 62 / 62
- **Published / candidate declared / downloaded length:** 1,059,729 / 1,059,746 / 1,059,746 bytes
- **Previously published SHA-256:** `sha256-b5012093375d284330714ee8794d208ce4ff0b6f4d2d5e1ab983cf3696397fd6`
- **Downloaded SHA-256:** `sha256-49953ff3524e56e8f9fc51acac4efd8e3de1f08c64014f36b4f3d75f84d9c364`
- **Exception:** TRUST REVIEW — Verify intended release against trusted upstream evidence and prior hash; require reviewed approval if new digest

### 12. StremioProvider
- **Source index:** [Tearrs (GitLab)](https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/plugins.json) (source ID: `tearrs-vietnamese`)
- **Package URL:** https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/StremioProvider.cs3
- **Published / candidate version:** 7 / 7
- **Published / candidate declared / downloaded length:** 109,635 / 109,635 / 131,077 bytes
- **Previously published SHA-256:** `not declared`
- **Downloaded SHA-256:** `sha256-5d7c4d6f65b974f82d2edf454b3343d309b1dc04b627cb9e519beff66799d7a7`
- **Exception:** SIZE MISMATCH — Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck

### 13. ViStreamProvider
- **Source index:** [Tearrs (GitLab)](https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/plugins.json) (source ID: `tearrs-vietnamese`)
- **Package URL:** https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/ViStreamProvider.cs3
- **Published / candidate version:** 35 / 35
- **Published / candidate declared / downloaded length:** 313,377 / 313,377 / 312,346 bytes
- **Previously published SHA-256:** `not declared`
- **Downloaded SHA-256:** `sha256-2f83a4ffacc087efe993e2719d9e6e2f187bc6289b1ffd3e3e35f6928ed8337a`
- **Exception:** SIZE MISMATCH — Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck

### 14. XtreamIPTVProvider
- **Source index:** [Tearrs (GitLab)](https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/plugins.json) (source ID: `tearrs-vietnamese`)
- **Package URL:** https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/XtreamIPTVProvider.cs3
- **Published / candidate version:** 1 / 1
- **Published / candidate declared / downloaded length:** 28,540 / 28,540 / 29,257 bytes
- **Previously published SHA-256:** `not declared`
- **Downloaded SHA-256:** `sha256-3d30e8f45432043394c399339556fe206c7a563d6cc2caf07160a606dec24782`
- **Exception:** SIZE MISMATCH — Confirm exact intended upstream package, then correct source manifest size and publish an independently reviewed digest; recheck

## Priority and release rules

**P0: Six mismatches.** Contact/confirm upstream intended release, compare binary at a stable revision against upstream build artifacts, correct source data only with evidence. These package URLs are mutable; preserving the previous `plugins.json` alone does not pin the earlier bytes.

**P1: One unchanged-version binary change.** **StreamHubOne** remains v62 but published hash and size differ from the current package. This requires special upstream confirmation; version equality is not proof of binary identity.

**P1: Six other changed CNC entries.** Their candidate versions increased and newly declared hashes matched the downloaded files, but those new hashes have not been explicitly approved as trusted. Treat as pending provenance review, not corrupted by default.

**P2: Missing size.** The curated `OnlineMoviesHinditProvider` entry in [recovery_plugins.json](https://github.com/admknight/CloudstreamExtensions/blob/master/recovery_plugins.json) has no `fileSize` or authenticated `fileHash`. Obtain release evidence before any curated metadata update.

**Acceptance criteria:** provenance established, published and downloaded size match, trusted SHA-256 verification where available, no unresolved unexpected binary substitutions, full post-publication audit passes. Do not silently change sizes, downgrade or delete entries, dismiss audit failures, or merge the strict publication gate while existing unreviewed blockers remain.

## Group counts by repository

| Source | Affected | Count |
|---|---|---:|
| [CNCVerse (GitHub)](https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension) | CricifyProvider, LivXowProvider, PlayFyProvider, PlayZTVProvider, SKTechProvider, SportzxProvider | 6 |
| [Shakzz Recovery (GitHub)](https://github.com/Shakzz890/ShakzzCutie) | Extractors, GDIndex | 2 |
| [MegaRepo curated recovery](https://github.com/admknight/CloudstreamExtensions) | OnlineMoviesHinditProvider | 1 |
| [CloudX V2 (GitHub)](https://github.com/Asm0d3usX/CloudX-V2) | Sarangfilm | 1 |
| [Desi (GitHub)](https://github.com/Faisal0786/Desi) | StreamHubOne | 1 |
| [Tearrs (GitLab)](https://gitlab.com/tearrs/cloudstream-vietnamese) | StremioProvider, ViStreamProvider, XtreamIPTVProvider | 3 |

## Upstream revision investigation — 2026-10-09

This investigation strengthens the original read-only dry-run with **GitHub repository tree objects**, **immutable commit-specific manifest snapshots**, and recorded provider history. Git blob IDs are repository revision evidence; they are not substitutes for independently trusted SHA-256 signatures. These observations cannot prove runtime playback.

### Source-level findings

| Source and pinned reference | Independent finding | Interpretation / next action |
|---|---|---|
| [Shakzz builds commit 7c6272b](https://github.com/Shakzz890/ShakzzCutie/tree/7c6272bc25262d78b7046f8754ee77db72fb0ae1) | The checked-in binary sizes are **GDIndex 17,206** and **Extractors 39,894** bytes, but the [manifest at that same revision](https://raw.githubusercontent.com/Shakzz890/ShakzzCutie/7c6272bc25262d78b7046f8754ee77db72fb0ae1/plugins.json) declares **15,937** and **39,206** respectively. The package files were last changed by [commit 36cce70](https://github.com/Shakzz890/ShakzzCutie/commit/36cce705ca18d2037179b47c226f51beeff9c9a6) in February 2026, while the index was subsequently edited in September 2026. | **Reproducible upstream manifest/file mismatch** in one immutable revision. Seek upstream correction with a reviewed binary checksum; preserve both incidents until proven. |
| [CloudX-V2 builds commit c738096](https://github.com/Asm0d3usX/CloudX-V2/tree/c73809693bc8406a6f8cffd98039e8278e92a95a) | Checked-in Sarangfilm binary size **28,666** versus declared **29,763** in the same pinned [manifest](https://raw.githubusercontent.com/Asm0d3usX/CloudX-V2/c73809693bc8406a6f8cffd98039e8278e92a95a/plugins.json). The manifest references an older `Asm0d3usX/CloudX` repository path; GitHub now redirects that repository's API request. | **Upstream inconsistency** plus an older repository slug; request source correction and source-owned checksum. Do not silently rewrite the URL without verifying it resolves to the intended binary. |
| [Desi build 34e3969](https://github.com/Faisal0786/Desi/tree/34e3969040b482dd0f9ed5f58e780175fc7f6d84) versus [bbcc450](https://github.com/Faisal0786/Desi/tree/bbcc450cc9ed591c9515bb988d82cf24040963d0) | **StreamHubOne version remains 62**, but the package changes from **1,059,746** bytes, SHA-256 `49953ff3524e56e8f9fc51acac4efd8e3de1f08c64014f36b4f3d75f84d9c364`, to **1,046,291** bytes, SHA-256 `91bc0884893da138cf73b69fb3a708a882051b999d1e0e62c082591465d3cbd9`. Both commit-pinned manifests declare the same version and their respective sizes/hashes. | **Mutable same-version release**, not merely stale length. Recommend upstream increment version and publish stable, traceable package artifacts before approving any substitution. |
| [CNCVerse builds commit 0f413f2](https://github.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/tree/0f413f212902bfae8aa8506a5162b5290a7fb519) | Six identified provider entries have increased candidate versions, and their declared sizes agree with the files recorded in the same GitHub tree. The earlier full-catalog dry-run independently matched their newly declared hashes to the downloaded files. | **Source-consistent update, but release authority unverified**. A new upstream-declared hash matching downloaded bytes is not automatically a prior trusted digest. Review intended releases or add explicit evidence-backed approvals as required by [draft Phase 2](https://github.com/admknight/CloudstreamExtensions/pull/20). |
| [Tearrs GitLab manifest](https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/plugins.json) | The October 9 full-catalog audit observed size disagreements for Stremio, ViStream and XtreamIPTV. Live independent GitLab file-history/size metadata could **not** be obtained through the available external API during this investigation. | **Evidence remains from the actual download audit**, not a separately authenticated GitLab revision. Obtain a pinned GitLab commit and matching binary metadata before any corrective change. |
| [MegaRepo recovery index](https://github.com/admknight/CloudstreamExtensions/blob/master/recovery_plugins.json) | OnlineMoviesHinditProvider has neither `fileSize` nor `fileHash`; the [pinned Shakzz tree](https://github.com/Shakzz890/ShakzzCutie/tree/7c6272bc25262d78b7046f8754ee77db72fb0ae1) records a **12,001-byte** binary. | **Missing metadata**, not proof of a safe release. Review the exact intended source binary before updating the curated recovery manifest. |

### One issue for each remediation entry

| Failure class | Managed issue links | Count |
|---|---|---:|
| Upstream file-size mismatch | [Extractors #30](https://github.com/admknight/CloudstreamExtensions/issues/30), [GDIndex #18](https://github.com/admknight/CloudstreamExtensions/issues/18), [Sarangfilm #26](https://github.com/admknight/CloudstreamExtensions/issues/26), [Stremio #32](https://github.com/admknight/CloudstreamExtensions/issues/32), [ViStream #33](https://github.com/admknight/CloudstreamExtensions/issues/33), [XtreamIPTV #34](https://github.com/admknight/CloudstreamExtensions/issues/34) | 6 |
| Changed release metadata awaiting trust review | [Cricify #22](https://github.com/admknight/CloudstreamExtensions/issues/22), [LivXow #23](https://github.com/admknight/CloudstreamExtensions/issues/23), [PlayFy #24](https://github.com/admknight/CloudstreamExtensions/issues/24), [PlayZTV #25](https://github.com/admknight/CloudstreamExtensions/issues/25), [SKTech #27](https://github.com/admknight/CloudstreamExtensions/issues/27), [Sportzx #28](https://github.com/admknight/CloudstreamExtensions/issues/28), [StreamHubOne #29](https://github.com/admknight/CloudstreamExtensions/issues/29) | 7 |
| Missing metadata | [OnlineMoviesHinditProvider #31](https://github.com/admknight/CloudstreamExtensions/issues/31) | 1 |
| **Total** | **All blockers have a dedicated tracked issue** | **14** |

### Release decision and limitations

The repository-owning upstream is authoritative about the intended release **only after provenance is reviewed**. A GitHub file size, a stable commit, a downloaded hash, and a matching upstream-declared hash can establish source consistency but not inherently authenticity, safety, or working playback. The immutable GitHub references above establish **what the repository contained at a specific commit**, not an endorsement of those files.

Do **not** edit the published `builds` manifest, substitute binary URLs, suppress integrity alarms, or move [draft PR #20](https://github.com/admknight/CloudstreamExtensions/pull/20) out of draft on this evidence alone. Ask each source maintainer to correct inconsistent metadata or provide independently reviewable release evidence. For GitLab, first pin the current commit and compare its exact manifest to the actual package bytes.

**Scope:** Static evidence register, not a full security, installation, or runtime playback audit. Any newer upstream release must be rechecked against a fresh pinned catalog and actual file download before resolving an issue.
