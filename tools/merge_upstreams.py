#!/usr/bin/env python3
import argparse
import json
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


def fetch_json(url):
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "AdamKnight-CloudStream-Aggregator/3.0"},
    )
    with urllib.request.urlopen(req, timeout=45) as response:
        return json.load(response)


def plugin_key(plugin):
    return plugin.get("internalName") or plugin.get("name")


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
    return {plugin_key(p): p for p in data if plugin_key(p)}


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


def branded_plugin(plugin):
    result = dict(plugin)
    result["authors"] = []
    result["repositoryUrl"] = AGGREGATOR_REPO
    result["url"] = normalize_package_url(result.get("url"))

    description = str(result.get("description") or "").strip()
    prefix = f"Maintained by {MAINTAINER}"
    if description:
        if prefix.casefold() not in description.casefold():
            result["description"] = f"{prefix} • {description}"
    else:
        result["description"] = prefix

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


def plugin_table(plugin_rows):
    lines = [
        "| # | Plugin | Ver. | Maintainer | Lang | Types | Package | Source | Change |",
        "| ---: | --- | ---: | --- | --- | --- | --- | --- | --- |",
    ]
    for i, row in enumerate(plugin_rows, 1):
        lines.append(
            f"| {i} | **{md(row['name'])}** | {row['version']} | {MAINTAINER} | "
            f"{md(row['language'])} | {md(row['tvTypes'])} | ✅ Reachable | "
            f"{md(row['sourceName'])} | {row['change']} |"
        )
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
        '<h1 align="center">Adam Knight Mega Repo</h1>',
        "",
        '<p align="center">',
        '  <a href="https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml">',
        '    <img src="https://github.com/admknight/CloudstreamExtensions/actions/workflows/build.yml/badge.svg?branch=master&amp;event=push&amp;v=20261004-2" alt="Update Aggregated Repository">',
        '  </a>',
        '</p>',
        "",
        f'<p align="center">A dynamic CloudStream mega repository maintained by <strong>{MAINTAINER}</strong>.</p>',
        "",
        "The catalog is rebuilt from multiple published CloudStream repositories, deduplicated, package-checked, and only then published.",
        "",
        "## 🌐 Quick installation",
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
        "## 📊 Current dashboard",
        "",
        f"Last successful refresh: **{report['generatedAt']}**",
        "",
    ]
    lines.extend(summary_table(report))
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
        "## 📦 Available plugins",
        "",
        f"**{report['uniquePlugins']} plugins are currently published and package-reachable.**",
        "",
    ]
    lines.extend(plugin_table(plugin_rows))
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
        "## 🧭 Status files",
        "",
        "- STATUS.md on the builds branch — detailed current health report",
        "- BUILD_HISTORY.md on the builds branch — successful publication history",
        "- merge-report.json on the builds branch — machine-readable build report",
        "- provenance.json on the builds branch — original source/author provenance retained for maintenance",
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
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    previous = load_previous(args.previous)

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
            plugins = fetch_json(source["index"])
            if not isinstance(plugins, list):
                raise RuntimeError("upstream response is not a plugin list")
            state["ok"] = True
            state["rawCount"] = len(plugins)

            for plugin in plugins:
                key = plugin_key(plugin)
                if not key:
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
                    "plugin": key,
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
        provenance.append(
            {
                "plugin": key,
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
                    "plugin": key,
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

    for key in sorted(selected, key=str.casefold):
        winner = selected[key]
        output_plugin = branded_plugin(winner["plugin"])
        old = previous.get(key)

        if old is None:
            change = "🆕 Added"
            added += 1
        elif comparable(old) != comparable(output_plugin):
            change = "🔄 Updated"
            updated += 1
        else:
            change = "—"
            unchanged += 1

        published_plugins.append(output_plugin)
        plugin_rows.append(
            {
                "name": key,
                "version": winner["version"],
                "language": output_plugin.get("language", ""),
                "tvTypes": tv_types_text(output_plugin),
                "sourceId": winner["source"]["id"],
                "sourceName": winner["source"]["name"],
                "change": change,
            }
        )

    published_keys = {plugin_key(p) for p in published_plugins}
    removed = sorted(set(previous) - published_keys, key=str.casefold)

    candidate_status = "READY"
    if failed_sources:
        candidate_status = "BLOCKED - upstream index failure"
    elif len(published_plugins) < 50:
        candidate_status = "BLOCKED - safety floor"

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
