#!/usr/bin/env python3
"""Promote only existing, source-bound upstream upgrades under a standing owner policy.

This is a staging transform, not a publisher or blanket checksum approval.
It checks the registered publisher's pinned GitHub index and separately downloads
both the mutable candidate and the commit-pinned binary. Only versions strictly
newer than a previously published plugin qualify; new plugins, source transfers,
same-version binary replacements and status changes remain on the manual gate.
The publisher itself remains a trust boundary: these checks cannot prove a
publisher has not been compromised or that the extension works at runtime.
"""
import argparse
import copy
import json
import os
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

from audit_package_integrity import DIGEST_RE, plugin_identity, verify_package
from index_immutable_recovery import (
    github_resolve_ref, parse_package_url, pinned_url,
)
from audit_package_integrity import fetch_json
from verify_candidate_integrity import index_plugins, source_index

COMMIT = re.compile(r"^[a-f0-9]{40}$", re.I)
INDEX = re.compile(
    r"^https://raw\.githubusercontent\.com/([A-Za-z0-9_.-]+)/"
    r"([A-Za-z0-9_.-]+)/(?:refs/heads/)?builds/plugins\.json$", re.I
)
REPO = re.compile(
    r"^https://github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$", re.I
)
POLICY_KIND = "trusted_upstream_policy_v1"


def _source_rules(sources, policy):
    if (type(policy) is not dict or policy.get("schemaVersion") != 1
            or not isinstance(policy.get("rules"), list)):
        raise ValueError("Invalid trusted publisher policy schema")
    definitions = sources.get("sources") if isinstance(sources, dict) else None
    if not isinstance(definitions, list):
        raise ValueError("Source registry is not a JSON source list")
    by_id = {s.get("id"): s for s in definitions}
    if len(by_id) != len(definitions):
        raise ValueError("Duplicated source ID in registry")
    rules = {}
    for rule in policy["rules"]:
        if not isinstance(rule, dict):
            raise ValueError("Malformed standing trust rule")
        source_id = rule.get("sourceId")
        if (not isinstance(source_id, str) or source_id in rules
                or source_id not in by_id or rule.get("mode") != "existing_upgrade_only"):
            raise ValueError("Unrecognized, duplicated or overbroad trust rule")
        source = by_id[source_id]
        repo = REPO.fullmatch(str(source.get("repo") or ""))
        index = INDEX.fullmatch(str(source.get("index") or ""))
        authorized = REPO.fullmatch(str(rule.get("repository") or ""))
        if (not repo or not index or not authorized
                or tuple(x.lower() for x in repo.groups())
                != tuple(x.lower() for x in index.groups())
                or tuple(x.lower() for x in repo.groups())
                != tuple(x.lower() for x in authorized.groups())):
            raise ValueError("Trusted publisher policy does not match source registry")
        rules[source_id] = (source, repo.groups())
    return rules


def _source_map(provenance):
    values = {}
    for row in provenance:
        key = str(row.get("plugin") or row.get("originalName") or "").strip().casefold()
        if not key or key in values:
            raise ValueError("Missing or duplicated provenance identity")
        values[key] = row
    return values


def _verified(result, item):
    return (isinstance(result, dict)
            and result.get("status") == "hash_verified"
            and result.get("actualFileSize") == item["fileSize"]
            and str(result.get("actualFileHash") or "").lower()
            == item["fileHash"].lower())


