#!/usr/bin/env python3
import argparse
import json
import re
import urllib.request
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / "sources.json").read_text())
MAINTAINER = CONFIG["maintainer"]
SHORTCODE = CONFIG["shortcode"]
AGGREGATOR_REPO = CONFIG["repositoryUrl"]
SOURCES = CONFIG["sources"]
INACTIVE_SOURCES = CONFIG.get("inactive", [])

CATEGORY_META = [
    ("Movies", "🎬"),
    ("Anime", "🎌"),
    ("Indian", "🇮🇳"),
    ("Arabic", "🌙"),
    ("Asian", "🌏"),
    ("Live", "📡"),
    ("Sports", "🏟️"),
    ("Games", "🎮"),
    ("Tools", "🛠️"),
    ("Adult", "🔞"),
    ("Other", "📦"),
]
CATEGORY_ORDER = [name for name, _ in CATEGORY_META]
CATEGORY_ICONS = dict(CATEGORY_META)


def fetch_json(url):
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "AdamKnight-CloudStream-Aggregator/3.0"},
    )
    with urllib.request.urlopen(req, timeout=45) as response:
        return json.load(response)


def fetch_source_plugins(source):
    """Load one explicit, version-controlled local recovery source safely."""
    local_file = source.get("localFile")
    if local_file is None:
        return fetch_json(source["index"])
    allowed_url = (
        "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/"
        "master/local_verified_plugins.json"
    )
    if (
        source.get("id") != "local-pinned-recovery"
        or local_file != "local_verified_plugins.json"
        or source.get("index") != allowed_url
    ):
        raise ValueError("Unrecognized local source: refusing arbitrary file access")
    path = ROOT / "local_verified_plugins.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("Local verified source must be a JSON array")
    expected = {str(x).strip().casefold() for x in source.get("include", [])}
    seen = set()
    github_pin = re.compile(
        r"^https://raw\\.githubusercontent\\.com/[A-Za-z0-9_.-]+/"
        r"[A-Za-z0-9_.-]+/[a-f0-9]{40}/[^?#]+\\.cs3$"
    )
    gitlab_pin = re.compile(
        r"^https://gitlab\\.com/tearrs/cloudstream-vietnamese/"
        r"-/raw/[a-f0-9]{40}/[^?#]+\\.cs3$"
    )
    for plugin in data:
        if not isinstance(plugin, dict):
            raise ValueError("Malformed local verified entry")
        key = plugin_identity(plugin)
        url = plugin.get("url", "")
        digest = plugin.get("fileHash")
        size = plugin.get("fileSize")
        if (
            not key or key in seen or key not in expected
            or type(plugin.get("version")) is not int
            or type(size) is not int or size <= 0
            or not isinstance(digest, str)
            or re.fullmatch(r"sha256-[a-f0-9]{64}", digest) is None
            or not isinstance(url, str)
            or (github_pin.fullmatch(url) is None
                and gitlab_pin.fullmatch(url) is None)
        ):
            raise ValueError("Invalid/non-immutable local package pin: " + str(key))
        seen.add(key)
    if not expected or seen != expected:
        raise ValueError("Local source and explicitly included identities differ")
    return data


def plugin_key(plugin):
    return plugin.get("internalName") or plugin.get("name")


def plugin_identity(plugin):
    key = plugin_key(plugin)
    return str(key).casefold() if key else None


def base_display_name(plugin):
    name = str(plugin.get("name") or plugin.get("internalName") or "").strip()
    for category in CATEGORY_ORDER:
        prefix = f"[{category}] "
        if name.casefold().startswith(prefix.casefold()):
            return name[len(prefix):].strip()
    return name


def clean_description(value):
    description = str(value or "").strip()
    for prefix in (
        f"Maintained by {MAINTAINER} • ",
        f"Maintained by {MAINTAINER} •",
        f"Maintained by {MAINTAINER}",
    ):
        if description.casefold().startswith(prefix.casefold()):
            description = description[len(prefix):].lstrip(" •-")
            break
    return description


def classify_plugin(plugin, source_id):
    types = {
        str(value).strip().casefold()
        for value in (plugin.get("tvTypes") or [])
        if value is not None
    }
    language = str(plugin.get("language") or "").strip().casefold()
    name = str(plugin.get("internalName") or plugin.get("name") or "").strip().casefold()
    description = str(plugin.get("description") or "").strip().casefold()
    haystack = f"{name} {description}"

    adult_keywords = (
        "porn", "hentai", "xnxx", "xhamster", "xxx", "nsfw",
        "uncut", "erotic", "sex", "jav",
    )
    if source_id == "cxxx" or (
        "nsfw" in types and any(word in haystack for word in adult_keywords)
    ):
        return "Adult"

    if source_id == "ayu-games":
        return "Games"

    tool_keywords = (
        "jellyfin", "m3uplaylistplayer", "sectionorganizer",
        "stremio", "subscriptionmanager", "syncplugin",
        "syncstream", "torrentio", "watchparty",
    )
    if any(word in name for word in tool_keywords):
        return "Tools"

    if "live" in types:
        return "Live"

    sports_keywords = (
        "basketball", "football", "wrestling", "sport",
        "race", "replay", "calcio", "cric",
    )
    if any(word in haystack for word in sports_keywords):
        return "Sports"

    if language in {"hi", "bn", "ta", "te"}:
        return "Indian"

    if language == "ar":
        return "Arabic"

    asian_name_keywords = ("korea", "dorama", "donghua", "asian")
    if language in {"id", "ko", "zh", "fil", "vi"} or (
        "asiandrama" in types and any(word in name for word in asian_name_keywords)
    ):
        return "Asian"

    anime_types = {"anime", "animemovie", "ova"}
    general_types = {
        "movie", "movies", "tvseries", "tv series",
        "documentary", "cartoon", "cartoons", "drama",
    }
    anime_name_signal = (
        "anime" in name
        or name.startswith("ani")
        or "otaku" in name
        or "donghua" in name
    )
    if types.intersection(anime_types) and (
        anime_name_signal or not types.intersection(general_types)
    ):
        return "Anime"

    if types.intersection(general_types | anime_types):
        return "Movies"

    return "Other"


