#!/usr/bin/env python3
"""Read-only, source-independent repair planning for already published packages.

Never accept a different hash, add/remove identities, or authorize publication.
Independently re-download original URLs and proposed immutable replacements.
"""
import argparse
import copy
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from audit_package_integrity import DIGEST_RE, plugin_identity, verify_package
from plan_per_plugin_selection import canonical_sha256
from verified_immutable_fallback import (
    validated_recovery_locks, restore_previous_from_lock,
)


def exact_verified(entry, result):
    digest, size = entry.get("fileHash"), entry.get("fileSize")
    return (isinstance(digest, str) and DIGEST_RE.fullmatch(digest) is not None
            and type(size) is int and size > 0 and isinstance(result, dict)
            and result.get("status") == "hash_verified"
            and type(result.get("actualFileSize")) is int
            and result["actualFileSize"] == size
            and str(result.get("actualFileHash") or "").lower() == digest.lower())


def safe_check(checker, entry):
    try:
        result = checker(copy.deepcopy(entry))
        return result if isinstance(result, dict) else {"status": "error"}
    except Exception as exc:
        return {"status": "error", "error": type(exc).__name__}


def plan_repairs(plugins, provenance, inventory, *, checker=verify_package, workers=6):
    if type(workers) is not int or not 1 <= workers <= 8:
        raise ValueError("workers must be between 1 and 8")
    if not isinstance(plugins, list) or not plugins or not isinstance(provenance, list):
        raise ValueError("Missing published snapshot")
    locks = validated_recovery_locks(inventory, plugins, provenance)
    origins = {str(x.get("plugin") or x.get("originalName") or "").strip().casefold(): x
               for x in provenance}

    def one(entry):
        key = plugin_identity(entry)
        origin = origins[key]
        original = safe_check(checker, entry)
        old_ok = exact_verified(entry, original)
        provenance_ok = origin.get("packageUrl") == entry.get("url")
        row = {"plugin": key, "sourceId": origin.get("sourceId"),
               "version": entry.get("version"), "fileSize": entry.get("fileSize"),
               "fileHash": entry.get("fileHash"), "originalUrl": entry.get("url"),
               "originalCheck": original, "originalBytesVerified": old_ok,
               "provenanceUrlMatches": provenance_ok, "proposedUrl": entry.get("url"),
               "proposedBytesVerified": old_ok, "status": "unresolved"}
        if not provenance_ok:
            row["reason"] = "published provenance URL mismatch; no automatic edit"
            return row
        lock = locks.get(key)
        if lock and lock["pinnedUrl"] != entry.get("url"):
            pinned, new_origin, reason = restore_previous_from_lock(
                key, entry, origin, locks, lambda p: safe_check(checker, p))
            if pinned is not None:
                expected_entry = dict(entry, url=lock["pinnedUrl"])
                expected_origin = dict(origin, packageUrl=lock["pinnedUrl"])
                if pinned != expected_entry or new_origin != expected_origin:
                    raise ValueError("Repair changed more than package URL: " + key)
                row.update(status="protect_verified" if old_ok else "repair_verified",
                           proposedUrl=pinned["url"], proposedBytesVerified=True,
                           commitSha=lock["commitSha"], reason=reason)
                return row
            row["pinFailure"] = reason
        if old_ok:
            row.update(status="unchanged_verified", reason="original bytes match")
        else:
            row["reason"] = "no independently reverified replacement"
        return row

    with ThreadPoolExecutor(max_workers=workers) as pool:
        rows = list(pool.map(one, plugins))
    counts = {name: sum(r["status"] == name for r in rows) for name in (
        "repair_verified", "protect_verified", "unchanged_verified", "unresolved")}
    if sum(counts.values()) != len(plugins) or len({r["plugin"] for r in rows}) != len(plugins):
        raise ValueError("Repair plan does not cover every published identity")
    original_matches = sum(r["originalBytesVerified"] for r in rows)
    proposed_matches = sum(r["proposedBytesVerified"] for r in rows)
    if proposed_matches != original_matches + counts["repair_verified"]:
        raise ValueError("Repair plan would lose a verified package")
    return {"mode": "READ_ONLY_PUBLISHED_URL_REPAIR_PLAN",
            "autoPublicationAuthorized": False, "actualPublicationPerformed": False,
            "sourceCatalogDigest": canonical_sha256(plugins),
            "sourceProvenanceDigest": canonical_sha256(provenance),
            "publishedCount": len(plugins), "originalHashMatches": original_matches,
            "proposedHashMatches": proposed_matches,
            "fullByteIntegrityPassed": proposed_matches == len(plugins),
            "allProvenanceUrlsMatch": all(r["provenanceUrlMatches"] for r in rows),
            "counts": counts, "rows": rows}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("published", "provenance", "inventory", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--workers", type=int, default=6)
    args = parser.parse_args(argv)
    if args.output.resolve() in {p.resolve() for p in
                                 (args.published, args.provenance, args.inventory)}:
        parser.error("Cannot overwrite any input snapshot")
    read = lambda p: json.loads(p.read_text(encoding="utf-8"))
    report = plan_repairs(read(args.published), read(args.provenance),
                          read(args.inventory), workers=args.workers)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "rows"}, indent=2))


if __name__ == "__main__":
    main()
