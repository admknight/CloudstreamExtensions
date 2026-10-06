# Contributing to Adam Knight Mega Repo

Thanks for helping improve the project.

## Best ways to contribute

### Report a broken plugin/provider

Use the **Broken plugin or provider** issue form and include:

- plugin/provider name;
- plugin version if known;
- what is failing;
- clear reproduction steps;
- CloudStream/device version when relevant.

Package reachability and provider-site runtime availability are different signals, so please be specific about whether the package itself is unavailable or the provider website/runtime behavior is failing.

### Suggest a repository/source

Use the **Suggest a repository / source** issue form.

Useful source candidates should generally have:

- a maintained public repository;
- a published CloudStream plugin index or otherwise verifiable build output;
- reachable packages;
- identifiable plugin versions/internal names;
- meaningful additional coverage without unnecessary duplication.

Every source is reviewed before it is added. Inclusion is not automatic.

## Safety and maintenance boundaries

This project can work with publicly exposed catalogs, metadata, ordinary links, supported public embeds, and directly available package files.

Please do not request implementations that depend on bypassing:

- DRM;
- CAPTCHA;
- authentication/access controls;
- anti-bot or Cloudflare protections;
- protected link-shortener gates;
- encrypted or obfuscated access mechanisms.

Do not post credentials, session cookies, API secrets, personal data, or private account information in issues.

## Production behavior

The live catalog is generated automatically. Upstream failures, unreachable packages, duplicate versions, and suspicious catalog drops are evaluated by the build pipeline before publication.

Changes should preserve the last-known-good publication model rather than weakening safety gates for the sake of increasing plugin count.

## Project links

- Dashboard: https://admknight.github.io/CloudstreamExtensions/
- Repository: https://github.com/admknight/CloudstreamExtensions
- Latest release: https://github.com/admknight/CloudstreamExtensions/releases/latest
- Promotion kit: ./PROMOTION.md
