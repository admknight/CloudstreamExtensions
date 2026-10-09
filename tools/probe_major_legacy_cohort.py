#!/usr/bin/env python3
"""Advisory source-commit and mutable-byte comparison for 79 legacy entries.

Any failed package is reported and remains unapproved. This tool never edits the
published catalog or issues trusted binary approvals.
"""
import argparse
import hashlib
import json
import time
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from io import BytesIO
from pathlib import Path

SOURCES = (
    ("kekik", "maarrem/cs-Kekik", "51034df969e134e1347f4a3abe1e57063474a95f", "builds", 42),
    ("ayu-games", "errorcode26/Ayu-CloudStream-Games", "3945b17e94a0176bf25ee527519ed427db2ce5ec", "builds", 21),
    ("vietnam", "t23-02/cloudstream", "72ea428321cd0f3749773933c42c9a1c3573ab25", "refs/heads/main", 14),
    ("nuyuls-recovery", "nuyuls79/StreamPlay-movie", "e065fad55d0acf955036102dddec92162c56f6ed", "builds", 2),
)
MAX_PACKAGE_BYTES = 12 * 1024 * 1024
MAX_MANIFEST_BYTES = 4 * 1024 * 1024


def download(url, limit):
    if not url.startswith("https://raw.githubusercontent.com/"):
        raise ValueError("Unsupported original-source host")
    last = None
    for attempt in range(2):
        try:
            request = urllib.request.Request(url, headers={
                "User-Agent": "MegaRepo-ReadOnly-Immutable-LegacyEvidence/1.0"
            })
            with urllib.request.urlopen(request, timeout=45) as response:
                if response.status != 200:
                    raise ValueError("Unexpected HTTP " + str(response.status))
                data = response.read(limit + 1)
                if not data or len(data) > limit:
                    raise ValueError("Empty or oversized upstream artifact")
                return data
        except Exception as err:
            last = err
            if attempt == 0:
                time.sleep(0.6)
    raise RuntimeError(f"{type(last).__name__}: {last}") from last


def check_one(definition, old, manifest_entry, get):
    sid, repo, revision, ref, _ = definition
    name = old["internalName"]
    base = f"https://raw.githubusercontent.com/{repo}/"
    published_url = base + ref + "/" + name + ".cs3"
    pinned_url = base + revision + "/" + name + ".cs3"
    if (old.get("url") != published_url or old.get("fileHash") or
            old.get("version") != manifest_entry.get("version") or
            old.get("status") != manifest_entry.get("status") or
            old.get("fileSize") != manifest_entry.get("fileSize") or
            type(old.get("fileSize")) is not int or old["fileSize"] <= 0):
        raise ValueError("Published and original commit metadata do not agree")
    immutable = get(pinned_url, MAX_PACKAGE_BYTES)
    observed = get(published_url, MAX_PACKAGE_BYTES)
    if len(immutable) != old["fileSize"] or immutable != observed:
        raise ValueError("Original commit is not byte-identical to currently published URL")
    if not zipfile.is_zipfile(BytesIO(immutable)):
        raise ValueError("Original .cs3 binary is not a valid ZIP")
    digest = "sha256-" + hashlib.sha256(immutable).hexdigest()
    if manifest_entry.get("fileHash") and manifest_entry["fileHash"].lower() != digest:
        raise ValueError("Original manifest SHA-256 disagrees")
    return {
        "plugin": name, "sourceId": sid, "originalRepo": repo,
        "revision": revision, "version": old["version"], "status": old["status"],
        "size": old["fileSize"], "sha256": digest,
        "immutableUrl": pinned_url, "previousMutableUrl": published_url,
        "reviewEvidenceUrl": f"https://github.com/{repo}/blob/{revision}/{name}.cs3",
        "originalManifestEntry": manifest_entry,
        "byteIdenticalToPublished": True, "releaseAuthorized": False,
    }


