#!/usr/bin/env python3
"""Find immutable, byte-matching GitHub package revisions for existing catalog entries.

This is a READ-ONLY recovery evidence index, not release approval or publication.
Only hashes/sizes from the PREVIOUSLY PUBLISHED catalog are used as baselines.
A successful match establishes identical bytes at a full Git commit, not author
signatures, safety, third-party runtime correctness, or trust in new versions.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

from verify_candidate_integrity import index_plugins, source_index
from plan_per_plugin_selection import canonical_sha256

SHA256 = re.compile(r"^sha256-([a-f0-9]{64})$", re.I)
GIT_SHA = re.compile(r"^[a-f0-9]{40}$", re.I)
MAX_FILE_BYTES = 20 * 1024 * 1024
HISTORICAL_REVISIONS_FILE = Path(__file__).resolve().parents[1] / "verified_historical_revisions.json"
USER_AGENT = "MegaRepo-Published-Release-Fingerprint/1.0"


def parse_package_url(url):
    if not isinstance(url, str):
        return None
    p = urllib.parse.urlparse(url)
    if (p.scheme != "https" or p.netloc != "raw.githubusercontent.com"
            or p.username or p.password or p.query or p.fragment):
        return None
    segments = p.path.lstrip("/").split("/")
    if len(segments) < 4 or not all(segments):
        return None
    owner, repo = segments[:2]
    if not all(re.fullmatch(r"[A-Za-z0-9_.-]+", x) for x in (owner, repo)):
        return None
    rem = segments[2:]
    if len(rem) >= 4 and rem[:2] == ["refs", "heads"]:
        reference = rem[2]
        relative = rem[3:]
    else:
        reference = rem[0]
        relative = rem[1:]
    if (not reference or reference in (".", "..") or
            not relative or not relative[-1].lower().endswith(".cs3") or
            any(x in ("", ".", "..") for x in relative)):
        return None
    return {"owner": owner, "repo": repo, "ref": reference,
            "path": "/".join(relative)}


def pinned_url(info, sha):
    if not GIT_SHA.fullmatch(sha):
        raise ValueError("A full immutable 40-hex commit is required")
    return (f"https://raw.githubusercontent.com/{info['owner']}/{info['repo']}/"
            f"{sha.lower()}/{info['path']}")


def _http_json(url, token=None):
    headers = {"User-Agent": USER_AGENT, "Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=25) as response:
        return json.load(response)


def _http_bytes(url, max_bytes=MAX_FILE_BYTES):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=35) as response:
        chunks, used = [], 0
        while True:
            block = response.read(65536)
            if not block:
                break
            used += len(block)
            if used > max_bytes:
                raise ValueError("Pinned plugin exceeds download size limit")
            chunks.append(block)
    return b"".join(chunks)


def github_resolve_ref(owner, repo, ref, token=None):
    url = ("https://api.github.com/repos/" + urllib.parse.quote(owner) + "/"
           + urllib.parse.quote(repo) + "/commits/" + urllib.parse.quote(ref, safe=""))
    result = _http_json(url, token)
    sha = str(result.get("sha", ""))
    if not GIT_SHA.fullmatch(sha):
        raise ValueError("GitHub did not return a full Git commit")
    return sha.lower()


def github_history(info, limit=16, token=None):
    root = (f"https://api.github.com/repos/{urllib.parse.quote(info['owner'])}/"
            f"{urllib.parse.quote(info['repo'])}/commits?")
    qs = urllib.parse.urlencode({"sha": info["ref"], "path": info["path"],
                                 "per_page": limit})
    data = _http_json(root + qs, token)
    if not isinstance(data, list):
        raise ValueError("Commit history response was not an array")
    return [row["sha"].lower() for row in data
            if isinstance(row, dict) and GIT_SHA.fullmatch(str(row.get("sha") or ""))]


def compare_pinned_bytes(info, sha, expected_size, expected_hash, fetch_binary):
    url = pinned_url(info, sha)
    actual = fetch_binary(url)
    if not isinstance(actual, (bytes, bytearray)):
        raise ValueError("Package downloader must return bytes")
    observed_size = len(actual)
    observed_digest = "sha256-" + hashlib.sha256(actual).hexdigest()
    return (
        observed_size == expected_size and observed_digest == expected_hash.lower(),
        {"actualFileSize": observed_size, "actualFileHash": observed_digest,
         "url": url}
    )


def index_recovery(plugins, provenance, resolve_ref, list_history, fetch_binary,
                   max_history=16, workers=6, supplemental_revisions=None):
    if supplemental_revisions is None:
        supplemental_revisions = {}
    if not isinstance(supplemental_revisions, dict) or any(
        not isinstance(k, str) or not isinstance(v, list) or len(v) > 16
        or any(not isinstance(x, str) or not GIT_SHA.fullmatch(x) for x in v)
        for k, v in supplemental_revisions.items()
    ):
        raise ValueError("Historical revision hints must contain full Git commit SHAs")
    if not (isinstance(max_history, int) and 0 <= max_history <= 50):
        raise ValueError("history limit must be between 0 and 50")
    if not (isinstance(workers, int) and 1 <= workers <= 8):
        raise ValueError("workers must be between 1 and 8")
    catalog = index_plugins(plugins)
    sources = source_index(provenance)
    provenance_index = {}
    for record in provenance:
        if not isinstance(record, dict):
            raise ValueError("Invalid provenance row")
        identity = str(record.get("plugin") or record.get("originalName") or "").strip().casefold()
        if identity in provenance_index:
            raise ValueError("Duplicated provenance entry: " + identity)
        provenance_index[identity] = record
    if set(catalog) != set(sources):
        raise ValueError("Plugin identities do not reconcile with provenance")

    work = {}
    errors = {}
    for key, entry in catalog.items():
        ref = sources.get(key)
        record = provenance_index[key]
        if not ref:
            errors[key] = "missing_source_id"
            continue
        if str(record.get("packageUrl") or "") != str(entry.get("url") or ""):
            errors[key] = "previous_provenance_package_url_mismatch"
            continue
        digest = entry.get("fileHash")
        size = entry.get("fileSize")
        if digest is None:
            errors[key] = "published_without_sha256"
            continue
        if not isinstance(digest, str) or not SHA256.fullmatch(digest):
            errors[key] = "invalid_published_hash"
            continue
        if type(size) is not int or size <= 0 or size > MAX_FILE_BYTES:
            errors[key] = "invalid_published_size"
            continue
        info = parse_package_url(entry.get("url"))
        if not info:
            errors[key] = "unsupported_or_mutable_non_github_url"
            continue
        work[key] = (entry, record, info)

    refs = {(value[2]["owner"], value[2]["repo"], value[2]["ref"])
            for value in work.values()}
    sha_map = {}
    with ThreadPoolExecutor(max_workers=workers) as pool:
        future_map = {
            pool.submit(resolve_ref, *locator): locator for locator in sorted(refs)
        }
        for future in as_completed(future_map):
            key = future_map[future]
            try:
                result = future.result()
                if not GIT_SHA.fullmatch(str(result)):
                    raise ValueError("Repository ref resolution did not return full SHA")
                sha_map[key] = str(result).lower()
            except Exception as exc:
                sha_map[key] = None

    def check_one(key, payload):
        entry, origin, info = payload
        location = (info["owner"], info["repo"], info["ref"])
        head = sha_map.get(location)
        if not head:
            return None, "unresolvable_repository_commit"
        size = entry["fileSize"]
        digest = entry["fileHash"].lower()
        attempts = [head]
        try:
            same, detail = compare_pinned_bytes(info, head, size, digest, fetch_binary)
            if same:
                return {
                    "plugin": entry.get("internalName") or entry.get("name"),
                    "sourceId": origin["sourceId"],
                    "originalUrl": entry["url"],
                    "pinnedUrl": detail["url"],
                    "commitSha": head,
                    "version": entry.get("version"),
                    "fileSize": size,
                    "fileHash": digest,
                    "originalSourceIndex": origin.get("sourceIndex"),
                    "originalSourceRepository": origin.get("sourceRepository"),
                    "trustBasis": "matches_previously_published_sha256_and_size",
                    "releaseAuthorVerified": False,
                    "historyLookupUsed": False,
                }, None
        except Exception:
            pass
        if max_history:
            try:
                history = list_history(info, max_history)
            except Exception:
                history = []
            history = list(supplemental_revisions.get(key, [])) + history
            for sha in history:
                if not GIT_SHA.fullmatch(str(sha)) or sha.lower() in attempts:
                    continue
                attempts.append(sha.lower())
                try:
                    same, detail = compare_pinned_bytes(
                        info, sha, size, digest, fetch_binary
                    )
                except Exception:
                    continue
                if same:
                    return {
                        "plugin": entry.get("internalName") or entry.get("name"),
                        "sourceId": origin["sourceId"],
                        "originalUrl": entry["url"],
                        "pinnedUrl": detail["url"],
                        "commitSha": sha.lower(),
                        "version": entry.get("version"),
                        "fileSize": size,
                        "fileHash": digest,
                        "originalSourceIndex": origin.get("sourceIndex"),
                        "originalSourceRepository": origin.get("sourceRepository"),
                        "trustBasis": "matches_previously_published_sha256_and_size",
                        "releaseAuthorVerified": False,
                        "historyLookupUsed": True,
                    }, None
        return None, "published_bytes_not_found_in_bounded_immutable_history"

    locks = {}
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(check_one, key, value): key
                   for key, value in work.items()}
        for future in as_completed(futures):
            key = futures[future]
            try:
                result, problem = future.result()
                if result:
                    locks[key] = result
                else:
                    errors[key] = problem
            except Exception as exc:
                errors[key] = "verification_exception:" + type(exc).__name__
    counts = Counter(errors.values())
    locked_current = sum(not x["historyLookupUsed"] for x in locks.values())
    locked_history = len(locks) - locked_current
    result = {
        "mode": "READ_ONLY_PINNED_RECOVERY_EVIDENCE",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sourceCatalogDigest": canonical_sha256(plugins),
        "sourceProvenanceDigest": canonical_sha256(provenance),
        "publishedCount": len(plugins),
        "validPublishedSha256Count": len(work) + counts.get("unsupported_or_mutable_non_github_url", 0),
        "immutableMatches": len(locks),
        "matchedCurrentCommit": locked_current,
        "matchedPriorCommit": locked_history,
        "unmatchedCount": len(errors),
        "unmatchedReasons": dict(sorted(counts.items())),
        "locks": [locks[key] for key in sorted(locks)],
        "unmatched": [{"plugin": key, "reason": errors[key]} for key in sorted(errors)],
        "autoPublicationAuthorized": False,
        "warning": (
            "A lock pins bytes matching the previously published checksum and size, "
            "not a signed author-approved release. Never approve new/different "
            "binaries, modify production, or claim runtime functionality based "
            "on this evidence alone."
        ),
    }
    if len(result["locks"]) + len(result["unmatched"]) != len(plugins):
        raise ValueError("Recovery inventory accounting mismatch")
    return result


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--published", required=True, type=Path)
    p.add_argument("--provenance", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    p.add_argument("--max-history", type=int, default=16)
    p.add_argument("--workers", type=int, default=6)
    args = p.parse_args(argv)
    if args.output.resolve() in (args.published.resolve(), args.provenance.resolve()):
        p.error("Recovery output must never overwrite a production input")
    entries = json.loads(args.published.read_text(encoding="utf-8"))
    origin = json.loads(args.provenance.read_text(encoding="utf-8"))
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    hints = json.loads(HISTORICAL_REVISIONS_FILE.read_text(encoding="utf-8")) if HISTORICAL_REVISIONS_FILE.is_file() else {}
    result = index_recovery(
        entries, origin,
        lambda owner, repo, ref: github_resolve_ref(owner, repo, ref, token),
        lambda info, limit: github_history(info, limit, token),
        _http_bytes,
        max_history=args.max_history,
        workers=args.workers,
        supplemental_revisions=hints,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({
        "publishedCount": result["publishedCount"],
        "immutableMatches": result["immutableMatches"],
        "matchedCurrentCommit": result["matchedCurrentCommit"],
        "matchedPriorCommit": result["matchedPriorCommit"],
        "unmatchedCount": result["unmatchedCount"],
        "unmatchedReasons": result["unmatchedReasons"],
        "autoPublicationAuthorized": False,
    }, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
