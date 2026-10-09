#!/usr/bin/env python3
"""Read-only exact-commit comparison of final 16 SHA-less MegaRepo plugins.

Only positively byte-identical original-source binaries are eligible for
review. Different package bytes and unavailable old URLs are strictly held.
"""
import argparse
import hashlib
import json
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from io import BytesIO
from pathlib import Path

GITHUB_ORIGINAL = (
    ("cloudx", "Asm0d3usX/CloudX-V2", "c73809693bc8406a6f8cffd98039e8278e92a95a",
     "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/", 13),
    ("rowdy-recovery", "RowdyRushya/rowdy-cs-extensions",
     "0143d69ce3ec9b7cf09f6d6216212694dc5890a0",
     "https://raw.githubusercontent.com/rushi-chavan/rowdy-cs-extensions/builds/", 1),
)
GITLAB_URL = "https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/"
GITLAB_COMMIT = "05b8e0b8c7b3aa43665fc57c477862cc0888f917"
GITLAB_NAMES = ("MonPlayerProvider", "SyncProvider")
MAX_BYTES = 2 * 1024 * 1024


def download(url, limit=MAX_BYTES):
    allowed = (url.startswith("https://raw.githubusercontent.com/") or
               url.startswith("https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/"))
    if not allowed:
        raise ValueError("Unrecognized download host")
    request = urllib.request.Request(url, headers={"User-Agent": "MegaRepo-Final16-ImmutableEvidence/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        binary = response.read(limit + 1)
        if response.status != 200 or not binary or len(binary) > limit:
            raise ValueError("Bad response or package size exceeds guard")
        return binary


def index_entries(index):
    if not isinstance(index, list):
        raise ValueError("Original manifest must be a list")
    result = {}
    for item in index:
        if not isinstance(item, dict):
            raise ValueError("Malformed source manifest")
        key = str(item.get("internalName") or "").casefold()
        if not key or key in result:
            raise ValueError("Invalid/duplicate original plugin")
        result[key] = item
    return result


def verify_one(published, original, source_repo, revision, pinned_url, mutable_url, get=download):
    name = published["internalName"]
    if (original.get("internalName") != name or
            original.get("version") != published.get("version") or
            original.get("status") != published.get("status") or
            published.get("url") != mutable_url or
            published.get("fileHash") or
            type(published.get("fileSize")) is not int or published["fileSize"] <= 0):
        raise ValueError("Published package identity/status/version does not match source")
    old_manifest_size = original.get("fileSize")
    pinned = get(pinned_url)
    served = get(mutable_url)
    if (not zipfile.is_zipfile(BytesIO(pinned)) or
            len(pinned) != published["fileSize"] or
            served != pinned):
        raise ValueError("Original release bytes/ZIP do not match published package")
    digest = "sha256-" + hashlib.sha256(pinned).hexdigest()
    if original.get("fileHash") and original["fileHash"].lower() != digest:
        raise ValueError("Original hash disagrees with binary")
    if type(old_manifest_size) is int and old_manifest_size != len(pinned):
        raise ValueError("Original manifest fileSize differs: local review required")
    return {
        "plugin": name, "version": published["version"],
        "status": published["status"], "size": len(pinned),
        "sha256": digest, "immutableUrl": pinned_url,
        "mutableUrl": mutable_url, "sourceRepository": source_repo,
        "evidenceUrl": (f"https://github.com/{source_repo}/blob/{revision}/{name}.cs3"
                        if "gitlab.com" not in source_repo else
                        pinned_url.replace("/-/raw/", "/-/blob/")),
        "originalManifestEntry": original, "originalManifestSize": old_manifest_size,
        "immutableOriginalBytesVerified": True,
        "identicalToPublishedBytes": True, "approvedForPublication": False
    }


def inspect(plugins, provenance, get=download, workers=6):
    if not isinstance(plugins, list) or not isinstance(provenance, list) or len(plugins) != len(provenance):
        raise ValueError("Published catalog/provenance mismatch")
    actual = {}
    for p in plugins:
        if not isinstance(p, dict) or not p.get("internalName"):
            raise ValueError("Malformed published entry")
        name = p["internalName"].casefold()
        if name in actual:
            raise ValueError("Duplicate published identity")
        actual[name] = p
    prov = {}
    for p in provenance:
        if not isinstance(p, dict) or not p.get("plugin") or not p.get("sourceId"):
            raise ValueError("Malformed provenance")
        name = p["plugin"].casefold()
        if name in prov:
            raise ValueError("Duplicate original provenance")
        prov[name] = p
    if set(prov) != set(actual):
        raise ValueError("Published plugin/provenance mismatch")
    queue, withheld = [], []
    for sid, repo, revision, mutable_base, expected in GITHUB_ORIGINAL:
        records = [x for name, x in actual.items() if prov[name]["sourceId"] == sid and not x.get("fileHash")]
        if len(records) != expected:
            raise ValueError("Unexpected unsigned count in " + sid)
        pinned_base = f"https://raw.githubusercontent.com/{repo}/{revision}/"
        upstream = index_entries(json.loads(get(pinned_base + "plugins.json")))
        for p in records:
            name = p["internalName"]
            if name.casefold() not in upstream:
                withheld.append({"plugin": name, "sourceId": sid, "reason": "Absent from immutable source"})
                continue
            queue.append((p, upstream[name.casefold()], repo, revision,
                          pinned_base + name + ".cs3", mutable_base + name + ".cs3",sid))
    lab_base = GITLAB_URL + GITLAB_COMMIT + "/"
    upstream = index_entries(json.loads(get(lab_base + "plugins.json")))
    entries = [p for name,p in actual.items() if prov[name]["sourceId"] == "tearrs-vietnamese" and not p.get("fileHash")]
    if len(entries) != 2 or {p["internalName"] for p in entries} != set(GITLAB_NAMES):
        raise ValueError("Unexpected SHA-less GitLab cohort")
    for p in entries:
        name = p["internalName"]
        if name.casefold() not in upstream:
            withheld.append({"plugin": name, "sourceId": "tearrs-vietnamese", "reason": "Absent from immutable source"})
            continue
        queue.append((p, upstream[name.casefold()], "gitlab.com/tearrs/cloudstream-vietnamese",
                      GITLAB_COMMIT, lab_base + name + ".cs3",
                      GITLAB_URL + "main/" + name + ".cs3", "tearrs-vietnamese"))
    verified = []
    with ThreadPoolExecutor(max_workers=max(1, min(workers, 8))) as pool:
        futures = {pool.submit(verify_one, *args[:6], get): args for args in queue}
        for future in as_completed(futures):
            args = futures[future]
            try:
                row = future.result()
                row["sourceId"] = args[6]
                verified.append(row)
            except Exception as exc:
                withheld.append({"plugin": args[0]["internalName"], "sourceId": args[6],
                                "reason": f"{type(exc).__name__}: {exc}"[:260]})
    verified.sort(key=lambda r: (r["sourceId"],r["plugin"].casefold()))
    withheld.sort(key=lambda r: (r["sourceId"],r["plugin"].casefold()))
    if len(verified)+len(withheld)!=16:
        raise ValueError("Missing verification outcome")
    return {"expected": 16, "verifiedCount": len(verified), "heldCount":len(withheld),
            "approvedForPublication": False, "verified": verified, "held":withheld}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--plugins",type=Path,required=True)
    p.add_argument("--provenance",type=Path,required=True)
    p.add_argument("--output",type=Path,required=True)
    args=p.parse_args()
    if args.output.resolve() in {args.plugins.resolve(),args.provenance.resolve()}:
        p.error("May not overwrite published evidence")
    result=inspect(json.loads(args.plugins.read_text()),json.loads(args.provenance.read_text()))
    args.output.write_text(json.dumps(result,indent=2,ensure_ascii=False)+"\n")
    print(json.dumps({"expected":16,"verified":result["verifiedCount"],"held":result["heldCount"]}))
    for x in result["verified"]:
        print("VERIFIED",x["plugin"],x["sourceId"],x["version"],x["size"],x["sha256"],x["immutableUrl"])
    for x in result["held"]:
        print("HELD",x["plugin"],x["sourceId"],x["reason"])


if __name__=="__main__":
    main()
