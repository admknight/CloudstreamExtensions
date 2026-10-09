#!/usr/bin/env python3
"""Read-only diagnostic of blocked MegaRepo candidates and reviewed overrides.

This utility does not approve binaries, modify manifests, or publish plugins.
Use actual gate evidence; an observed SHA-256 is not release authorization.
"""
import argparse
import json
import re
from datetime import date
from pathlib import Path
from urllib.parse import urlparse

HASH = re.compile(r"sha256-[0-9a-f]{64}\Z", re.IGNORECASE)


def _normalize(row):
    if not isinstance(row, dict):
        raise ValueError("Incident must be an object")
    plugin = str(row.get("plugin") or "").strip().casefold()
    source = str(row.get("sourceId") or row.get("candidateSourceId") or "").strip()
    url = row.get("candidateUrl") or row.get("url")
    version = row.get("candidateVersion") if "candidateVersion" in row else row.get("version")
    actual_size = row.get("actualFileSize") if "actualFileSize" in row else row.get("observedActualSize")
    actual_hash = row.get("observedActualSHA256") or row.get("actualFileHash")
    declared = row.get("declaredFileSize") if "declaredFileSize" in row else row.get("expectedFileSize")
    verification = row.get("verificationStatus") or row.get("verification") or "unknown"
    location = urlparse(url) if isinstance(url, str) else None
    if (not plugin or not source or not location or location.scheme != "https"
            or not location.hostname or location.username or location.password
            or not location.path.lower().endswith(".cs3") or
            type(version) is not int or version < 0):
        raise ValueError("Incomplete/invalid candidate identity, source, URL or version")
    if actual_size is not None and (type(actual_size) is not int or actual_size <= 0):
        raise ValueError("Invalid downloaded package size")
    if actual_hash is not None and (not isinstance(actual_hash, str)
                                    or HASH.fullmatch(actual_hash) is None):
        raise ValueError("Invalid observed SHA-256")
    return {"plugin": plugin, "sourceId": source, "candidateUrl": url,
            "candidateVersion": version, "declaredFileSize": declared,
            "actualFileSize": actual_size, "observedActualSHA256": actual_hash,
            "verificationStatus": str(verification)}


def _matching_review(incident, record):
    if not isinstance(record, dict):
        return False
    address = urlparse(str(record.get("evidenceUrl") or ""))
    if (address.scheme != "https" or not address.hostname or
            address.username or address.password):
        return False
    try:
        reviewed = date.fromisoformat(str(record.get("reviewedAt") or ""))
    except (TypeError, ValueError):
        return False
    digest = str(record.get("fileHash") or "")
    return bool(
        reviewed <= date.today() and record.get("reviewedBy") and record.get("reason")
        and str(record.get("plugin") or "").strip().casefold() == incident["plugin"]
        and record.get("sourceId") == incident["sourceId"]
        and record.get("url") == incident["candidateUrl"]
        and record.get("version") == incident["candidateVersion"]
        and type(record.get("fileSize")) is int
        and record["fileSize"] == incident["actualFileSize"]
        and record["fileSize"] == incident["declaredFileSize"]
        and HASH.fullmatch(digest)
        and digest.lower() == str(incident["observedActualSHA256"] or "").lower()
        and incident["verificationStatus"] == "hash_verified"
    )


def _selection_index(selection, keys):
    if selection is None:
        return {}
    if not isinstance(selection, dict) or not isinstance(selection.get("incidents"), list):
        raise ValueError("Selection must contain the gate's complete incident list")
    if selection.get("blockedCandidateCount") != len(keys):
        raise ValueError("Selection and verifier blocked counts differ")
    index = {}
    for row in selection["incidents"]:
        if not isinstance(row, dict):
            raise ValueError("Malformed selection incident")
        if row.get("disposition") == "previous_absent_from_candidate":
            continue
        key = str(row.get("plugin") or "").strip().casefold()
        if not key or key in index:
            raise ValueError("Invalid/duplicate selection identity")
        index[key] = row
    if set(index) != keys:
        raise ValueError("Selection incidents differ from blocked candidates")
    return index


def analyze(incidents, approvals, selection=None):
    if not isinstance(incidents, list) or not isinstance(approvals, list):
        raise ValueError("Incidents and approvals must be JSON arrays")
    checked = [_normalize(item) for item in incidents]
    identities = [row["plugin"] for row in checked]
    if len(set(identities)) != len(checked):
        raise ValueError("Duplicate blocked plugin identity")
    selected = _selection_index(selection, set(identities))
    results = []
    for row in checked:
        key = row["plugin"]
        fall = selected.get(key)
        if fall is not None:
            if (fall.get("candidateSourceId") != row["sourceId"] or
                    fall.get("candidateUrl") != row["candidateUrl"] or
                    fall.get("observedActualSize") != row["actualFileSize"] or
                    fall.get("observedActualSHA256") != row["observedActualSHA256"]):
                raise ValueError("Selection evidence does not match blocked candidate: " + key)
        observed_size = row["actualFileSize"]
        declared = row["declaredFileSize"]
        if type(declared) is not int or declared <= 0 or (observed_size is not None and declared != observed_size):
            category = "metadata_correction_requires_release_review"
        elif row["verificationStatus"] == "hash_verified":
            category = "changed_binary_requires_review"
        elif row["verificationStatus"] == "size_only_no_checksum":
            category = "digest_and_release_review_required"
        else:
            category = "download_or_integrity_investigation_required"
        matches = sum(_matching_review(row, approval) for approval in approvals)
        results.append({
            "plugin": key, "sourceId": row["sourceId"],
            "category": category,
            "matchingReviewRecordCount": matches,
            "recordStatus": ("matching_record_requires_original_gate_recheck" if matches else "no_matching_review_record"),
            "selectionDisposition": fall.get("disposition") if fall else None,
            "safeToPublishCandidate": False,
        })
    return {
        "mode": "READ_ONLY_LOCAL_RECOVERY_READINESS",
        "releaseAuthorized": False,
        "blockedCandidateCount": len(results),
        "withoutMatchingReviewCount": sum(not r["matchingReviewRecordCount"] for r in results),
        "incidents": results,
        "notice": "Observed hashes and this diagnostic never authorize binaries. The original guarded gate must independently verify a reviewed release before publication.",
    }


def main():
    p = argparse.ArgumentParser(description=__doc__)
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--incidents", type=Path, help="Legacy list of normalized candidate incidents")
    g.add_argument("--verification", type=Path, help="Actual verification-report.json from the gate")
    p.add_argument("--selection", type=Path, help="Matching integrity-selection.json")
    p.add_argument("--approvals", type=Path, required=True)
    p.add_argument("--output", type=Path, help="Write advisory JSON separately from source inputs")
    args = p.parse_args()
    load = lambda path: json.loads(path.read_text(encoding="utf-8"))
    if args.verification:
        verification = load(args.verification)
        if (not isinstance(verification, dict) or not isinstance(verification.get("blocked"), list)
                or verification.get("blockedCount") != len(verification["blocked"])
                or type(verification.get("checked")) is not int or verification["checked"] < 50):
            p.error("Incomplete verifier evidence")
        incidents = verification["blocked"]
    else:
        incidents = load(args.incidents)
    result = analyze(incidents, load(args.approvals),
                     load(args.selection) if args.selection else None)
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output:
        protected = {a.resolve() for a in (args.incidents, args.verification, args.selection, args.approvals) if a}
        if args.output.resolve() in protected:
            p.error("Readiness output cannot overwrite an input")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")


if __name__ == "__main__":
    main()
