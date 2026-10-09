# Changelog

All notable project-level changes to **Adam Knight Mega Repo** are documented here.

The live CloudStream catalog updates continuously from configured upstream repositories. This changelog focuses on repository architecture, automation, custom providers, and stable milestones rather than every upstream plugin version change.

## Companion tools since v1.0.0

- The independent [Personal Bundles](https://github.com/admknight/cloudstream-personal-bundles) Cloudflare Worker creates selected-only CloudStream repository URLs using published MegaRepo metadata without modifying the full catalog.
- The website and README now distinguish the full catalog, selected-only Personal Repository Builder, and discovery-only Extension Explorer.
- The separate Builder maintains its own versioning and release history; this entry does not change the main MegaRepo v1.0.0 milestone or its catalog manifest.

## [1.0.0] - 2026-10-06

### First stable release

Adam Knight Mega Repo v1.0.0 establishes the first production-ready baseline of the project.

### Production baseline

- 540 package-reachable CloudStream plugins at the v1.0 milestone.
- 35 active upstream, recovery, and custom sources.
- 0 package failures at release time.
- Category-based display prefixes while preserving internal plugin identities.
- Source provenance retained separately for maintenance and attribution.

### Automation

- Daily upstream aggregation.
- Automatic version selection and duplicate resolution.
- Package reachability checks before publication.
- Publication safety gates protecting the last known-good catalog.
- Automatic retry logic for GitHub publication operations.
- Build history and machine-readable aggregation reports.
- Generated production README/dashboard.

### Custom providers

The dedicated `custom-builds` branch contains automatically compiled custom providers.

v1.0 includes:

- CinevezProvider v1
- Cinemaluxe v36
- World4uFree v14
- Full4Movies v17
- Cinedoze v1
- India4Movies v1

Custom provider source modules are discovered and built automatically, then published to the custom feed and merged into production.

### Monitoring

- Scheduled custom-provider website health checks.
- Retry/backoff for provider health checks.
- Advisory health reporting that does not remove plugins automatically during temporary provider-site outages.
- GitHub Actions summaries and downloadable health artifacts.

### Discoverability and documentation

- Search-friendly main README.
- Professional build, health, catalog, stars, forks, and release badges.
- Generated README for the `custom-builds` branch.
- GitHub Pages project site at https://admknight.github.io/CloudstreamExtensions/
- Google Search Console ownership verification and indexing setup.
- Sitemap and robots directives for the project site.

### Installation

CloudStream repository shortcode:

```
admknight
```

Repository manifest:

```
https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/master/repo.json
```

### Notes

Package reachability confirms that an extension package can be fetched. It does not guarantee that every third-party provider website or runtime feature will always be operational.

[1.0.0]: https://github.com/admknight/CloudstreamExtensions/releases/tag/v1.0.0