def version_value(plugin):
    try:
        return int(plugin.get("version", 0))
    except (TypeError, ValueError):
        return 0


def md(value):
    if value is None:
        return ""
    return str(value).replace("|", "\\|").replace("\n", " ").strip()


def tv_types_text(plugin):
    values = plugin.get("tvTypes")
    if isinstance(values, list):
        return ", ".join(str(x) for x in values)
    return str(values or "")


def original_authors(plugin):
    authors = plugin.get("authors")
    if isinstance(authors, list):
        return [str(x) for x in authors]
    if authors:
        return [str(authors)]
    return []


def load_previous(path):
    if not path:
        return {}
    file = Path(path)
    if not file.exists():
        return {}
    data = json.loads(file.read_text())
    if not isinstance(data, list):
        return {}
    return {plugin_identity(p): p for p in data if plugin_identity(p)}


def load_json_object(path):
    if not path:
        return {}
    file = Path(path)
    if not file.exists():
        return {}
    try:
        data = json.loads(file.read_text())
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def load_previous_provenance(path):
    if not path:
        return {}
    file = Path(path)
    if not file.exists():
        return {}
    try:
        data = json.loads(file.read_text())
    except Exception:
        return {}
    if not isinstance(data, list):
        return {}

    result = {}
    for row in data:
        if not isinstance(row, dict):
            continue
        key = str(row.get("plugin") or row.get("originalName") or "").strip().casefold()
        if key:
            result[key] = row
    return result


def comparable(plugin):
    return json.dumps(plugin, sort_keys=True, ensure_ascii=False)


def normalize_package_url(url):
    if not url:
        return url
    return str(url).replace(" ", "%20")


def check_package_url(url):
    if not url:
        return {"ok": False, "status": None, "error": "Missing package URL"}

    url = normalize_package_url(url)

    headers = {
        "User-Agent": "AdamKnight-CloudStream-Aggregator/3.0",
        "Accept": "*/*",
    }
    last_error = None

    for _ in range(2):
        try:
            req = urllib.request.Request(url, headers=headers, method="HEAD")
            with urllib.request.urlopen(req, timeout=15) as response:
                status = getattr(response, "status", 200)
                if 200 <= status < 400:
                    return {"ok": True, "status": status, "error": None}
        except Exception as exc:
            last_error = f"{type(exc).__name__}: {exc}"

        try:
            range_headers = dict(headers)
            range_headers["Range"] = "bytes=0-0"
            req = urllib.request.Request(url, headers=range_headers, method="GET")
            with urllib.request.urlopen(req, timeout=20) as response:
                status = getattr(response, "status", 200)
                response.read(1)
                if 200 <= status < 400:
                    return {"ok": True, "status": status, "error": None}
        except Exception as exc:
            last_error = f"{type(exc).__name__}: {exc}"

    return {"ok": False, "status": None, "error": last_error or "Package check failed"}


def branded_plugin(plugin, category):
    result = dict(plugin)
    result["authors"] = [f"{MAINTAINER} (Maintainer)"]
    result["repositoryUrl"] = AGGREGATOR_REPO
    result["url"] = normalize_package_url(result.get("url"))

    original_name = base_display_name(result)
    if original_name:
        result["name"] = f"[{category}] {original_name}"

    description = clean_description(result.get("description"))
    if description:
        result["description"] = description
    else:
        result.pop("description", None)

    return result


def source_table(source_status):
    lines = [
        "| Source | Index | Raw | Published | Package failed | Duplicate-skipped |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
    ]
    for source in source_status:
        state = "✅ OK" if source["ok"] else "❌ FAILED"
        raw = source["rawCount"] if source["rawCount"] is not None else "-"
        lines.append(
            f"| [{md(source['name'])}]({source['repo']}) | {state} | {raw} | "
            f"{source['includedCount']} | {source.get('packageFailed', 0)} | {source['duplicateSkipped']} |"
        )
    return lines


def summary_table(report):
    return [
        "| Available | Package failures | Active sources | Failed sources | Added | Updated | Removed |",
        "| ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        f"| {report['uniquePlugins']} | {report['packageHealth']['failed']} | "
        f"{report['sourceHealth']['ok']} | {report['sourceHealth']['failed']} | "
        f"{report['changes']['added']} | {report['changes']['updated']} | {report['changes']['removed']} |",
    ]


