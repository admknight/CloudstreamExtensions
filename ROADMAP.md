# Roadmap

Adam Knight Mega Repo follows a stability-first roadmap. The live catalog continues to update automatically, while major repository/automation changes are grouped into release milestones.

## v1.0.0 — Stable foundation ✅

Released 2026-10-06.

Established:

- automated upstream aggregation;
- package reachability checks;
- version-aware duplicate resolution;
- publication safety gates;
- custom-provider build/publish pipeline;
- scheduled provider health monitoring;
- GitHub Pages live dashboard;
- public release snapshots;
- contribution/issue intake;
- promotion and discovery infrastructure.

## v1.1 — Reliability & observability

Primary goal: make the project easier to maintain as the catalog and community grow.

Tracked work:

- [x] [#4 — Add health history and trend data](https://github.com/admknight/CloudstreamExtensions/issues/4)
- [ ] [#5 — Automate release notes from production diffs](https://github.com/admknight/CloudstreamExtensions/issues/5)
- [ ] [#6 — Add source quality and stability scoring](https://github.com/admknight/CloudstreamExtensions/issues/6)
- [ ] [#7 — Detect custom-provider domain drift and redirects](https://github.com/admknight/CloudstreamExtensions/issues/7)

v1.1 should preserve the current last-known-good publication model. Observability features should inform maintenance decisions rather than silently removing sources/providers.

## v1.2 — Discovery & community

Potential focus after v1.1:

- richer contributor/source documentation;
- better source provenance exploration;
- searchable/filterable plugin catalog UI;
- community-tested source recommendations;
- additional project discovery/SEO improvements based on real traffic and Search Console data.

## v2.0 — Major architecture changes

Reserved for changes that materially alter repository contracts, build/publish architecture, catalog format, or compatibility assumptions.

No v2.0 work is planned until the v1.x automation and observability model has proven stable.

## Principles

1. **Stability over plugin count** — a smaller known-good catalog is better than a larger unreliable one.
2. **Transparent provenance** — preserve where plugins originate.
3. **Non-destructive monitoring** — transient provider failures should be visible without causing automatic removal.
4. **Repeatable automation** — routine maintenance should be reproducible in GitHub Actions.
5. **Publicly explainable rules** — version, priority, health, and safety decisions should be understandable from generated reports.
6. **Safe maintenance boundaries** — no DRM, CAPTCHA, authentication/access-control, or anti-bot bypass work.
