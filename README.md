# Cloudstream Extensions - Adam Knight

This repository contains various extensions for [Cloudstream 3](https://github.com/recloudstream/cloudstream), maintained by **Adam Knight** (`admknight`).

## Available Plugins

| Sr. # | Name | Description |
| :--- | :--- | :--- |
| 1 | [Cinevood](Cinevood/) | High quality movies and series in Hindi and English. |
| 2 | [BingedReview](BingedReview/) | Movie reviews and streaming info. |
| 3 | [Mp4Moviez](Mp4Moviez/) | Latest Bollywood and Hollywood movies. |
| 4 | [SkymoviesHD](SkymoviesHD/) | South Indian and Bollywood movies in HD. |
| 5 | [Tamilblasters](Tamilblasters/) | Latest Tamil movies and series. |

## Installation

To add these extensions to Cloudstream, follow these steps:

1. Open Cloudstream and go to **Settings** -> **Extensions**.
2. Tap on **Add Repository**.
3. Enter the following URL:
   ```
   https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/plugins.json
   ```
   *(Note: This repository contains all plugins listed above. If you are using a different branch, replace `master` with your branch name, e.g., `main`.)*

## Development

### Building a specific plugin
To build a specific plugin (e.g., Archive Movies):
- Windows: `.\gradlew.bat ArchiveMovies:make`
- Linux & Mac: `./gradlew ArchiveMovies:make`

### Building all plugins
To generate `plugins.json` and build all plugins:
- Windows: `.\gradlew.bat makePluginsJson`
- Linux & Mac: `./gradlew makePluginsJson`

### Deploying for testing
1. Connect your device via ADB.
2. Run the following command:
   - Windows: `.\gradlew.bat ArchiveMovies:deployWithAdb`
   - Linux & Mac: `./gradlew ArchiveMovies:deployWithAdb`

## Granting All Files Access on Newer Android Devices

For local plugin testing, you need to grant the app "All Files Access" on newer Android devices (Android 11 and above).

### Using ADB
* `adb shell appops set --uid PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow`
* Replace `PACKAGE_NAME` with the name of the package for the Cloudstream 3 version you are using (e.g., `com.lagradost.cloudstream3`).

## License

Everything in this repo is released into the public domain.
