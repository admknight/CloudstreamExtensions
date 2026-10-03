#!/usr/bin/env python3
import json
import sys
import urllib.request
from pathlib import Path

SOURCES = [
    ("phisher", "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/plugins.json"),
    ("cinephile", "https://raw.githubusercontent.com/rockhero1234/cinephile/builds/plugins.json"),
    ("csx", "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/plugins.json"),
    ("netmirror", "https://raw.githubusercontent.com/Sushan64/NetMirror-Extension/builds/plugins.json"),
    ("storm", "https://raw.githubusercontent.com/Stormunblessed/storm-ext/builds/plugins.json"),
]

def fetch_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "CloudstreamExtensions-Aggregator/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)

def plugin_key(plugin):
    return plugin.get("internalName") or plugin.get("name")

def version_value(plugin):
    value = plugin.get("version", 0)
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0

def main():
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "merged")
    out_dir.mkdir(parents=True, exist_ok=True)

    merged = {}
    source_counts = {}
    duplicate_decisions = []

    for source_name, url in SOURCES:
        plugins = fetch_json(url)
        if not isinstance(plugins, list):
            raise RuntimeError(f"{source_name} did not return a plugin list")
        source_counts[source_name] = len(plugins)

        for plugin in plugins:
            key = plugin_key(plugin)
            if not key:
                continue

            candidate = dict(plugin)
            candidate["_aggregatorSource"] = source_name

            if key not in merged:
                merged[key] = candidate
                continue

            current = merged[key]
            keep_candidate = version_value(candidate) > version_value(current)
            winner = candidate if keep_candidate else current
            loser = current if keep_candidate else candidate
            merged[key] = winner
            duplicate_decisions.append({
                "plugin": key,
                "keptSource": winner.get("_aggregatorSource"),
                "keptVersion": version_value(winner),
                "droppedSource": loser.get("_aggregatorSource"),
                "droppedVersion": version_value(loser),
            })

    plugins = []
    source_distribution = {}
    for key in sorted(merged, key=str.casefold):
        plugin = merged[key]
        source = plugin.pop("_aggregatorSource", "unknown")
        source_distribution[source] = source_distribution.get(source, 0) + 1
        plugins.append(plugin)

    if len(plugins) < 50:
        raise RuntimeError(f"Safety check failed: only {len(plugins)} unique plugins were merged")

    repo_json = {
        "name": "Adam Knight Extensions",
        "description": "One-stop CloudStream repository aggregated from maintained upstream plugin indexes",
        "manifestVersion": 1,
        "pluginLists": [
            "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/builds/plugins.json"
        ],
    }

    report = {
        "sourceCounts": source_counts,
        "uniquePlugins": len(plugins),
        "sourceDistributionAfterDedup": source_distribution,
        "duplicates": duplicate_decisions,
    }

    (out_dir / "plugins.json").write_text(json.dumps(plugins, indent=2, ensure_ascii=False) + "\n")
    (out_dir / "repo.json").write_text(json.dumps(repo_json, indent=2) + "\n")
    (out_dir / "merge-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))

if __name__ == "__main__":
    main()
