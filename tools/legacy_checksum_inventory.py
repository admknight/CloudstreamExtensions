#!/usr/bin/env python3
"""Inventory published MegaRepo plugins lacking an authenticated SHA-256.

This is read-only evidence for the next provenance review. Never infer a
trusted checksum or approve a binary from its mutable package URL.
"""
import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from urllib.parse import urlsplit

DIGEST = re.compile(r"sha256-[a-f0-9]{64}\Z", re.I)
COMMIT = re.compile(r"[a-f0-9]{40}\Z", re.I)


def immutable_url(url):
    try:
        parsed = urlsplit(url)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
            return False
        pieces = [p for p in parsed.path.split("/") if p]
        if parsed.hostname == "raw.githubusercontent.com":
            return (len(pieces) >= 4 and COMMIT.fullmatch(pieces[2]) is not None)
        if parsed.hostname == "gitlab.com":
            return ("-/raw" in parsed.path and
                    any(COMMIT.fullmatch(p) for p in pieces))
        return False
    except (ValueError, TypeError):
        return False


def identity(obj, keys):
    if not isinstance(obj, dict):
        return ""
    return str(next((obj.get(k) for k in keys if obj.get(k)), "")).strip().casefold()


def analyze(plugins, provenance, report):
    if not isinstance(plugins, list) or not isinstance(provenance, list) or not isinstance(report, dict):
        raise ValueError("Published catalog evidence must be structured")
    if report.get("uniquePlugins") != len(plugins) or len(provenance) != len(plugins):
        raise ValueError("Published catalog counts differ")
    source = {}
    for row in provenance:
        name = identity(row, ("plugin", "originalName"))
        if not name or name in source:
            raise ValueError("Duplicate or missing provenance identity")
        source[name] = row
    entries, names = [], set()
    for item in plugins:
        name = identity(item, ("internalName", "name"))
        if not name or name in names or name not in source:
            raise ValueError("Catalog identities or provenance are inconsistent")
        names.add(name)
        digest = item.get("fileHash")
        if digest and isinstance(digest, str) and DIGEST.fullmatch(digest):
            continue
        if digest:
            raise ValueError("Malformed existing fileHash for " + name)
        length = item.get("fileSize")
        if type(length) is not int or length <= 0 or type(item.get("version")) is not int:
            raise ValueError("Legacy metadata missing valid length/version")
        origin = source[name]
        sid = origin.get("sourceId")
        url = item.get("url")
        if not isinstance(sid, str) or not sid or not isinstance(url, str):
            raise ValueError("Missing original source or package URL")
        parsed = urlsplit(url)
        if parsed.scheme != "https" or not parsed.hostname:
            raise ValueError("Legacy package does not have a valid HTTPS URL")
        entries.append({
            "plugin": item.get("internalName") or item.get("name"),
            "sourceId": sid,
            "sourceRepository": origin.get("sourceRepository"),
            "sourceIndex": origin.get("sourceIndex"),
            "version": item["version"],
            "declaredSize": length,
            "packageUrl": url,
            "sourceHost": parsed.hostname,
            "immutablePackageUrl": immutable_url(url),
            "authenticatedReleaseDigest": None,
            "status": "requires_original_source_commit_and_release_review",
        })
    if set(source) != names:
        raise ValueError("Source provenance contains unpublished identities")
    reported = (report.get("integrityHealth") or {}).get("sizeOnlyLegacyCandidateAccepted")
    if type(reported) is not int or reported != len(entries):
        raise ValueError("Reported size-only integrity count disagrees with published plugins")
    per_source = defaultdict(list)
    for item in entries:
        per_source[item["sourceId"]].append(item)
    groups = [{
        "sourceId": sid,
        "legacyEntries": len(items),
        "mutableUrls": sum(not item["immutablePackageUrl"] for item in items),
        "immutableUrls": sum(item["immutablePackageUrl"] for item in items),
        "repository": items[0]["sourceRepository"],
        "samplePlugin": items[0]["plugin"],
    } for sid, items in per_source.items()]
    groups.sort(key=lambda row: (-row["legacyEntries"], row["sourceId"]))
    entries.sort(key=lambda row: (row["sourceId"], row["plugin"].casefold()))
    return {
        "generatedFromPublishedReportAt": report.get("generatedAt"),
        "publishedPluginCount": len(plugins),
        "legacyWithoutSha256Count": len(entries),
        "affectedSourceCount": len(groups),
        "readOnly": True, "releaseAuthorized": False,
        "groups": groups, "entries": entries,
        "nextStep": ("Find the original repository's immutable commit, verify each "
                     "package's committed manifest, version, bytes and digest, "
                     "and use scoped reviewed approvals only after independent evidence. "
                     "Never trust a checksum derived solely from the current mutable URL."),
    }


def markdown(result):
    lines = ["# MegaRepo Legacy Package Integrity Inventory", "",
             f"Published snapshot: **{result['generatedFromPublishedReportAt']}**",
             f"Packages without an authenticated SHA-256: **{result['legacyWithoutSha256Count']}**",
             f"Affected original sources: **{result['affectedSourceCount']}**",
             "", "> Advisory evidence only. No plugin was approved or published by this audit.",
             "", "| Source ID | Missing SHA-256 | Mutable URLs | Immutable URLs |",
             "| --- | ---: | ---: | ---: |"]
    for row in result["groups"]:
        lines.append(f"| {row['sourceId']} | {row['legacyEntries']} | "
                     f"{row['mutableUrls']} | {row['immutableUrls']} |")
    lines.extend(["", "## Safe upgrade procedure", "",
                  "1. Pin the **original** upstream repository to an exact commit.",
                  "2. Confirm the committed source manifest version and binary file size; independently SHA-256 hash the pinned binary.",
                  "3. Require a reviewed, exact-match local approval before adding a trusted digest or changing the package URL.",
                  "4. Validate a guarded no-removal release and independent post-publication audit.",
                  "", "No manifest or package metadata was changed by this inventory.", ""])
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--plugins", required=True, type=Path)
    p.add_argument("--provenance", required=True, type=Path)
    p.add_argument("--report", required=True, type=Path)
    p.add_argument("--json", required=True, type=Path)
    p.add_argument("--markdown", required=True, type=Path)
    args = p.parse_args()
    load = lambda p: json.loads(p.read_text(encoding="utf-8"))
    result = analyze(load(args.plugins), load(args.provenance), load(args.report))
    for path, content in (
        (args.json, json.dumps(result, indent=2, ensure_ascii=False) + "\n"),
        (args.markdown, markdown(result)),
    ):
        protected = {args.plugins.resolve(), args.provenance.resolve(), args.report.resolve()}
        if path.resolve() in protected:
            p.error("Inventory output must not overwrite a published input")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    print(json.dumps({"legacyWithoutSha256Count": result["legacyWithoutSha256Count"],
                      "affectedSourceCount": result["affectedSourceCount"],
                      "releaseAuthorized": False}))


if __name__ == "__main__":
    main()
