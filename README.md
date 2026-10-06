<p align="center">
  <img src="https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/master/assets/icon.png" alt="Adam Knight Custom CloudStream Providers" width="160">
</p>

<h1 align="center">Adam Knight Custom CloudStream Providers</h1>

<p align="center">
  <a href="https://github.com/admknight/CloudstreamExtensions/actions/workflows/custom-build.yml">
    <img src="https://github.com/admknight/CloudstreamExtensions/actions/workflows/custom-build.yml/badge.svg?branch=master" alt="Custom Provider Build">
  </a>
  <a href="https://github.com/admknight/CloudstreamExtensions/actions/workflows/health.yml">
    <img src="https://github.com/admknight/CloudstreamExtensions/actions/workflows/health.yml/badge.svg?branch=master" alt="Provider Health">
  </a>
  <img src="https://img.shields.io/badge/custom%20plugins-7-2ea44f?style=flat-square" alt="7 custom plugins">
  <a href="https://github.com/admknight/CloudstreamExtensions/stargazers">
    <img src="https://img.shields.io/github/stars/admknight/CloudstreamExtensions?style=flat-square" alt="GitHub stars">
  </a>
</p>

This branch contains the **compiled custom CloudStream extensions** maintained as part of the Adam Knight Mega Repo. It is generated automatically from source modules on the `master` branch; files here should not be edited manually.

## Install

### Full Mega Repo (recommended)

In CloudStream: **Settings → Extensions → Add Repository** and enter:

    admknight

### Custom providers feed only

    https://raw.githubusercontent.com/admknight/CloudstreamExtensions/custom-builds/repo.json

## Custom providers

**7 custom CloudStream plugins are currently published on this branch.**

| # | Provider | Version | Lang | Types | Description |
| ---: | --- | ---: | --- | --- | --- |
| 1 | **Cinedoze** | 1 | hi | Movie, TvSeries | Cinedoze catalogue, search and metadata for the current CineDoze domain. |
| 2 | **Cinemaluxe** | 36 | hi | Movie, TvSeries | Cinemaluxe catalogue, search and metadata for the current CinemaLux domain. Playback is limited to directly exposed supported public embeds. |
| 3 | **CinevezProvider** | 1 | hi | Movie, TvSeries | Cinevez catalogue: browse, search and metadata. Playback is limited to supported authorized embeds. |
| 4 | **Cinevood** | 12 | hi | Movie, TvSeries, Anime, AsianDrama | Cinevood catalogue, search and metadata for the current CineVood domain. Playback is intentionally not implemented. |
| 5 | **Full4Movies** | 17 | hi | Movie, TvSeries | Full4Movies catalogue, search and metadata for the current FullMoviesMX domain. |
| 6 | **India4Movies** | 1 | hi | Movie, TvSeries | India4Movies catalogue, search and metadata for the current India4Movies frontend. |
| 7 | **World4uFree** | 14 | hi | Movie, TvSeries | World4uFree catalogue, search and metadata for the current Worldfree4u domain. Playback is limited to directly exposed supported public embeds. |

## Automation

- Source code lives on the `master` branch.
- GitHub Actions compiles all discovered custom provider modules.
- Successful builds publish `.cs3`, `.jar`, `plugins.json`, and this README automatically.
- The custom feed automatically triggers the production Mega Repo aggregation.
- A separate scheduled workflow checks configured provider websites and reports runtime availability.

## Branch files

- `repo.json` — CloudStream repository manifest.
- `plugins.json` — generated custom plugin catalog.
- `*.cs3` — installable CloudStream plugin packages.
- `*.jar` — generated plugin JARs.

## Disclaimer

This project is an extension index/build project and does not host video or media content. Package availability and website health checks do not guarantee that every third-party provider feature will work at runtime.

*Maintained by Adam Knight*
