# 🎯 Adam Knight Cloudstream Extensions

This repository contains a curated collection of extensions for [Cloudstream 3](https://github.com/recloudstream/cloudstream), maintained and automatically synchronized by **Adam Knight**.

![Build Status](https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml/badge.svg)
![Sync Status](https://github.com/admknight/CloudstreamExtensions/actions/workflows/sync.yml/badge.svg)

---

## 🌐 Installation

To add these extensions to your Cloudstream app:

1. Open **Cloudstream** and navigate to **Settings** (⚙️) -> **Extensions**.
2. Tap on **Add Repository**.
3. Enter the following URL:
   ```text
   https://raw.githubusercontent.com/admknight/CloudstreamExtensions/builds/plugins.json
   ```
4. Tap **Add Repository** and you are ready to go!

---

## ✨ Featured Plugins

This repository hosts over **50+ plugins**, including:

*   **🎬 Bollywood & Regional**: Cinevood, Mp4Moviez, Tamilblasters, SkymoviesHD, HDhub4u, Vegamovies.
*   **🌍 International**: ShowBox, Goojara, Cinemacity, Kisskh.
*   **🎌 Anime & Cartoons**: AnimePahe, AllWish, AnimeDekho, DoraBash.
*   **📺 Live TV & Tools**: IPTVPlayer, Jellyfin, BingedReview.

*The list is updated automatically every 24 hours to ensure you have the latest working versions.*

---

## 🔄 Automation & Sync

This repository uses a custom **Auto-Sync Engine** that pulls the latest code from various community sources.

*   **Branded Content**: All plugins are automatically built with the **Adam Knight** author tag.
*   **Self-Hosted Icons**: Icons are mirrored locally to ensure they never break.
*   **Daily Updates**: The sync runs every midnight to pull in new features and fixes.

---

## 🛠️ Development

If you want to build or test these plugins yourself:

### 🔨 Building a Plugin
- **Windows**: `.\gradlew.bat <PluginName>:make`
- **Linux/Mac**: `./gradlew <PluginName>:make`

### 🚀 Deploying for Testing (ADB)
1. Connect your device.
2. Run: `.\gradlew.bat <PluginName>:deployWithAdb`

---

## ⚖️ DMCA & Disclaimer

* **No Hosting**: This repository does not host any video files, movies, or media content. 
* **Browser-like Functionality**: These extensions act as a specialized web browser that fetches links from third-party websites. 
* **Responsibility**: The developer of this repository is not responsible for any content viewed via these extensions. 
* **Copyright**: If you have issues with copyrighted material, please contact the third-party file hosts directly.

---

## 📜 License

Everything in this repo is released into the **Public Domain**. You are free to use, modify, and distribute it however you like.

---
*Maintained with ❤️ by [Adam Knight](https://github.com/admknight)*