def plugin_table(plugin_rows, use_published_name=True):
    lines = [
        "| # | Plugin | Ver. | Lang | Types | Source | Change |",
        "| ---: | --- | ---: | --- | --- | --- | --- |",
    ]
    for i, row in enumerate(plugin_rows, 1):
        name = row["name"] if use_published_name else row["originalName"]
        lines.append(
            f"| {i} | **{md(name)}** | {row['version']} | "
            f"{md(row['language'])} | {md(row['tvTypes'])} | "
            f"{md(row['sourceName'])} | {row['change']} |"
        )
    return lines


def category_summary_table(report):
    lines = [
        "| Section | CloudStream prefix | Plugins |",
        "| --- | --- | ---: |",
    ]
    counts = report.get("categoryCounts", {})
    for category in CATEGORY_ORDER:
        count = counts.get(category, 0)
        if not count:
            continue
        icon = CATEGORY_ICONS.get(category, "📦")
        lines.append(f"| {icon} **{category}** | `[{category}]` | {count} |")
    return lines


def category_sections(plugin_rows):
    grouped = defaultdict(list)
    for row in plugin_rows:
        grouped[row["category"]].append(row)

    lines = []
    for category in CATEGORY_ORDER:
        rows = grouped.get(category, [])
        if not rows:
            continue
        rows = sorted(rows, key=lambda row: row["originalName"].casefold())
        icon = CATEGORY_ICONS.get(category, "📦")
        lines += [
            f"### {icon} {category} — {len(rows)} plugins",
            "",
            f"CloudStream display prefix: `[{category}]`",
            "",
        ]
        lines.extend(plugin_table(rows, use_published_name=False))
        lines.append("")
    return lines


def failed_table(failed_plugins):
    lines = [
        "| Plugin | Best source checked | Version | Result |",
        "| --- | --- | ---: | --- |",
    ]
    if not failed_plugins:
        lines.append("| — | — | — | ✅ No package failures |")
        return lines

    for item in failed_plugins:
        lines.append(
            f"| {md(item['plugin'])} | {md(item['sourceName'])} | {item['version']} | "
            f"❌ {md(item['error'])} |"
        )
    return lines


