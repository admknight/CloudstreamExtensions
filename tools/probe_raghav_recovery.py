#!/usr/bin/env python3
"""Independent read-only verification of four changed Raghav build packages.

Outputs the exact committed upstream metadata and measured binary fingerprints.
It does not grant release approval or change any published entry.
"""
import hashlib
import json
import re
import urllib.request
import zipfile
from io import BytesIO
from pathlib import Path

OWNER_REPO = "KSHITIJ8473/raghav"
REV = "bbd6dcf1318d7c76bbf8c853ec120b857dd7df22"
NAMES = ("AnimeTH", "Anv", "JustPlay", "TorrentsV1")
BASE = f"https://raw.githubusercontent.com/{OWNER_REPO}/{REV}/"
MAX_BYTES = 2 * 1024 * 1024

def download(url):
    request = urllib.request.Request(url, headers={
        "User-Agent": "MegaRepo-Immutable-Raghav-Evidence/1.0"
    })
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read(MAX_BYTES + 1)
        if response.status != 200 or len(data) > MAX_BYTES:
            raise ValueError("Unexpected upstream response or oversized package")
        return data

def collect(get=download):
    index = json.loads(get(BASE + "plugins.json"))
    if not isinstance(index, list):
        raise ValueError("Original source manifest is not a list")
    packages = []
    for name in NAMES:
        rows = [x for x in index if isinstance(x, dict)
                and str(x.get("internalName") or "").casefold() == name.casefold()]
        if len(rows) != 1:
            raise ValueError("Missing or ambiguous upstream release: " + name)
        entry = rows[0]
        binary = get(BASE + name + ".cs3")
        if not zipfile.is_zipfile(BytesIO(binary)):
            raise ValueError("Original upstream .cs3 is not a ZIP: " + name)
        digest = "sha256-" + hashlib.sha256(binary).hexdigest()
        if (entry.get("fileHash") != digest or
                type(entry.get("fileSize")) is not int or
                entry["fileSize"] != len(binary) or
                type(entry.get("version")) is not int or
                not re.fullmatch("sha256-[0-9a-f]{64}", digest)):
            raise ValueError("Original manifest/digest mismatch: " + name)
        packages.append({
            "plugin": name,
            "commitSha": REV,
            "metadata": entry,
            "size": len(binary),
            "sha256": digest,
            "immutablePackageUrl": BASE + name + ".cs3",
            "sourceSignatureVerified": False,
            "approvedForPublication": False,
        })
    return {"sourceRepository": "https://github.com/" + OWNER_REPO,
            "commitSha": REV, "packages": packages,
            "automaticApproval": False}

if __name__ == "__main__":
    report = collect()
    path = Path("raghav-immutable-evidence.json")
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(path.read_text())
