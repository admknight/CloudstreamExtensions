# Adam Knight Extensions

[![Update Aggregated Repository](https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml/badge.svg)](https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml)

A one-stop CloudStream repository that aggregates the **published plugin indexes** of maintained upstream repositories.

This project is an **index aggregator**, not a forked/rebranded plugin source tree. Plugin metadata, authors, download URLs, repository URLs, and upstream ownership are preserved.

## Install

Add this URL in CloudStream under **Settings -> Extensions -> Add Repository**:

```text
https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/master/repo.json
```

CloudStream reads the production plugin index from the `builds` branch automatically.

## Live status

The production workflow runs daily and can also be started manually.

- **Current production status:** [STATUS.md](https://github.com/admknight/CloudstreamExtensions/blob/builds/STATUS.md)
- **Build history:** [BUILD_HISTORY.md](https://github.com/admknight/CloudstreamExtensions/blob/builds/BUILD_HISTORY.md)
- **Machine-readable report:** [merge-report.json](https://github.com/admknight/CloudstreamExtensions/blob/builds/merge-report.json)
- **Production plugin index:** [plugins.json](https://github.com/admknight/CloudstreamExtensions/blob/builds/plugins.json)

Every workflow run also writes the status tables into the GitHub Actions job summary. If an upstream index cannot be fetched, or the candidate catalog drops below the safety threshold, publication is blocked and the last known-good production catalog stays unchanged.

## Active upstreams

| Upstream | Role | Attribution |
| --- | --- | --- |
| [Phisher Repo](https://github.com/phisher98/cloudstream-extensions-phisher) | Published plugin index | Original plugin metadata/authors preserved |
| [Cinephile](https://github.com/rockhero1234/cinephile) | Published plugin index | Original plugin metadata/authors preserved |
| [CSX](https://github.com/SaurabhKaperwan/CSX) | Published plugin index | Original plugin metadata/authors preserved |
| [NetMirror Extension](https://github.com/Sushan64/NetMirror-Extension) | Published plugin index | Original plugin metadata/authors preserved |
| [Storm Extensions](https://github.com/Stormunblessed/storm-ext) | Published plugin index | Original plugin metadata/authors preserved |

### Known source not currently aggregated

| Upstream | Status | Reason |
| --- | --- | --- |
| [Hexated CloudStream Extensions](https://github.com/Hexated/CloudStream-Extensions) | Not aggregated | No published `builds/plugins.json` index was available when the aggregator was designed |

## What happens on every update

1. Fetch each active upstream `plugins.json`.
2. Record whether every upstream fetch succeeded.
3. Merge plugin entries without modifying their code or authorship.
4. Deduplicate by `internalName` / `name`; the higher plugin version wins.
5. Compare the candidate catalog with the current production catalog.
6. Generate source-level and plugin-level status tables.
7. Block publication if:
   - any active upstream fetch fails;
   - fewer than 50 unique plugins remain;
   - production already has 50+ plugins and the candidate drops by more than 20%;
   - duplicate plugin names remain after deduplication.
8. Publish with a normal Git push only after all checks pass.

## Repository structure

```text
.github/workflows/build.yml   Production aggregation + safety workflow
tools/merge_upstreams.py      Merge, deduplication and status-report generator
repo.json                     CloudStream repository manifest
README.md                     Project documentation
builds branch                 Live plugins.json, STATUS.md, reports and history
```

The previous compile-based source mirror, Gradle project, provider source copies, and repair scripts were retired after the recovery. They remain available in the rollback branch:

```text
backup/pre-aggregator-master-20261004
```

## Attribution and upstream policy

Showing the upstream repositories is intentional. This repository depends on their published indexes, and clear attribution makes provenance and maintenance easier to audit. It also avoids implying that third-party plugins were authored by this repository maintainer.

This aggregator does not claim ownership of upstream plugins. Each plugin remains subject to its upstream author metadata, repository terms, and applicable license. If an upstream maintainer asks for their repository to be removed from the aggregator, it should be removed from the active source list.

## Disclaimer

This repository is an index/aggregation project. It does not host video or media content. Availability and behavior of individual plugins are controlled by their respective upstream projects and the third-party services they interact with.

Maintained by [Adam Knight](https://github.com/admknight).
