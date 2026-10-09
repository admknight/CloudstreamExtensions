#!/usr/bin/env python3
"""Read-only first-party Desi StreamHubOne immutable-release evidence.

Neither downloading a package nor matching its published hash constitutes an
approval. The exact reviewed record and guarded candidate gate are separate.
"""
import hashlib
import json
import re
import urllib.request
import zipfile
from io import BytesIO
from pathlib import Path

OWNER_REPO = "Faisal0786/Desi"
REV = "0af83282ff9d36e0cc7447582b7152cc948fd1cc"
NAME = "StreamHubOne"
BASE = f"https://raw.githubusercontent.com/{OWNER_REPO}/{REV}/"
MAX_BYTES = 3 * 1024 * 1024

def download(url):
    request = urllib.request.Request(url, headers={
        "User-Agent": "MegaRepo-Immutable-Desi-Evidence/1.0"
    })
    with urllib.request.urlopen(request, timeout=40) as response:
        data = response.read(MAX_BYTES + 1)
        if response.status != 200 or len(data) > MAX_BYTES:
            raise ValueError("Unexpected response or oversized source artifact")
        return data

def collect(get=download):
    index = json.loads(get(BASE + "plugins.json"))
    if not isinstance(index, list):
        raise ValueError("Pinned upstream index is not a list")
    matches = [p for p in index if isinstance(p, dict)
               and p.get("internalName", "").casefold() == NAME.casefold()]
    if len(matches) != 1:
        raise ValueError("StreamHubOne missing or ambiguous at pinned commit")
    plugin = matches[0]
    package = get(BASE + NAME + ".cs3")
    if not zipfile.is_zipfile(BytesIO(package)):
        raise ValueError("Pinned binary isn't a valid .cs3 ZIP")
    actual = "sha256-" + hashlib.sha256(package).hexdigest()
    declared = plugin.get("fileHash")
    declared_size = plugin.get("fileSize")
    if (not isinstance(declared, str) or
            not re.fullmatch(r"sha256-[a-f0-9]{64}", declared) or
            actual != declared or type(declared_size) is not int or
            len(package) != declared_size or
            not isinstance(plugin.get("version"), int)):
        raise ValueError("Original immutable upstream manifest and package disagree")
    return {
        "sourceRepository": "https://github.com/" + OWNER_REPO,
        "commitSha": REV,
        "plugin": NAME,
        "version": plugin["version"],
        "size": len(package),
        "sha256": actual,
        "immutablePackageUrl": BASE + NAME + ".cs3",
        "upstreamManifestUrl": BASE + "plugins.json",
        "upstreamReleaseSignatureVerified": False,
        "approvedForPublication": False,
    }

if __name__ == "__main__":
    report = collect()
    path = Path("desi-immutable-evidence.json")
    path.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
    print(path.read_text())