def analyze(plugins, provenance, get=download, workers=6):
    if not isinstance(plugins, list) or not isinstance(provenance, list):
        raise ValueError("Published inputs must be complete lists")
    plugin_index, provenance_index = {}, {}
    for item in plugins:
        if not isinstance(item, dict) or not item.get("internalName"):
            raise ValueError("Malformed published plugin")
        key = str(item["internalName"]).casefold()
        if key in plugin_index:
            raise ValueError("Duplicate plugin identity")
        plugin_index[key] = item
    for record in provenance:
        if not isinstance(record, dict) or not record.get("plugin") or not record.get("sourceId"):
            raise ValueError("Malformed provenance")
        key = str(record["plugin"]).casefold()
        if key in provenance_index:
            raise ValueError("Duplicate provenance")
        provenance_index[key] = record
    if set(plugin_index) != set(provenance_index):
        raise ValueError("Published/provenance mismatch")
    work, errors = [], []
    for definition in SOURCES:
        sid, repo, revision, ref, expected = definition
        entries = [p for key, p in plugin_index.items()
                   if provenance_index[key]["sourceId"] == sid and not p.get("fileHash")]
        if len(entries) != expected:
            raise ValueError(f"Unexpected legacy count for {sid}: {len(entries)} != {expected}")
        base = f"https://raw.githubusercontent.com/{repo}/{revision}/"
        upstream = json.loads(get(base + "plugins.json", MAX_MANIFEST_BYTES))
        if not isinstance(upstream, list):
            raise ValueError("Original committed manifest isn't an array: " + sid)
        mapping = {}
        for row in upstream:
            if not isinstance(row, dict):
                continue
            name = str(row.get("internalName") or "").casefold()
            if name in mapping:
                raise ValueError("Duplicate original upstream manifest identity")
            mapping[name] = row
        for item in entries:
            name = item["internalName"]
            if name.casefold() not in mapping:
                errors.append({"plugin": name, "sourceId": sid, "reason": "No committed upstream manifest entry"})
            else:
                work.append((definition, item, mapping[name.casefold()]))
    verified = []
    with ThreadPoolExecutor(max_workers=max(1, min(workers, 8))) as pool:
        futures = {pool.submit(check_one, *job, get): job for job in work}
        for future in as_completed(futures):
            job = futures[future]
            try:
                verified.append(future.result())
            except Exception as exc:
                errors.append({"plugin": job[1]["internalName"], "sourceId": job[0][0],
                               "reason": f"{type(exc).__name__}: {exc}"[:350]})
    verified.sort(key=lambda x: (x["sourceId"], x["plugin"].casefold()))
    errors.sort(key=lambda x: (x["sourceId"], x["plugin"].casefold()))
    total = sum(x[-1] for x in SOURCES)
    if len(verified) + len(errors) != total:
        raise ValueError("Missing evidence results")
    return {
        "expectedCount": total, "verifiedCount": len(verified),
        "withheldCount": len(errors), "releaseAuthorized": False,
        "originalSourceCommitChecked": True,
        "verified": verified, "withheld": errors,
        "notice": "Original source commit bytes match the published version only for verified entries; separate scoped release review is required.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--published", type=Path, required=True)
    parser.add_argument("--provenance", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=6)
    args = parser.parse_args()
    if args.output.resolve() in {args.published.resolve(), args.provenance.resolve()}:
        parser.error("Evidence output cannot overwrite published input")
    result = analyze(json.loads(args.published.read_text(encoding="utf-8")),
                     json.loads(args.provenance.read_text(encoding="utf-8")),
                     workers=args.workers)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")
    print(json.dumps({"expected": result["expectedCount"],
                      "verified": result["verifiedCount"],
                      "withheld": result["withheldCount"],
                      "releaseAuthorized": False}))
    for row in result["withheld"]:
        print("HELD", row["plugin"], row["sourceId"], row["reason"])
    for row in result["verified"]:
        print("VERIFIED", row["plugin"], row["sourceId"], row["version"],
              row["size"], row["sha256"], row["revision"])


if __name__ == "__main__":
    main()
