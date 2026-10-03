#!/usr/bin/env python3
import argparse
import json
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SOURCES = [
    {
        "id": "phisher",
        "name": "Phisher Repo",
        "repo": "https://github.com/phisher98/cloudstream-extensions-phisher",
        "index": "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/plugins.json",
    },
    {
        "id": "cinephile",
        "name": "Cinephile",
        "repo": "https://github.com/rockhero1234/cinephile",
        "index": "https://raw.githubusercontent.com/rockhero1234/cinephile/builds/plugins.json",
    },
    {
        "id": "csx",
        "name": "CSX",
        "repo": "https://github.com/SaurabhKaperwan/CSX",
        "index": "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/plugins.json",
    },
    {
        "id": "netmirror",
        "name": "NetMirror Extension",
        "repo": "https://github.com/Sushan64/NetMirror-Extension",
        "index": "https://raw.githubusercontent.com/Sushan64/NetMirror-Extension/builds/plugins.json",
    },
    {
        "id": "storm",
        "name": "Storm Extensions",
        "repo": "https://github.com/Stormunblessed/storm-ext",
        "index": "https://raw.githubusercontent.com/Stormunblessed/storm-ext/builds/plugins.json",
    },
]

INACTIVE_SOURCES = [
    {
        "id": "hexated",
        "name": "Hexated CloudStream Extensions",
        "repo": "https://github.com/Hexated/CloudStream-Extensions",
        "reason": "No published builds/plugins.json index is currently used by this aggregator.",
    }
]


def fetch_json(url):
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "CloudstreamExtensions-Aggregator/2.0"},
    )
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


def plugin_key(plugin):
    return plugin.get("internalName") or plugin.get("name")


def version_value(plugin):
    value = plugin.get("version", 0)
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def md(value):
    if value is None:
        return ""
    return str(value).replace("|", "\\|").replace("\n", " ").strip()


def authors_text(plugin):
    authors = plugin.get("authors")
    if isinstance(authors, list):
        return ", ".join(str(x) for x in authors)
    return str(authors or "")


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


def source_summary_table(source_status):
    lines = [
        "| Source | Fetch | Upstream entries | Included after dedup | Duplicate entries dropped |",
        "| --- | --- | ---: | ---: | ---: |",
    ]
    for item in source_status:
        status = "OK" if item["ok"] else "FAILED"
        raw = item.get("rawCount", "-")
        included = item.get("includedCount", 0)
        dropped = item.get("duplicateDropped", 0)
        name = f"[{md(item['name'])}]({item['repo']})"
        lines.append(f"| {name} | {status} | {raw} | {included} | {dropped} |")
    return lines


def build_status_markdown(report, plugin_rows):
    lines = [
        "# Production Aggregation Status",
        "",
        f"Generated: **{report['generatedAt']}**",
        "",
        f"Candidate status: **{report['candidateStatus']}**",
        "",
        f"Previous production plugins: **{report['previousPlugins']}**  ",
        f"Candidate unique plugins: **{report['uniquePlugins']}**",
        "",
        "## Upstream status",
        "",
    ]
    lines.extend(source_summary_table(report["sourceStatus"]))
    lines += [
        "",
        "## Change summary",
        "",
        "| Added | Updated | Unchanged | Removed |",
        "| ---: | ---: | ---: | ---: |",
        f"| {report['changes']['added']} | {report['changes']['updated']} | {report['changes']['unchanged']} | {report['changes']['removed']} |",
        "",
    ]

    if report["duplicates"]:
        lines += [
            "## Duplicate decisions",
            "",
            "| Plugin | Kept | Dropped |",
            "| --- | --- | --- |",
        ]
        for d in report["duplicates"]:
            lines.append(
                f"| {md(d['plugin'])} | {md(d['keptSource'])} v{d['keptVersion']} | "
                f"{md(d['droppedSource'])} v{d['droppedVersion']} |"
            )
        lines.append("")

    if report["removedPlugins"]:
        lines += [
            "## Removed from candidate",
            "",
            "| Plugin | Previous version |",
            "| --- | ---: |",
        ]
        for item in report["removedPlugins"]:
            lines.append(f"| {md(item['plugin'])} | {item['version']} |")
        lines.append("")

    lines += [
        "## Plugin status",
        "",
        "| # | Plugin | Version | Author(s) | Language | Source | Upstream status | Change |",
        "| ---: | --- | ---: | --- | --- | --- | --- | --- |",
    ]
    for idx, row in enumerate(plugin_rows, 1):
        lines.append(
            f"| {idx} | {md(row['name'])} | {row['version']} | {md(row['authors'])} | "
            f"{md(row['language'])} | {md(row['source'])} | {md(row['upstreamStatus'])} | {row['change']} |"
        )

    lines += [
        "",
        "## Known source not currently aggregated",
        "",
        "| Source | Status | Reason |",
        "| --- | --- | --- |",
    ]
    for source in INACTIVE_SOURCES:
        lines.append(
            f"| [{md(source['name'])}]({source['repo']}) | Not aggregated | {md(source['reason'])} |"
        )

    lines += [
        "",
        "> This repository aggregates published upstream indexes. It does not compile or rewrite upstream plugin source code.",
        "",
    ]
    return "\n".join(lines)


