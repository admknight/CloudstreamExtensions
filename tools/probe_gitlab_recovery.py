#!/usr/bin/env python3
"""Read-only GitLab immutable release evidence for locally curated recovery.

No package is approved here; the selection gate separately checks exact pinned
hashes and locally reviewed approvals before a build can publish anything.
"""
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

PROJECT = "65865366"
BASE = "https://gitlab.com/tearrs/cloudstream-vietnamese"
NAMES = ("StremioProvider", "ViStreamProvider", "XtreamIPTVProvider", "IPTVProvider")
MAX_BYTES = 2 * 1024 * 1024


def download(url):
    req = urllib.request.Request(url, headers={
        "User-Agent": "MegaRepo-Immutable-Evidence/1.0",
        "Accept": "application/json, application/octet-stream;q=0.8",
    })
    with urllib.request.urlopen(req, timeout=28) as response:
        data = response.read(MAX_BYTES + 1)
        if response.status != 200 or len(data) > MAX_BYTES:
            raise ValueError("Unexpected download response or limit exceeded")
        return data


def collect(get=download):
    branch = json.loads(get(
        "https://gitlab.com/api/v4/projects/" + PROJECT + "/repository/branches/main"
    ))
    sha = str((branch.get("commit") or {}).get("id") or "").lower()
    if len(sha) != 40 or any(c not in "0123456789abcdef" for c in sha):
        raise ValueError("GitLab branch endpoint did not provide a commit SHA")
    base = BASE + "/-/raw/" + sha + "/"
    entries = json.loads(get(base + "plugins.json"))
    if not isinstance(entries, list):
        raise ValueError("Pinned GitLab index is not a list")
    result = []
    for name in NAMES:
        matches = [p for p in entries
                   if str(p.get("internalName") or p.get("name") or "").lower() == name.lower()]
        if len(matches) != 1:
            raise ValueError("Missing or duplicate pinned plugin: " + name)
        item = matches[0]
        data = get(base + name + ".cs3")
        if not data.startswith(b"PK"):
            raise ValueError("Pinned .cs3 payload is not a ZIP: " + name)
        result.append({
            "plugin": name, "upstreamManifestVersion": item.get("version"),
            "upstreamDeclaredSize": item.get("fileSize"),
            "size": len(data), "sha256": "sha256-" + hashlib.sha256(data).hexdigest(),
            "url": base + name + ".cs3",
            "manifest": base + "plugins.json",
            "repository": BASE,
            "upstreamReleaseSignatureVerified": False,
            "approvedForPublication": False,
        })
    return {"source": BASE, "commitSha": sha, "verifiedAtPinnedGitRevision": True,
            "packages": result, "automaticBinaryApproval": False}


def main():
    report = collect()
    out = Path("gitlab-immutable-evidence.json")
    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(out.read_text())


if __name__ == "__main__":
    sys.exit(main())
