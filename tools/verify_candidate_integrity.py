#!/usr/bin/env python3
"""Fail-closed package integrity gate for a candidate MegaRepo publication.

All candidate binaries are downloaded and checked against declared length/hash.
Existing unpinned entries may continue only while their critical identity and
metadata are unchanged; new or changed unpinned binaries require a reviewed,
SHA-256-pinned approval. A matching digest alone does not establish authorship.
"""
import argparse
import json
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from audit_package_integrity import DIGEST_RE, plugin_identity, verify_package

CRITICAL = ("url", "version", "fileHash", "fileSize", "status")


def read_list(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError(f"{path} must contain a JSON list")
    return data


def index_plugins(entries):
    result = {}
    for item in entries:
        if not isinstance(item, dict):
            raise ValueError("Plugin entries must be objects")
        key = plugin_identity(item)
        if not key or key in result:
            raise ValueError(f"Missing or duplicate plugin identity: {key}")
        result[key] = item
    return result


def source_index(entries):
    result = {}
    for row in entries:
        if not isinstance(row, dict):
            raise ValueError("Provenance rows must be objects")
        name = str(row.get("plugin") or row.get("originalName") or "").strip().casefold()
        if name:
            if name in result:
                raise ValueError(f"Duplicate provenance identity: {name}")
            result[name] = str(row.get("sourceId") or "")
    return result


def approved(plugin, source_id, actual, approvals):
    """Only exact reviewed approvals for a binary digest are valid."""
    actual_digest = str(actual.get("actualFileHash") or "").lower()
    size = actual.get("actualFileSize")
    if not DIGEST_RE.fullmatch(actual_digest):
        return False
    for row in approvals:
        if not isinstance(row, dict):
            continue
        try:
            match = (
                str(row.get("plugin") or "").strip().casefold() == plugin_identity(plugin)
                and str(row.get("sourceId") or "") == source_id
                and row.get("url") == plugin.get("url")
                and row.get("version") == plugin.get("version")
                and row.get("fileSize") == size
                and row.get("fileSize") == plugin.get("fileSize")
                and str(row.get("fileHash") or "").lower() == actual_digest
                and str(plugin.get("fileHash") or "").lower() == actual_digest
            )
        except (TypeError, ValueError):
            match = False
        if match:
            return True
    return False


def evaluate(old, new, old_source, new_source, approvals, check_result):
    """Assess a single candidate using observable result, never HEAD alone."""
    if not isinstance(new.get("fileSize"), int) or isinstance(new.get("fileSize"), bool) or new["fileSize"] <= 0:
        return "blocked", "no valid declared fileSize; package length is not verified"
    if check_result.get("status") not in ("hash_verified", "size_only_no_checksum"):
        return "blocked", f"download verification failed: {check_result.get('reason') or check_result.get('error') or check_result.get('status')}"
    changed = (
        old is None or old_source != new_source
        or any(old.get(field) != new.get(field) for field in CRITICAL)
    )
    if not changed:
        if check_result["status"] == "size_only_no_checksum":
            return "legacy_unpinned", "unchanged entry; size verified but no authenticated checksum"
        return "verified_unchanged", "matching declared size and checksum"
    old_hash = str((old or {}).get("fileHash") or "").lower()
    new_hash = str(new.get("fileHash") or "").lower()
    if (
        old_hash and new_hash == old_hash
        and old is not None and old_source == new_source
        and old.get("url") == new.get("url")
        and check_result.get("status") == "hash_verified"
    ):
        return "trusted_existing_digest", "binary matches previously published digest"
    if approved(new, new_source, check_result, approvals):
        return "review_approved_digest", "matches exact reviewed approval"
    return "blocked", "new or changed binary metadata has no previously trusted digest or exact reviewed approval"


def gate(candidate, previous, candidate_provenance, previous_provenance,
         approvals, checker=verify_package, workers=8):
    current = index_plugins(candidate)
    old = index_plugins(previous)
    cur_src = source_index(candidate_provenance)
    old_src = source_index(previous_provenance)
    if not current:
        raise ValueError("Empty candidate catalog")
    for key in current:
        if key not in cur_src or not cur_src[key]:
            raise ValueError(f"Missing candidate provenance: {key}")
    output = {
        "checked": len(current), "blocked": [], "legacyUnpinned": 0,
        "hashVerified": 0, "trustedChanges": 0, "reviewApproved": 0
    }
    checked = {}
    with ThreadPoolExecutor(max_workers=max(1, min(workers, 12))) as pool:
        future_map = {pool.submit(checker, plugin): key for key, plugin in current.items()}
        for future in as_completed(future_map):
            key = future_map[future]
            try:
                checked[key] = future.result()
            except Exception as error:
                checked[key] = {"status": "error", "error": f"{type(error).__name__}: {error}"}
    for key, entry in current.items():
        check = checked[key]
        classification, reason = evaluate(
            old.get(key), entry, old_src.get(key, ""), cur_src[key], approvals, check
        )
        if classification == "blocked":
            output["blocked"].append({
                "plugin": key, "sourceId": cur_src[key], "reason": reason,
                "url": entry.get("url"), "version": entry.get("version"),
                "expectedFileSize": entry.get("fileSize"),
                "actualFileSize": check.get("actualFileSize"),
                "actualFileHash": check.get("actualFileHash"),
                "verification": check.get("status"),
            })
        elif classification == "legacy_unpinned":
            output["legacyUnpinned"] += 1
        elif classification == "review_approved_digest":
            output["reviewApproved"] += 1
        elif classification == "trusted_existing_digest":
            output["trustedChanges"] += 1
        if check.get("status") == "hash_verified":
            output["hashVerified"] += 1
    output["passed"] = not output["blocked"]
    output["blockedCount"] = len(output["blocked"])
    return output


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("candidate", "previous", "candidate-provenance",
                 "previous-provenance", "approvals", "report"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        report = gate(
            read_list(args.candidate), read_list(args.previous),
            read_list(args.candidate_provenance),
            read_list(args.previous_provenance), read_list(args.approvals)
        )
    except Exception as exc:
        report = {"passed": False, "fatalError": f"{type(exc).__name__}: {exc}"}
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "blocked"}, indent=2))
    for row in report.get("blocked", [])[:30]:
        print("BLOCKED:", row["plugin"], row["reason"])
    return 0 if report.get("passed") else 1


if __name__ == "__main__":
    sys.exit(main())
