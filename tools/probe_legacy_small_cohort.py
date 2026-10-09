#!/usr/bin/env python3
"""Read-only proof of nine legacy packages at original immutable Git commits.

Compare committed upstream metadata, original commit-pinned .cs3 ZIP bytes and
the currently published mutable package bytes. This is *not* release approval.
"""
import argparse
import hashlib
import json
import urllib.request
import zipfile
from io import BytesIO
from pathlib import Path

# Explicit small, manageable first cohort; approved releases are separate.
COHORT = (
    ("Gian-Fr/ItalianProvider", "e34c60c1139308f2265e57631abf2576addf73b0",
     ("AltadefinizioneProvider", "GuardaSerieProvider")),
    ("rockhero1234/cinephile", "9aebfedfde585c7312edb1378ac287a6a847a59e",
     ("BingedReview", "SkymoviesHD")),
    ("CranberrySoup/AniyomiCompatExtension", "0a539fd454179399dd34b38ef5d4d3db49ce4971",
     ("AniyomiProvider",)),
    ("self-similarity/MegaRepo", "d8b64bab636845a89503469f1c3cb13fdc750a8a",
     ("MegaProvider",)),
    ("kim20598/cloudstream-extensions-test", "3e2792be0585090691a9d3421d902c29d7736c0f",
     ("DramaDrip", "IndianTVProvider", "UltimaBeta")),
)
MAX_BYTES = 2 * 1024 * 1024


def download(url):
    if not url.startswith("https://raw.githubusercontent.com/"):
        raise ValueError("Unsupported original-source artifact host")
    request = urllib.request.Request(url, headers={
        "User-Agent": "MegaRepo-Legacy-OriginalCommitEvidence/1.0",
    })
    with urllib.request.urlopen(request, timeout=40) as response:
        data = response.read(MAX_BYTES + 1)
        if response.status != 200 or len(data) > MAX_BYTES:
            raise ValueError("Original artifact missing or too large")
        return data


def analyze(published, get=download):
    if not isinstance(published, list):
        raise ValueError("Published catalog must be a list")
    names = {}
    for item in published:
        if not isinstance(item, dict):
            raise ValueError("Malformed published plugin")
        name = item.get("internalName")
        if not isinstance(name, str) or not name or name.casefold() in names:
            raise ValueError("Missing/duplicate published identity")
        names[name.casefold()] = item
    results = []
    for repo, revision, selection in COHORT:
        raw = f"https://raw.githubusercontent.com/{repo}/"
        pinned = raw + revision + "/"
        upstream = json.loads(get(pinned + "plugins.json"))
        if not isinstance(upstream, list):
            raise ValueError("Original upstream manifest must be a list: " + repo)
        for name in selection:
            original = [p for p in upstream if isinstance(p, dict)
                        and str(p.get("internalName") or "").casefold() == name.casefold()]
            if len(original) != 1:
                raise ValueError("Missing/duplicate original source release: " + name)
            source = original[0]
            old = names.get(name.casefold())
            if old is None:
                raise ValueError("Original plugin is not in published catalog: " + name)
            mutable = raw + "builds/" + name + ".cs3"
            if (old.get("url") != mutable or old.get("fileHash") or
                    old.get("version") != source.get("version") or
                    old.get("fileSize") != source.get("fileSize") or
                    old.get("status") != source.get("status") or
                    type(old.get("version")) is not int or
                    type(old.get("fileSize")) is not int or
                    old["fileSize"] <= 0):
                raise ValueError("Published and committed original metadata differ: " + name)
            pinned_url = pinned + name + ".cs3"
            committed_bytes = get(pinned_url)
            mutable_bytes = get(mutable)
            if (len(committed_bytes) != old["fileSize"] or
                    committed_bytes != mutable_bytes or
                    not zipfile.is_zipfile(BytesIO(committed_bytes))):
                raise ValueError("Original immutable vs currently published package differs: " + name)
            digest = "sha256-" + hashlib.sha256(committed_bytes).hexdigest()
            if source.get("fileHash") and source["fileHash"].lower() != digest:
                raise ValueError("Original manifest hash disagrees: " + name)
            results.append({
                "plugin": name, "originalRepo": repo, "revision": revision,
                "originalManifestUrl": pinned + "plugins.json",
                "reviewEvidenceUrl": f"https://github.com/{repo}/blob/{revision}/{name}.cs3",
                "immutableUrl": pinned_url, "publishedMutableUrl": mutable,
                "version": old["version"], "size": old["fileSize"],
                "sha256": digest, "originalManifestEntry": source,
                "immutableMatchesCurrentPublishedBytes": True,
                "originCommitChecked": True, "releaseAuthorized": False,
            })
    return {
        "cohortSize": len(results), "releaseAuthorized": False,
        "publishedPluginCount": len(published), "packages": results,
        "notice": "Original-source commit and live byte match is strong review evidence, but not a signed release or automatic approval.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--published", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.published.resolve() == args.output.resolve():
        parser.error("Evidence output must not overwrite original published catalog")
    report = analyze(json.loads(args.published.read_text(encoding="utf-8")))
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