def build_readme(report, plugin_rows):
    lines = [
        '<p align="center">',
        '  <img src="https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/master/assets/icon.png" alt="Adam Knight Mega Repo" width="180">',
        '</p>',
        "",
        '<h1 align="center">Adam Knight Mega Repo — CloudStream Extensions & Plugins</h1>',
        "",
        '<p align="center">',
        '  <a href="https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml">',
        '    <img src="https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml/badge.svg?branch=master&amp;event=push&amp;v=20261004-2" alt="Update Aggregated Repository">',
        '  </a>',
        '  <a href="https://github.com/admknight/CloudstreamExtensions/actions/workflows/health.yml">',
        '    <img src="https://github.com/admknight/CloudstreamExtensions/actions/workflows/health.yml/badge.svg?branch=master" alt="Custom Provider Health">',
        '  </a>',
        f'  <img src="https://img.shields.io/badge/plugins-{report["uniquePlugins"]}-2ea44f?style=flat-square" alt="{report["uniquePlugins"]} plugins">',
        f'  <img src="https://img.shields.io/badge/sources-{report["sourceHealth"]["ok"]}-blue?style=flat-square" alt="{report["sourceHealth"]["ok"]} active sources">',
        f'  <img src="https://img.shields.io/badge/package%20failures-{report["packageHealth"]["failed"]}-{"brightgreen" if report["packageHealth"]["failed"] == 0 else "red"}?style=flat-square" alt="{report["packageHealth"]["failed"]} package failures">',
        '  <a href="https://github.com/admknight/CloudstreamExtensions/stargazers">',
        '    <img src="https://img.shields.io/github/stars/admknight/CloudstreamExtensions?style=flat-square" alt="GitHub stars">',
        '  </a>',
        '  <a href="https://github.com/admknight/CloudstreamExtensions/forks">',
        '    <img src="https://img.shields.io/github/forks/admknight/CloudstreamExtensions?style=flat-square" alt="GitHub forks">',
        '  </a>',
        '  <a href="https://github.com/admknight/CloudstreamExtensions/releases/latest">',
        '    <img src="https://img.shields.io/github/v/release/admknight/CloudstreamExtensions?display_name=tag&style=flat-square" alt="Latest release">',
        '  </a>',
        '</p>',
        "",
        f'<p align="center">A dynamic CloudStream extensions and plugins repository maintained by <strong>{MAINTAINER}</strong>.</p>',
        "",
        "Adam Knight Mega Repo is a searchable, automatically updated **CloudStream plugin repository** that aggregates published CloudStream extensions into one installer-friendly catalog. "
        "It is intended for users looking for CloudStream plugins, extension repositories, anime/movie/TV providers, live TV tools, games, and multilingual sources in a single maintained repo.",
        "",
        "The catalog is rebuilt from multiple published CloudStream repositories, deduplicated, package-checked, runtime-monitored for custom providers, and only then published.",
        "",
        "> 🎉 **Stable milestone:** [Adam Knight Mega Repo v1.0.0](https://github.com/admknight/CloudstreamExtensions/releases/tag/v1.0.0) establishes the first production-ready baseline for the automated Mega Repo.",
        "",
        "## Choose the right MegaRepo tool",
        "",
        "**Full MegaRepo = complete catalog. Personal Repository Builder = selected-only repository link. Extension Explorer = discovery and local bookmarks.**",
        "",
        "| Your goal | Where to go | What happens |",
        "| --- | --- | --- |",
        "| **I want access to every published plugin** | **Full MegaRepo** — shortcode `admknight` | Adds one complete plugin catalog to CloudStream. Open it and install individual extensions yourself. |",
        "| **I want a repository with only my picks** | **[Personal Repository Builder](https://adam-cloudstream-bundles.badass-insane.workers.dev/)** | Select up to 100 plugins and generate a personal `repo.json` URL. Add that catalog to CloudStream, then install the plugins you want. |",
        "| **I want to research plugins first** | **[Extension Explorer](https://admknight.github.io/CloudstreamExtensions/explore.html)** | Search, filter and bookmark names in your browser. The Explorer neither creates repository URLs nor installs anything. |",
        "",
        "**Important: adding a repository is not the same as installing a plugin.** Full MegaRepo and the Personal Repository Builder create two different catalog choices; in either case, install individual extensions inside CloudStream. Explorer bookmarks are not transferred into the Builder.",
        "",
        "**How they connect:** upstream plugin indexes feed the guarded MegaRepo catalog. The Full MegaRepo exposes that entire catalog; the independent Personal Repository Builder generates a selected-only view; the Explorer is for browser-based research. The Builder follows published metadata for chosen plugin identities, but changing your selection requires a new link. Package reachability does not guarantee a working provider website.",
        "",
        "### Personal Bundles — a separate MegaRepo companion",
        "",
        "**Personal Bundles** is the separate, read-only companion to MegaRepo. Select up to **100** published plugins, generate a personal CloudStream `repo.json` URL, and install individual extensions inside CloudStream. The Worker follows current published package metadata for stable plugin identities without changing the full MegaRepo, hosting packages, or requiring accounts. To change your selection, generate a new link; existing Explorer bookmarks are not imported automatically.",
        "",
        "**Try it:** [Personal Repository Builder](https://adam-cloudstream-bundles.badass-insane.workers.dev/) · **[Independent source repository](https://github.com/admknight/cloudstream-personal-bundles)** · [Extension Explorer](https://admknight.github.io/CloudstreamExtensions/explore.html).",
        "",
        "### Why use this CloudStream repository?",
        "",
        "- One repository URL for hundreds of CloudStream extensions.",
        "- Guarded upstream metadata refresh every three hours, plus a separate hourly read-only integrity audit.",
        "- Duplicate/version resolution so the best reachable package is selected.",
        "- Package reachability checks and publication safety gates.",
        "- Custom provider builds with separate health monitoring.",
        "",
        "",
        "## 🌐 Full MegaRepo installation",
        "",
        "### Preferred: shortcode",
        "",
        "In CloudStream go to **Settings → Extensions → Add Repository** and enter:",
        "",
        f"    {SHORTCODE}",
        "",
        "### Raw URL fallback",
        "",
        "    https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/master/repo.json",
        "",
        "This shortcode and manifest load the **entire published catalog**, not a personal subset. For a selected-only repository, use the [Personal Repository Builder](https://adam-cloudstream-bundles.badass-insane.workers.dev/).",
        "",
        "## 📊 Current dashboard",
        "",
        f"Last successful refresh: **{report['generatedAt']}**",
        "",
    ]
    lines.extend(summary_table(report))
    lines += [
        "",
        "## 🗂️ Browse by section",
        "",
        "Plugins are grouped with a display-name prefix in CloudStream. "
        "The prefix changes only the visible name; the internal plugin identity remains unchanged for updates.",
        "",
    ]
    lines.extend(category_summary_table(report))
    lines += [
        "",
        "### Source health",
        "",
    ]
    lines.extend(source_table(report["sourceStatus"]))
    lines += [
        "",
        "### Package failures",
        "",
        "A package is considered reachable when its published .cs3 URL responds successfully. "
        "This verifies package availability, not whether the underlying provider website still works at runtime.",
        "",
    ]
    lines.extend(failed_table(report["failedPlugins"]))
    lines += [
        "",
        "## 📦 Plugins by section",
        "",
        f"**{report['uniquePlugins']} plugins are currently published and package-reachable.**",
        "",
    ]
    lines.extend(category_sections(plugin_rows))
    lines += [
        "",
        "## 🔁 Duplicate handling",
        "",
        "When the same plugin is published by more than one source, the highest version is preferred; "
        "source priority breaks version ties. Unreachable candidates are skipped in favor of a reachable alternative when possible.",
        "",
    ]
    if report["duplicates"]:
        lines += [
            "| Plugin | Selected | Skipped |",
            "| --- | --- | --- |",
        ]
        for d in report["duplicates"]:
            lines.append(
                f"| {md(d['plugin'])} | {md(d['selectedSource'])} v{d['selectedVersion']} | "
                f"{md(d['skippedSource'])} v{d['skippedVersion']} ({md(d['reason'])}) |"
            )
    else:
        lines.append("No duplicates in the current candidate set.")

    lines += [
        "",
        "## 🤖 Automation & monitoring",
        "",
        "- Guarded production aggregation runs every three hours at minute **17 UTC** and on relevant configuration changes.",
        "- A separate read-only package integrity audit runs hourly; it compares upstream metadata and rotates direct package SHA-256 checks without modifying published manifests.",
        "- Custom-provider website health runs every day at **01:30 UTC** and on custom provider source changes.",
        "- Publication pushes retry automatically up to **3 times** before a workflow is marked failed.",
        "- Runtime health is advisory: a temporary provider-site outage is reported but does not remove or overwrite the last known-good production catalog.",
        "",
        "## 🧭 Status files",
        "",
        "- STATUS.md on the builds branch — detailed current health report",
        "- BUILD_HISTORY.md on the builds branch — successful publication history",
        "- merge-report.json on the builds branch — machine-readable build report",
        "- provenance.json on the builds branch — original source/author provenance retained for maintenance",
        "- RELEASE_NOTES.md on the builds branch — auto-generated notes for the latest production diff",
        "- release-diff.json on the builds branch — machine-readable plugin/source/custom-provider change details",
        "",
        "## 🛡️ Publication safety",
        "",
        "Production is not overwritten when an active source index fails. A large unexpected catalog drop is also blocked by the safety gate, so the last known-good catalog remains live.",
        "",
        "## 🧰 Repository architecture",
        "",
        "This is an aggregator, not a source-code fork. Published CloudStream package URLs are consumed from upstream indexes; "
        "the installer-facing catalog is branded as maintained by Adam Knight, while original provenance is retained separately for maintenance and attribution.",
        "",
        "## ⚖️ Disclaimer",
        "",
        "This repository is an index/aggregation project and does not host video or media content. "
        "Package reachability does not guarantee that every third-party provider website is operational at runtime.",
        "",
        f"*Maintained by {MAINTAINER}*",
        "",
    ]
    return "\n".join(lines)