def promote(candidate, previous, candidate_provenance, previous_provenance,
            sources, policy, *, resolve=github_resolve_ref,
            fetch_index=fetch_json, checker=verify_package, token=None):
    """Return (candidate, provenance, policy attestations, summary); no input mutation."""
    rules = _source_rules(sources, policy)
    current, earlier = index_plugins(candidate), index_plugins(previous)
    current_src, previous_src = _source_map(candidate_provenance), _source_map(previous_provenance)
    if set(current) != set(current_src) or set(earlier) != set(previous_src):
        raise ValueError("Plugin/provenance coverage does not reconcile")
    pinned_catalog = copy.deepcopy(candidate)
    pinned_provenance = copy.deepcopy(candidate_provenance)
    located = {plugin_identity(row): i for i, row in enumerate(pinned_catalog)}
    located_source = {
        str(row.get("plugin") or row.get("originalName") or "").strip().casefold(): i
        for i, row in enumerate(pinned_provenance)
    }
    cache = {}
    approvals, skipped = [], []
    for key, item in current.items():
        source = current_src[key]
        source_id = str(source.get("sourceId") or "")
        if source_id not in rules:
            continue
        prior = earlier.get(key)
        older_source = previous_src.get(key, {})
        repo_definition, pair = rules[source_id]
        if (prior is None or older_source.get("sourceId") != source_id
                or older_source.get("sourceRepository", "").lower()
                   != repo_definition["repo"].lower()
                or source.get("sourceRepository", "").lower()
                   != repo_definition["repo"].lower()
                or source.get("sourceIndex") != repo_definition["index"]
                or str(source.get("packageUrl") or "").replace(" ", "%20")
                   != str(item.get("url") or "").replace(" ", "%20")):
            # New plugin or source migration: deliberately no automatic approval.
            continue
        fields = ("version", "fileHash", "fileSize", "status", "url")
        if all(prior.get(f) == item.get(f) for f in fields):
            continue
        # New versions only: same-version rebuilds require reviewed approval.
        if (type(item.get("version")) is not int or type(prior.get("version")) is not int
                or item["version"] <= prior["version"]
                or item.get("status") != prior.get("status")
                or type(item.get("fileSize")) is not int or item["fileSize"] <= 0
                or not isinstance(item.get("fileHash"), str)
                or DIGEST_RE.fullmatch(item["fileHash"]) is None):
            skipped.append({"plugin": key, "reason": "not a strictly newer, SHA-256-declared release"})
            continue
        info = parse_package_url(item.get("url"))
        if (not info or info["ref"] != "builds"
                or (info["owner"].lower(), info["repo"].lower())
                   != tuple(x.lower() for x in pair)):
            skipped.append({"plugin": key, "reason": "package URL is outside registered publisher builds branch"})
            continue
        try:
            if source_id not in cache:
                revision = resolve(pair[0], pair[1], "builds", token)
                if not isinstance(revision, str) or COMMIT.fullmatch(revision) is None:
                    raise ValueError("publisher revision is not an immutable Git commit")
                pinned_index_url = (
                    f"https://raw.githubusercontent.com/{pair[0]}/{pair[1]}/"
                    f"{revision.lower()}/plugins.json"
                )
                index = fetch_index(pinned_index_url)
                if not isinstance(index, list):
                    raise ValueError("pinned publisher index is not a plugin array")
                lookup = index_plugins(index)
                cache[source_id] = (revision.lower(), pinned_index_url, lookup)
            revision, pinned_index_url, lookup = cache[source_id]
            original = lookup.get(key)
            if not isinstance(original, dict):
                raise ValueError("plugin missing in commit-pinned publisher index")
            norm = lambda value: str(value or "").replace(" ", "%20")
            if (norm(original.get("url")) != norm(item.get("url"))
                    or any(original.get(field) != item.get(field)
                           for field in ("version", "fileSize", "status"))
                    or str(original.get("fileHash") or "").lower()
                       != item["fileHash"].lower()):
                raise ValueError("publisher pinned index disagrees with candidate metadata")
            pinned = copy.deepcopy(item)
            pinned["url"] = pinned_url(info, revision)
            if not _verified(checker(item), item):
                raise ValueError("mutable source binary failed independent SHA-256 verification")
            if not _verified(checker(pinned), pinned):
                raise ValueError("commit-pinned binary failed separate SHA-256 verification")
            pinned_origin = copy.deepcopy(source)
            pinned_origin["packageUrl"] = pinned["url"]
            pinned_catalog[located[key]] = pinned
            pinned_provenance[located_source[key]] = pinned_origin
            approvals.append({
                "authorizationKind": POLICY_KIND, "plugin": key,
                "sourceId": source_id, "sourceRepository": repo_definition["repo"],
                "originalUrl": item["url"], "url": pinned["url"],
                "version": item["version"], "fileSize": item["fileSize"],
                "fileHash": item["fileHash"], "immutableCommit": revision,
                "pinnedIndexUrl": pinned_index_url,
                "pinnedIndexVerified": True, "separateBytesVerified": True,
            })
        except Exception as exc:
            skipped.append({"plugin": key, "reason": f"{type(exc).__name__}: {exc}"})
    if len(approvals) != len({p["plugin"] for p in approvals}):
        raise ValueError("Duplicated trusted update approvals")
    return pinned_catalog, pinned_provenance, approvals, {
        "mode": "TRUSTED_EXISTING_UPSTREAM_UPGRADES_ONLY",
        "policySources": sorted(rules),
        "promotedCount": len(approvals),
        "promoted": [x["plugin"] for x in approvals],
        "skipped": skipped,
        "manualReviewStillRequiredForUnprovenChanges": True,
        "publisherBuildAuthenticityGuaranteed": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("candidate-dir", "previous-dir", "sources", "policy", "output-dir"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    source, previous, destination = (
        args.candidate_dir.resolve(), args.previous_dir.resolve(), args.output_dir.resolve()
    )
    if destination.exists() or destination in (source, previous) or any(
        destination.is_relative_to(q) for q in (source, previous)
    ):
        parser.error("Staging output must be a new independent directory")
    read = lambda p: json.loads(p.read_text(encoding="utf-8"))
    catalog, provenance, approvals, report = promote(
        read(source / "plugins.json"), read(previous / "plugins.json"),
        read(source / "provenance.json"), read(previous / "provenance.json"),
        read(args.sources), read(args.policy), token=os.environ.get("GH_TOKEN"),
    )
    shutil.copytree(source, destination)
    for name, value in (
        ("plugins.json", catalog), ("provenance.json", provenance),
        ("policy-approvals.json", approvals), ("trusted-update-report.json", report)
    ):
        (destination / name).write_text(
            json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
