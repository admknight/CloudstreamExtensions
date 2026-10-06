#!/usr/bin/env python3
import argparse
import json
import re
from pathlib import Path

PROVIDER_CLASS_RE = re.compile(r"class\s+\w+\s*:\s*(?:MainAPI|BaseProvider)\s*\(")
MAIN_URL_RE = re.compile(r'override\s+(?:var|val)\s+mainUrl\s*=\s*"([^"]+)"')
LOAD_RE = re.compile(r"override\s+suspend\s+fun\s+load\s*\(")
LOAD_LINKS_RE = re.compile(r"override\s+suspend\s+fun\s+loadLinks\s*\(")

def discover(root: Path):
    modules = {}
    for path in sorted(root.glob("*/src/main/kotlin/**/*.kt")):
        text = path.read_text(encoding="utf-8", errors="ignore")
        module = path.parts[0]
        row = modules.setdefault(
            module,
            {
                "module": module,
                "sources": [],
                "text": "",
                "mainUrls": [],
            },
        )
        row["sources"].append(str(path))
        row["text"] += "\n" + text
        row["mainUrls"].extend(MAIN_URL_RE.findall(text))

    rows = []
    for module, row in sorted(modules.items()):
        text = row.pop("text")
        if not PROVIDER_CLASS_RE.search(text) and not row["mainUrls"]:
            continue

        has_load = bool(LOAD_RE.search(text))
        has_load_links = bool(LOAD_LINKS_RE.search(text))
        uses_load_extractor = "loadExtractor(" in text
        emits_direct = "newExtractorLink(" in text or re.search(r"\bExtractorLink\s*\(", text) is not None
        uses_m3u8_helper = "M3u8Helper." in text
        typed_links = "ExtractorLinkType." in text
        allowlist_filtered = (
            "isSupportedPublicEmbed" in text
            or "isAuthorizedEmbed" in text
            or ".filter { isSupported" in text
            or ".filter { isAuthorized" in text
        )

        if not has_load:
            status = "incomplete"
            reason = "load() is not implemented"
        elif not has_load_links:
            status = "incomplete"
            reason = "loadLinks() is not implemented"
        elif not (uses_load_extractor or emits_direct or uses_m3u8_helper):
            status = "review"
            reason = "loadLinks() exists but no standard extractor/direct-link emission pattern was detected"
        else:
            status = "implemented"
            reason = ""

        if uses_load_extractor and (emits_direct or uses_m3u8_helper):
            mechanism = "hybrid"
        elif uses_load_extractor:
            mechanism = "extractor"
        elif emits_direct or uses_m3u8_helper:
            mechanism = "direct"
        else:
            mechanism = "unknown"

        rows.append(
            {
                **row,
                "hasLoad": has_load,
                "hasLoadLinks": has_load_links,
                "usesLoadExtractor": uses_load_extractor,
                "emitsDirectLinks": emits_direct,
                "usesM3u8Helper": uses_m3u8_helper,
                "explicitLinkType": typed_links,
                "allowlistFiltered": allowlist_filtered,
                "playbackMechanism": mechanism,
                "architectureState": status,
                "reason": reason,
            }
        )
    return rows

def render_markdown(rows):
    implemented = sum(1 for x in rows if x["architectureState"] == "implemented")
    review = sum(1 for x in rows if x["architectureState"] == "review")
    incomplete = sum(1 for x in rows if x["architectureState"] == "incomplete")
    icons = {"implemented": "✅", "review": "⚠️", "incomplete": "❌"}

    lines = [
        "# Custom Provider Architecture Audit",
        "",
        f"Providers inspected: **{len(rows)}** · Implemented: **{implemented}** · Review: **{review}** · Incomplete: **{incomplete}**",
        "",
        "> Static architecture audit only. It verifies the CloudStream provider contract at source level; it does not prove that a third-party website is reachable or that playback succeeds.",
        "",
        "| Provider | Architecture | load() | loadLinks() | Playback path | Explicit type | Allowlist filter | Reason |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]

    for row in rows:
        state = row["architectureState"]
        reason = (row["reason"] or "—").replace("|", "\\|")
        lines.append(
            f"| {row['module']} | {icons[state]} {state} | "
            f"{'✅' if row['hasLoad'] else '❌'} | "
            f"{'✅' if row['hasLoadLinks'] else '❌'} | "
            f"{row['playbackMechanism']} | "
            f"{'✅' if row['explicitLinkType'] else '—'} | "
            f"{'yes' if row['allowlistFiltered'] else 'no'} | {reason} |"
        )

    lines += [
        "",
        "## Release interpretation",
        "",
        "- **implemented**: the provider implements both load() and loadLinks() and uses a recognized extractor/direct-link emission path.",
        "- **review**: the provider implements the methods but the playback mechanism is non-standard and needs manual review.",
        "- **incomplete**: the provider is missing a required playback-stage method and should not be published as a working provider.",
        "",
        "CloudStream's player receives the data value produced by load(); loadLinks() must resolve that data and emit one or more ExtractorLink callbacks for playback to be considered successful.",
        "",
    ]
    return "\n".join(lines)

def filter_plugins(path: Path, rows):
    plugins = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(plugins, list):
        raise ValueError("plugins.json must contain a JSON array")

    blocked = {
        row["module"].casefold()
        for row in rows
        if row["architectureState"] == "incomplete"
    }
    kept = []
    removed = []

    for plugin in plugins:
        identity = str(plugin.get("internalName") or plugin.get("name") or "").strip()
        if identity.casefold() in blocked:
            removed.append(identity)
        else:
            kept.append(plugin)

    path.write_text(json.dumps(kept, indent=2) + "\n", encoding="utf-8")
    return removed

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--json", dest="json_path", default="provider-architecture.json")
    parser.add_argument("--markdown", dest="markdown_path", default="PROVIDER_ARCHITECTURE.md")
    parser.add_argument("--filter-plugins", default="")
    parser.add_argument("--fail-on-incomplete", action="store_true")
    args = parser.parse_args()

    rows = discover(Path(args.root))
    payload = {
        "providerCount": len(rows),
        "summary": {
            "implemented": sum(1 for x in rows if x["architectureState"] == "implemented"),
            "review": sum(1 for x in rows if x["architectureState"] == "review"),
            "incomplete": sum(1 for x in rows if x["architectureState"] == "incomplete"),
        },
        "providers": rows,
    }

    removed = []
    if args.filter_plugins:
        removed = filter_plugins(Path(args.filter_plugins), rows)
        payload["filteredPlugins"] = removed

    Path(args.json_path).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown_path).write_text(render_markdown(rows) + "\n", encoding="utf-8")

    print(json.dumps({**payload["summary"], "filtered": removed}))

    if args.fail_on_incomplete and payload["summary"]["incomplete"]:
        return 2
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