def release_item_line(item, mode):
    name = md(item.get("plugin") or item.get("name") or "Unknown")
    source = md(item.get("sourceName") or item.get("toSource") or "Unknown source")
    if mode == "updated":
        old_version = item.get("fromVersion", "?")
        new_version = item.get("toVersion", "?")
        old_source = md(item.get("fromSource") or "")
        source_text = source
        if old_source and old_source != source:
            source_text = f"{old_source} → {source}"
        return f"- **{name}** v{old_version} → v{new_version} — {source_text}"
    version = item.get("version", "?")
    return f"- **{name}** v{version} — {source}"


def build_release_notes(report):
    details = report.get("changeDetails", {})
    source_changes = report.get("sourceChanges", {})
    custom_changes = report.get("customProviderChanges", [])
    changes = report.get("changes", {})

    lines = [
        "# Adam Knight Mega Repo — Production Change Notes",
        "",
        f"Generated: **{report['generatedAt']}**",
        "",
        f"Production catalog: **{report['uniquePlugins']} reachable plugins** · "
        f"**{report['sourceHealth']['ok']} healthy sources** · "
        f"**{report['packageHealth']['failed']} package failures**",
        "",
        "## Summary",
        "",
        f"- Added: **{changes.get('added', 0)}**",
        f"- Updated: **{changes.get('updated', 0)}**",
        f"- Removed: **{changes.get('removed', 0)}**",
        f"- Unchanged: **{changes.get('unchanged', 0)}**",
        "",
    ]

    recovered = details.get("recovered", [])
    added = details.get("added", [])
    updated = details.get("updated", [])
    removed = details.get("removed", [])

    if recovered:
        lines += ["## Recovered plugins", ""]
        lines.extend(release_item_line(x, "added") for x in recovered)
        lines.append("")

    if added:
        lines += ["## Added plugins", ""]
        lines.extend(release_item_line(x, "added") for x in added)
        lines.append("")

    if updated:
        lines += ["## Updated plugins", ""]
        lines.extend(release_item_line(x, "updated") for x in updated)
        lines.append("")

    if removed:
        lines += ["## Removed plugins", ""]
        lines.extend(release_item_line(x, "removed") for x in removed)
        lines.append("")

    source_added = source_changes.get("added", [])
    source_removed = source_changes.get("removed", [])
    source_health = source_changes.get("healthChanged", [])
    if source_added or source_removed or source_health:
        lines += ["## Source changes", ""]
        for row in source_added:
            lines.append(f"- Added source: **{md(row.get('name'))}** (`{md(row.get('id'))}`)")
        for row in source_removed:
            lines.append(f"- Removed source: **{md(row.get('name'))}** (`{md(row.get('id'))}`)")
        for row in source_health:
            before = "healthy" if row.get("fromOk") else "failed"
            after = "healthy" if row.get("toOk") else "failed"
            lines.append(f"- **{md(row.get('name'))}** health changed: {before} → {after}")
        lines.append("")

    if custom_changes:
        lines += ["## Custom provider changes", ""]
        for row in custom_changes:
            action = row.get("action")
            if action == "updated":
                lines.append(release_item_line(row, "updated"))
            else:
                lines.append(f"- **{md(row.get('plugin'))}** v{row.get('version', '?')} — {action}")
        lines.append("")

    has_meaningful = any([
        recovered, added, updated, removed,
        source_added, source_removed, source_health, custom_changes,
    ])
    if not has_meaningful:
        lines += [
            "## Catalog changes",
            "",
            "No plugin or source changes were detected in this production refresh.",
            "",
        ]

    lines += [
        "## Production health",
        "",
        f"- Reachable packages: **{report['packageHealth']['reachable']}**",
        f"- Package failures: **{report['packageHealth']['failed']}**",
        f"- Healthy sources: **{report['sourceHealth']['ok']}**",
        f"- Failed sources: **{report['sourceHealth']['failed']}**",
        "",
        "> These notes are generated automatically from the production diff. Stable releases still require explicit manual approval.",
        "",
    ]
    return "\n".join(lines)


