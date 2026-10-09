#!/usr/bin/env python3
"""Use immutable recovery locks ONLY for byte-identical old plugin fallbacks.

Every recovery index is bound to one previous catalog/provenance snapshot. Every
lock must match identity, URL, source, version, size and pre-existing checksum.
A fallback candidate is independently downloaded and checksum-verified again.
No different/new upstream binary is approved by this module.
"""
import copy
import hashlib
import json
import re
from urllib.parse import urlparse

from audit_package_integrity import DIGEST_RE, plugin_identity
from verify_candidate_integrity import index_plugins, source_index

SHA = re.compile(r"^[a-f0-9]{40}$", re.I)


def _digest(items):
    return hashlib.sha256(
        json.dumps(items, ensure_ascii=False, sort_keys=True,
                   separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def _url_parts(url):
    p = urlparse(str(url or ""))
    if p.scheme != "https" or p.netloc != "raw.githubusercontent.com" or p.query or p.fragment:
        return None
    parts = p.path.strip("/").split("/")
    if len(parts) < 4 or not all(parts):
        return None
    if len(parts) >= 6 and parts[2:4] == ["refs", "heads"]:
        path = parts[5:]
    else:
        path = parts[3:]
    return (parts[0], parts[1], path) if path and path[-1].lower().endswith(".cs3") else None


def expected_immutable_url(original, sha):
    parsed = _url_parts(original)
    if not parsed or not SHA.fullmatch(str(sha)):
        return None
    owner, repo, relative = parsed
    return f"https://raw.githubusercontent.com/{owner}/{repo}/{sha.lower()}/" + "/".join(relative)


def validated_recovery_locks(index, previous, previous_provenance):
    """Reject stale, forged or nonmatching recovery inventory wholesale."""
    if index is None:
        return {}
    if not isinstance(index, dict):
        raise ValueError("Invalid immutable recovery inventory")
    old = index_plugins(previous)
    old_sources = source_index(previous_provenance)
    origin = {}
    for row in previous_provenance:
        if not isinstance(row, dict):
            raise ValueError("Invalid provenance for recovery")
        key = str(row.get("plugin") or row.get("originalName") or "").strip().casefold()
        if not key or key in origin:
            raise ValueError("Duplicate old recovery provenance")
        origin[key] = row
    if set(old) != set(origin):
        raise ValueError("Recovery source identity coverage does not reconcile")
    if (
        index.get("mode") != "READ_ONLY_PINNED_RECOVERY_EVIDENCE"
        or index.get("autoPublicationAuthorized") is not False
        or index.get("publishedCount") != len(previous)
        or index.get("sourceCatalogDigest") != _digest(previous)
        or index.get("sourceProvenanceDigest") != _digest(previous_provenance)
        or not isinstance(index.get("locks"), list)
        or len(index["locks"]) != index.get("immutableMatches")
    ):
        raise ValueError("Immutable recovery index is stale, malformed or untrusted")
    locks = {}
    for entry in index["locks"]:
        if not isinstance(entry, dict):
            raise ValueError("Invalid lock entry")
        key = str(entry.get("plugin") or "").strip().casefold()
        if not key or key in locks or key not in old:
            raise ValueError("Recovery entry missing or repeated plugin identity")
        plugin, provenance = old[key], origin[key]
        expected_hash = plugin.get("fileHash")
        size = plugin.get("fileSize")
        original = plugin.get("url")
        sha = entry.get("commitSha")
        url = expected_immutable_url(original, sha)
        if (
            not url
            or entry.get("pinnedUrl") != url
            or entry.get("originalUrl") != original
            or entry.get("fileHash") != expected_hash
            or not isinstance(expected_hash, str)
            or DIGEST_RE.fullmatch(expected_hash) is None
            or type(size) is not int or size <= 0
            or entry.get("fileSize") != size
            or entry.get("version") != plugin.get("version")
            or entry.get("sourceId") != old_sources.get(key)
            or entry.get("originalSourceIndex") != provenance.get("sourceIndex")
            or entry.get("originalSourceRepository") != provenance.get("sourceRepository")
            or entry.get("trustBasis") != "matches_previously_published_sha256_and_size"
            or entry.get("releaseAuthorVerified") is not False
            or provenance.get("packageUrl") != original
        ):
            raise ValueError("Immutable recovery lock does not match previous publication: " + key)
        locks[key] = entry
    return locks


def restore_previous_from_lock(key, previous_plugin, previous_provenance,
                               locks, checker):
    """Return verified previous pinned entry/provenance or (None, None, reason)."""
    lock = locks.get(key)
    if not lock or not previous_plugin or not previous_provenance:
        return None, None, "no verified immutable lock for previous publication"
    pinned = copy.deepcopy(previous_plugin)
    origin = copy.deepcopy(previous_provenance)
    pinned["url"] = lock["pinnedUrl"]
    origin["packageUrl"] = lock["pinnedUrl"]
    try:
        result = checker(pinned)
    except Exception as error:
        return None, None, "pinned binary download failed: " + type(error).__name__
    if not isinstance(result, dict) or (
        result.get("status") != "hash_verified"
        or result.get("actualFileSize") != previous_plugin["fileSize"]
        or str(result.get("actualFileHash") or "").lower()
        != str(previous_plugin["fileHash"]).lower()
    ):
        return None, None, "pinned binary no longer matches published hash and size"
    if plugin_identity(pinned) != key:
        return None, None, "pinned fallback changed plugin identity"
    return pinned, origin, "byte-identical previous release reverified at immutable commit"