def build_readme(report):
    lines = [
        "# Adam Knight Extensions - Production",
        "",
        "Live CloudStream catalog generated from maintained upstream plugin indexes.",
        "",
        "## Latest update",
        "",
        f"- Generated: **{report['generatedAt']}**",
        f"- Unique plugins: **{report['uniquePlugins']}**",
        f"- Added: **{report['changes']['added']}**",
        f"- Updated: **{report['changes']['updated']}**",
        f"- Removed: **{report['changes']['removed']}**",
        "- Repository URL: https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/master/repo.json",
        "",
        "## Upstream status",
        "",
    ]
    lines.extend(source_summary_table(report["sourceStatus"]))
    lines += [
        "",
        "See STATUS.md for the complete per-plugin table and BUILD_HISTORY.md for previous successful publications.",
        "",
        "Publication is blocked if an active upstream cannot be fetched or if the safety checks detect a suspicious catalog drop.",
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

    merged = {}
    source_status = []
    duplicates = []
    failed_sources = []

    for source in SOURCES:
        status = {
            "id": source["id"],
            "name": source["name"],
            "repo": source["repo"],
            "index": source["index"],
            "ok": False,
            "rawCount": None,
            "includedCount": 0,
            "duplicateDropped": 0,
        }
        try:
            plugins = fetch_json(source["index"])
            if not isinstance(plugins, list):
                raise RuntimeError("upstream response is not a plugin list")
            status["ok"] = True
            status["rawCount"] = len(plugins)

            for plugin in plugins:
                key = plugin_key(plugin)
                if not key:
                    continue

                candidate = dict(plugin)
                candidate_source = source["id"]

                if key not in merged:
                    merged[key] = {"plugin": candidate, "source": candidate_source}
                    continue

                current = merged[key]
                keep_candidate = version_value(candidate) > version_value(current["plugin"])
                if keep_candidate:
                    winner_plugin = candidate
                    winner_source = candidate_source
                    loser_plugin = current["plugin"]
                    loser_source = current["source"]
                    merged[key] = {"plugin": candidate, "source": candidate_source}
                else:
                    winner_plugin = current["plugin"]
                    winner_source = current["source"]
                    loser_plugin = candidate
                    loser_source = candidate_source

                duplicates.append(
                    {
                        "plugin": key,
                        "keptSource": winner_source,
                        "keptVersion": version_value(winner_plugin),
                        "droppedSource": loser_source,
                        "droppedVersion": version_value(loser_plugin),
                    }
                )
        except Exception as exc:
            status["error"] = f"{type(exc).__name__}: {exc}"
            failed_sources.append(source["id"])

        source_status.append(status)

    included_by_source = {}
    for entry in merged.values():
        included_by_source[entry["source"]] = included_by_source.get(entry["source"], 0) + 1

    dropped_by_source = {}
    for item in duplicates:
        dropped = item["droppedSource"]
        dropped_by_source[dropped] = dropped_by_source.get(dropped, 0) + 1

    for item in source_status:
        item["includedCount"] = included_by_source.get(item["id"], 0)
        item["duplicateDropped"] = dropped_by_source.get(item["id"], 0)

    plugins = []
    plugin_rows = []
    added = updated = unchanged = 0

    for key in sorted(merged, key=str.casefold):
        entry = merged[key]
        plugin = entry["plugin"]
        source = entry["source"]
        old = previous.get(key)

        if old is None:
            change = "Added"
            added += 1
        elif comparable(old) != comparable(plugin):
            change = "Updated"
            updated += 1
        else:
            change = "Unchanged"
            unchanged += 1

        plugins.append(plugin)
        upstream_status = plugin.get("status")
        if upstream_status == 1:
            upstream_status = "Active"
        elif upstream_status is None:
            upstream_status = "Not stated"

        plugin_rows.append(
            {
                "name": key,
                "version": version_value(plugin),
                "authors": authors_text(plugin),
                "language": plugin.get("language", ""),
                "source": source,
                "upstreamStatus": upstream_status,
                "change": change,
            }
        )

    removed_plugins = []
    for key in sorted(set(previous) - set(merged), key=str.casefold):
        removed_plugins.append(
            {"plugin": key, "version": version_value(previous[key])}
        )

    candidate_status = "READY"
    if failed_sources:
        candidate_status = "BLOCKED - upstream fetch failure"
    elif len(plugins) < 50:
        candidate_status = "BLOCKED - safety floor"

    report = {
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
        "candidateStatus": candidate_status,
        "sourceStatus": source_status,
        "inactiveSources": INACTIVE_SOURCES,
        "previousPlugins": len(previous),
        "uniquePlugins": len(plugins),
        "sourceDistributionAfterDedup": included_by_source,
        "duplicates": duplicates,
        "changes": {
            "added": added,
            "updated": updated,
            "unchanged": unchanged,
            "removed": len(removed_plugins),
        },
        "removedPlugins": removed_plugins,
    }

    repo_json = {
        "name": "Adam Knight Extensions",
        "description": "One-stop CloudStream repository aggregated from maintained upstream plugin indexes",
        "manifestVersion": 1,
        "pluginLists": [
            "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/builds/plugins.json"
        ],
    }

    (output_dir / "plugins.json").write_text(
        json.dumps(plugins, indent=2, ensure_ascii=False) + "\n"
    )
    (output_dir / "repo.json").write_text(json.dumps(repo_json, indent=2) + "\n")
    (output_dir / "merge-report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    )
    (output_dir / "STATUS.md").write_text(build_status_markdown(report, plugin_rows) + "\n")
    (output_dir / "README.md").write_text(build_readme(report) + "\n")

    print(json.dumps(report, indent=2, ensure_ascii=False))

    if failed_sources:
        raise RuntimeError(
            "Publication blocked because these upstreams could not be fetched: "
            + ", ".join(failed_sources)
        )
    if len(plugins) < 50:
        raise RuntimeError(
            f"Safety check failed: only {len(plugins)} unique plugins were merged"
        )


if __name__ == "__main__":
    main()