def build_status(report, plugin_rows):
    lines = [
        "# Production Aggregation Status",
        "",
        f"Generated: **{report['generatedAt']}**",
        "",
        f"Candidate status: **{report['candidateStatus']}**",
        "",
    ]
    lines.extend(summary_table(report))
    lines += ["", "## Source health", ""]
    lines.extend(source_table(report["sourceStatus"]))
    lines += ["", "## Failed packages", ""]
    lines.extend(failed_table(report["failedPlugins"]))
    lines += ["", "## Published plugins", ""]
    lines.extend(plugin_table(plugin_rows))
    lines += ["", "## Duplicate decisions", ""]
    if report["duplicates"]:
        lines += [
            "| Plugin | Selected | Skipped |",
            "| --- | --- | --- |",
        ]
        for d in report["duplicates"]:
            lines.append(
                f"| {md(d['plugin'])} | {md(d['selectedSource'])} v{d['selectedVersion']} | "
                f"{md(d['skippedSource'])} v{d['skippedVersion']} ({md(d['reason'])}) |"
            )
    else:
        lines.append("No duplicates.")
    lines += [
        "",
        "> Package health verifies that the published plugin package can be fetched. "
        "It is not a runtime test of the third-party provider website.",
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output_dir", nargs="?", default="merged")
    parser.add_argument("--previous", default=None)
    parser.add_argument("--previous-report", default=None)
    parser.add_argument("--previous-provenance", default=None)
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    previous = load_previous(args.previous)
    previous_report = load_json_object(args.previous_report)
    previous_provenance = load_previous_provenance(args.previous_provenance)

    candidates = defaultdict(list)
    source_status = []
    failed_sources = []

    for source in SOURCES:
        state = {
            "id": source["id"],
            "name": source["name"],
            "repo": source["repo"],
            "index": source["index"],
            "priority": source.get("priority", 100),
            "ok": False,
            "rawCount": None,
            "includedCount": 0,
            "packageFailed": 0,
            "duplicateSkipped": 0,
        }
        try:
            plugins = fetch_source_plugins(source)
            if not isinstance(plugins, list):
                raise RuntimeError("upstream response is not a plugin list")
            state["ok"] = True
            state["rawCount"] = len(plugins)

            include = {
                str(value).casefold()
                for value in (source.get("include") or [])
                if value is not None
            }
            exclude = {
                str(value).casefold()
                for value in (source.get("exclude") or [])
                if value is not None
            }

            for plugin in plugins:
                key = plugin_identity(plugin)
                if not key:
                    continue
                if include and key not in include:
                    continue
                if key in exclude:
                    continue
                candidates[key].append(
                    {
                        "plugin": dict(plugin),
                        "source": source,
                        "version": version_value(plugin),
                    }
                )
        except Exception as exc:
            state["error"] = f"{type(exc).__name__}: {exc}"
            failed_sources.append(source["id"])

        source_status.append(state)

    unique_urls = {}
    for group in candidates.values():
        for item in group:
            url = item["plugin"].get("url")
            if url and url not in unique_urls:
                unique_urls[url] = None

    with ThreadPoolExecutor(max_workers=24) as executor:
        future_map = {executor.submit(check_package_url, url): url for url in unique_urls}
        for future in as_completed(future_map):
            url = future_map[future]
            try:
                unique_urls[url] = future.result()
            except Exception as exc:
                unique_urls[url] = {
                    "ok": False,
                    "status": None,
                    "error": f"{type(exc).__name__}: {exc}",
                }

    selected = {}
    duplicate_rows = []
    failed_plugins = []
    provenance = []

    for key in sorted(candidates, key=str.casefold):
        group = sorted(
            candidates[key],
            key=lambda item: (-item["version"], item["source"].get("priority", 100)),
        )

        winner = None
        for item in group:
            url = item["plugin"].get("url")
            health = unique_urls.get(url) if url else {"ok": False, "error": "Missing package URL"}
            item["health"] = health
            if health and health.get("ok"):
                winner = item
                break

        if winner is None:
            best = group[0]
            failed_plugins.append(
                {
                    "plugin": plugin_key(best["plugin"]) or key,
                    "sourceId": best["source"]["id"],
                    "sourceName": best["source"]["name"],
                    "version": best["version"],
                    "url": best["plugin"].get("url"),
                    "error": (best.get("health") or {}).get("error", "No reachable candidate"),
                }
            )
            continue

        selected[key] = winner

        original = winner["plugin"]
        category = classify_plugin(original, winner["source"]["id"])
        winner["category"] = category
        original_name = base_display_name(original)
        published_name = f"[{category}] {original_name}" if original_name else str(plugin_key(original) or key)
        provenance.append(
            {
                "plugin": plugin_key(original) or key,
                "originalName": original_name,
                "publishedName": published_name,
                "category": category,
                "maintainer": f"{MAINTAINER} (Maintainer)",
                "selectedVersion": winner["version"],
                "sourceId": winner["source"]["id"],
                "sourceName": winner["source"]["name"],
                "sourceRepository": winner["source"]["repo"],
                "sourceIndex": winner["source"]["index"],
                "originalAuthors": original_authors(original),
                "originalRepositoryUrl": original.get("repositoryUrl"),
                "packageUrl": original.get("url"),
            }
        )

        for item in group:
            if item is winner:
                continue
            health = item.get("health") or unique_urls.get(item["plugin"].get("url"), {})
            if not health.get("ok"):
                reason = "package unreachable"
            elif item["version"] < winner["version"]:
                reason = "lower version"
            else:
                reason = "lower source priority"
            duplicate_rows.append(
                {
                    "plugin": plugin_key(winner["plugin"]) or key,
                    "selectedSource": winner["source"]["name"],
                    "selectedVersion": winner["version"],
                    "skippedSource": item["source"]["name"],
                    "skippedVersion": item["version"],
                    "reason": reason,
                }
            )

    included_by_source = Counter(item["source"]["id"] for item in selected.values())
    name_to_id = {source["name"]: source["id"] for source in SOURCES}
    skipped_by_id = Counter()
    for item in duplicate_rows:
        source_id = name_to_id.get(item["skippedSource"])
        if source_id:
            skipped_by_id[source_id] += 1

    failed_by_source = Counter(item["sourceId"] for item in failed_plugins)

    for state in source_status:
        state["includedCount"] = included_by_source.get(state["id"], 0)
        state["packageFailed"] = failed_by_source.get(state["id"], 0)
        state["duplicateSkipped"] = skipped_by_id.get(state["id"], 0)

    published_plugins = []
    plugin_rows = []
    added = updated = unchanged = 0
    added_details = []
    recovered_details = []
    updated_details = []
    previous_removed = {
        str(value).strip().casefold()
        for value in (previous_report.get("removedPlugins") or [])
        if value is not None
    }

    for key in sorted(selected, key=str.casefold):
        winner = selected[key]
        output_plugin = branded_plugin(winner["plugin"], winner["category"])
        old = previous.get(key)

        detail_base = {
            "plugin": plugin_key(winner["plugin"]) or key,
            "name": base_display_name(winner["plugin"]) or str(plugin_key(winner["plugin"]) or key),
            "version": winner["version"],
            "sourceId": winner["source"]["id"],
            "sourceName": winner["source"]["name"],
            "category": winner["category"],
        }

        if old is None:
            change = "🆕 Added"
            added += 1
            if key in previous_removed:
                recovered_details.append(dict(detail_base))
            else:
                added_details.append(dict(detail_base))
        elif comparable(old) != comparable(output_plugin):
            change = "🔄 Updated"
            updated += 1
            old_provenance = previous_provenance.get(key, {})
            updated_details.append({
                **detail_base,
                "fromVersion": version_value(old),
                "toVersion": winner["version"],
                "fromSourceId": old_provenance.get("sourceId"),
                "fromSource": old_provenance.get("sourceName"),
                "toSourceId": winner["source"]["id"],
                "toSource": winner["source"]["name"],
            })
        else:
            change = "—"
            unchanged += 1

        published_plugins.append(output_plugin)
        plugin_rows.append(
            {
                "name": output_plugin.get("name", plugin_key(winner["plugin"]) or key),
                "originalName": base_display_name(winner["plugin"]) or str(plugin_key(winner["plugin"]) or key),
                "category": winner["category"],
                "version": winner["version"],
                "language": output_plugin.get("language", ""),
                "tvTypes": tv_types_text(output_plugin),
                "sourceId": winner["source"]["id"],
                "sourceName": winner["source"]["name"],
                "change": change,
            }
        )

    published_keys = {plugin_identity(p) for p in published_plugins if plugin_identity(p)}
    removed_ids = set(previous) - published_keys
    removed = sorted(
        [str(plugin_key(previous[key]) or key) for key in removed_ids],
        key=str.casefold,
    )
    removed_details = []
    for key in sorted(removed_ids, key=str.casefold):
        old = previous[key]
        old_provenance = previous_provenance.get(key, {})
        removed_details.append({
            "plugin": plugin_key(old) or key,
            "name": base_display_name(old) or str(plugin_key(old) or key),
            "version": version_value(old),
            "sourceId": old_provenance.get("sourceId"),
            "sourceName": old_provenance.get("sourceName") or "Unknown source",
            "category": old_provenance.get("category"),
        })

    current_sources = {row["id"]: row for row in source_status}
    previous_sources = {
        row.get("id"): row
        for row in (previous_report.get("sourceStatus") or [])
        if isinstance(row, dict) and row.get("id")
    }
    source_added = [
        {"id": source_id, "name": current_sources[source_id].get("name"), "repo": current_sources[source_id].get("repo")}
        for source_id in sorted(set(current_sources) - set(previous_sources))
    ]
    source_removed = [
        {"id": source_id, "name": previous_sources[source_id].get("name"), "repo": previous_sources[source_id].get("repo")}
        for source_id in sorted(set(previous_sources) - set(current_sources))
    ]
    source_health_changed = []
    for source_id in sorted(set(current_sources).intersection(previous_sources)):
        before = bool(previous_sources[source_id].get("ok"))
        after = bool(current_sources[source_id].get("ok"))
        if before != after:
            source_health_changed.append({
                "id": source_id,
                "name": current_sources[source_id].get("name"),
                "fromOk": before,
                "toOk": after,
            })

    custom_provider_changes = []
    for row in recovered_details:
        if row.get("sourceId") == "adam-custom":
            custom_provider_changes.append({**row, "action": "recovered"})
    for row in added_details:
        if row.get("sourceId") == "adam-custom":
            custom_provider_changes.append({**row, "action": "added"})
    for row in updated_details:
        if row.get("toSourceId") == "adam-custom" or row.get("fromSourceId") == "adam-custom":
            custom_provider_changes.append({**row, "action": "updated"})
    for row in removed_details:
        if row.get("sourceId") == "adam-custom":
            custom_provider_changes.append({**row, "action": "removed"})

    candidate_status = "READY"
    if failed_sources:
        candidate_status = "BLOCKED - upstream index failure"
    elif len(published_plugins) < 50:
        candidate_status = "BLOCKED - safety floor"

    category_counts = Counter(row["category"] for row in plugin_rows)

    report = {
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
        "maintainer": MAINTAINER,
        "shortcode": SHORTCODE,
        "candidateStatus": candidate_status,
        "sourceHealth": {
            "ok": sum(1 for x in source_status if x["ok"]),
            "failed": sum(1 for x in source_status if not x["ok"]),
        },
        "sourceStatus": source_status,
        "inactiveSources": INACTIVE_SOURCES,
        "categoryCounts": {
            category: category_counts.get(category, 0)
            for category in CATEGORY_ORDER
            if category_counts.get(category, 0)
        },
        "previousPlugins": len(previous),
        "uniquePlugins": len(published_plugins),
        "packageHealth": {
            "reachable": len(published_plugins),
            "failed": len(failed_plugins),
        },
        "duplicates": duplicate_rows,
        "failedPlugins": failed_plugins,
        "changes": {
            "added": added,
            "updated": updated,
            "unchanged": unchanged,
            "removed": len(removed),
        },
        "changeDetails": {
            "added": added_details,
            "recovered": recovered_details,
            "updated": updated_details,
            "removed": removed_details,
        },
        "sourceChanges": {
            "added": source_added,
            "removed": source_removed,
            "healthChanged": source_health_changed,
        },
        "customProviderChanges": custom_provider_changes,
        "removedPlugins": removed,
    }

    repo_json = {
        "name": "Adam Knight Mega Repo",
        "description": "Dynamic CloudStream mega repository maintained by Adam Knight",
        "iconUrl": "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/master/assets/icon.png",
        "manifestVersion": 1,
        "pluginLists": [
            "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/builds/plugins.json"
        ],
    }

    (output_dir / "plugins.json").write_text(
        json.dumps(published_plugins, indent=2, ensure_ascii=False) + "\n"
    )
    (output_dir / "repo.json").write_text(json.dumps(repo_json, indent=2) + "\n")
    (output_dir / "merge-report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    )
    (output_dir / "provenance.json").write_text(
        json.dumps(provenance, indent=2, ensure_ascii=False) + "\n"
    )
    release_diff = {
        "generatedAt": report["generatedAt"],
        "candidateStatus": report["candidateStatus"],
        "catalog": {
            "plugins": report["uniquePlugins"],
            "healthySources": report["sourceHealth"]["ok"],
            "failedSources": report["sourceHealth"]["failed"],
            "reachablePackages": report["packageHealth"]["reachable"],
            "packageFailures": report["packageHealth"]["failed"],
        },
        "changes": report["changes"],
        "changeDetails": report["changeDetails"],
        "sourceChanges": report["sourceChanges"],
        "customProviderChanges": report["customProviderChanges"],
    }
    (output_dir / "release-diff.json").write_text(
        json.dumps(release_diff, indent=2, ensure_ascii=False) + "\n"
    )
    (output_dir / "RELEASE_NOTES.md").write_text(build_release_notes(report) + "\n")
    (output_dir / "STATUS.md").write_text(build_status(report, plugin_rows) + "\n")
    (output_dir / "README.md").write_text(build_readme(report, plugin_rows) + "\n")

    print(json.dumps(report, indent=2, ensure_ascii=False))

    if failed_sources:
        raise RuntimeError(
            "Publication blocked because these source indexes could not be fetched: "
            + ", ".join(failed_sources)
        )
    if len(published_plugins) < 50:
        raise RuntimeError(
            f"Safety check failed: only {len(published_plugins)} reachable plugins remain"
        )


if __name__ == "__main__":
    main()
