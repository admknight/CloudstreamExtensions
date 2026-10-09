#!/usr/bin/env python3
"""Dry-run, per-plugin MegaRepo integrity selection with explicit quarantine.

This is deliberately NOT a publisher. It never overwrites candidate inputs,
published builds, repository metadata, or issue state. It generates a proposed
catalog and provenance together with a complete verification decision ledger.

A blocked candidate cannot displace a trusted old binary. The previous entry
is retained in the PREVIEW only when it has a full immutable commit URL, valid
declared size/hash, and is independently re-downloaded and hash-verified.
If no verified immutable fallback exists, an old entry is quarantined and
excluded from the preview; it is NOT claimed to be safely retained at a mutable
URL. This preview is never authorized for automatic publication.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

from audit_package_integrity import DIGEST_RE, plugin_identity, verify_package
from verify_candidate_integrity import gate, index_plugins, read_list, source_index

GIT_SHA = re.compile(r"^[a-fA-F0-9]{40}$")


def immutable_package_url(url):
    """Recognize only commit-addressed GitHub raw file URLs as fallback pins.

    GitLab revision strings and redirects are deliberately not treated as pins
    until separately verified. This is conservative, not a complete URL parser.
    """
    if not isinstance(url, str):
        return False
    parsed = urlparse(url)
    if (parsed.scheme != "https" or parsed.netloc != "raw.githubusercontent.com"
            or parsed.username or parsed.password or parsed.query or parsed.fragment):
        return False
    components = parsed.path.strip("/").split("/")
    return (
        len(components) >= 4
        and all(components[:2])
        and GIT_SHA.fullmatch(components[2]) is not None
        and all(components[3:])
        and components[-1].lower().endswith(".cs3")
    )


def verified_immutable_previous(previous_entry, previous_source, checker):
    """Return (verified, evidence). A checksum or filename alone is not enough."""
    if not previous_entry or not previous_source:
        return False, "no previous entry or provenance"
    url = previous_entry.get("url")
    if not immutable_package_url(url):
        return False, "previous package URL is not immutable and commit-pinned"
    expected_size = previous_entry.get("fileSize")
    expected_hash = previous_entry.get("fileHash")
    if (
        type(expected_size) is not int or expected_size <= 0
        or not isinstance(expected_hash, str)
        or DIGEST_RE.fullmatch(expected_hash) is None
    ):
        return False, "previous pinned package has no valid size and SHA-256"
    try:
        result = checker(previous_entry)
    except Exception as exc:
        return False, f"previous package verification exception: {type(exc).__name__}: {exc}"
    if not isinstance(result, dict):
        return False, "previous package verifier returned invalid data"
    if (
        result.get("status") != "hash_verified"
        or result.get("actualFileSize") != expected_size
        or str(result.get("actualFileHash") or "").lower() != expected_hash.lower()
    ):
        return False, "previous pinned package bytes do not match the declared SHA-256 and size"
    return True, "previous immutable URL independently verified by size and SHA-256"


def build_preview(candidate, previous, candidate_provenance,
                  previous_provenance, approvals,
                  checker=verify_package, workers=8):
    """Build a non-publishable preview; never mutate or write source objects."""
    if not isinstance(approvals, list):
        raise ValueError("Approvals registry must be a JSON array")
    new = index_plugins(candidate)
    old = index_plugins(previous)
    new_sources = source_index(candidate_provenance)
    old_sources = source_index(previous_provenance)
    new_provenance = {
        plugin_identity({"internalName": row.get("plugin") or row.get("originalName")}): row
        for row in candidate_provenance if isinstance(row, dict)
    }
    old_provenance = {
        plugin_identity({"internalName": row.get("plugin") or row.get("originalName")}): row
        for row in previous_provenance if isinstance(row, dict)
    }
    if len(new_provenance) != len(new_sources) or len(old_provenance) != len(old_sources):
        raise ValueError("Duplicate or ambiguous source provenance")
    for key in new:
        if key not in new_provenance or not new_sources.get(key):
            raise ValueError("Missing source provenance for candidate " + key)

    verification = gate(candidate, previous, candidate_provenance,
                        previous_provenance, approvals,
                        checker=checker, workers=workers)
    if verification.get("fatalError") or verification.get("checked") != len(new):
        raise ValueError("Integrity gate did not verify the entire candidate")
    denied = {row["plugin"]: row for row in verification.get("blocked", [])}
    if len(denied) != verification.get("blockedCount") or not set(denied).issubset(new):
        raise ValueError("Incomplete or mismatched candidate verification result")

    proposal = []
    proposal_provenance = []
    records = []
    counts = {
        "acceptedCandidates": 0,
        "retainedImmutablePrevious": 0,
        "quarantinedExisting": 0,
        "withheldNew": 0,
        "previousRemovedByCandidate": 0,
        "legacySizeOnlyCandidates": verification.get("legacyUnpinned", 0),
        "declaredHashMatchedCandidates": verification.get("hashVerified", 0),
    }
    for key, entry in new.items():
        failure = denied.get(key)
        if not failure:
            proposal.append(entry)
            proposal_provenance.append(new_provenance[key])
            counts["acceptedCandidates"] += 1
            continue
        earlier = old.get(key)
        held, explanation = verified_immutable_previous(
            earlier, old_sources.get(key), checker
        )
        if held:
            if key not in old_provenance:
                raise ValueError("Verified previous binary lacks provenance: " + key)
            proposal.append(earlier)
            proposal_provenance.append(old_provenance[key])
            counts["retainedImmutablePrevious"] += 1
            disposition = "retained_immutable_previous"
        elif earlier is not None:
            counts["quarantinedExisting"] += 1
            disposition = "quarantined_existing"
        else:
            counts["withheldNew"] += 1
            disposition = "withheld_new"
        records.append({
            "plugin": key,
            "disposition": disposition,
            "candidateSourceId": new_sources[key],
            "previousSourceId": old_sources.get(key),
            "candidateUrl": entry.get("url"),
            "previousUrl": earlier.get("url") if earlier else None,
            "candidateReason": failure.get("reason"),
            "observedVerification": failure.get("verification"),
            "observedActualSize": failure.get("actualFileSize"),
            "observedActualSHA256": failure.get("actualFileHash"),
            "fallbackAssessment": explanation,
        })
    # A disappearance from the candidate is not an integrity-approved removal.
    # It appears only in the preview's exception ledger.
    for key, earlier in old.items():
        if key not in new:
            counts["previousRemovedByCandidate"] += 1
            records.append({
                "plugin": key,
                "disposition": "previous_absent_from_candidate",
                "previousSourceId": old_sources.get(key),
                "previousUrl": earlier.get("url"),
                "fallbackAssessment": "candidate removal requires explicit review",
            })

    check_index = index_plugins(proposal)
    plan_src = source_index(proposal_provenance)
    if len(proposal) != len(proposal_provenance) or set(check_index) != set(plan_src):
        raise ValueError("Proposed plugins and provenance did not reconcile")
    if any(not plan_src[k] for k in check_index):
        raise ValueError("Proposed catalog has empty source provenance")
    if len(proposal) < 50:
        raise ValueError("Preview has fewer than 50 plugins: release safety threshold")

    quarantines = counts["quarantinedExisting"] + counts["withheldNew"]
    requires_review = bool(
        quarantines or counts["previousRemovedByCandidate"]
        or counts["retainedImmutablePrevious"]
    )
    summary = {
        "inputDigests": {
            "candidate": canonical_sha256(candidate),
            "previous": canonical_sha256(previous),
            "candidateProvenance": canonical_sha256(candidate_provenance),
            "previousProvenance": canonical_sha256(previous_provenance),
        },
        "outputDigests": {
            "previewPlugins": canonical_sha256(proposal),
            "previewProvenance": canonical_sha256(proposal_provenance),
        },
        "mode": "PREVIEW_ONLY_NO_PUBLICATION",
        "candidateCount": len(candidate),
        "previousCount": len(previous),
        "previewCount": len(proposal),
        "previewProvenanceCount": len(proposal_provenance),
        "selection": counts,
        "blockedCandidateCount": verification["blockedCount"],
        "incidentCount": len(records),
        "incidents": records,
        "requiresExplicitReleaseReview": requires_review,
        "automaticPublicationAuthorized": False,
        "importantLimitation": (
            "Quarantined existing entries are omitted from this PREVIEW. "
            "Production remains unchanged. Do not replace the published catalog "
            "without separate review of removals, release metadata, and sources. "
            "A legacy size-only pass is not authenticated publisher provenance."
        ),
    }
    if (counts["acceptedCandidates"] + counts["retainedImmutablePrevious"]
            + quarantines != len(candidate)):
        raise ValueError("Selection arithmetic does not reconcile")
    return proposal, proposal_provenance, summary, verification


def canonical_sha256(value):
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    ).hexdigest()


def _write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n",
                    encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("candidate", "previous", "candidate-provenance",
                 "previous-provenance", "approvals", "output-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--fail-on-quarantine", action="store_true")
    args = parser.parse_args(argv)
    inputs = [args.candidate, args.previous, args.candidate_provenance,
              args.previous_provenance, args.approvals]
    destination = args.output_dir.resolve()
    if any(destination == p.resolve().parent for p in inputs) or destination.name in ("builds", "merged"):
        parser.error("Output must be a separate preview directory, never an input or production directory")
    if args.workers < 1 or args.workers > 12:
        parser.error("workers must be between 1 and 12")
    proposal, provenance, summary, verification = build_preview(
        read_list(args.candidate), read_list(args.previous),
        read_list(args.candidate_provenance), read_list(args.previous_provenance),
        read_list(args.approvals), workers=args.workers
    )
    destination.mkdir(parents=True, exist_ok=True)
    _write_json(destination / "preview.plugins.json", proposal)
    _write_json(destination / "preview.provenance.json", provenance)
    _write_json(destination / "integrity-selection.json", summary)
    _write_json(destination / "verification-report.json", verification)
    print(json.dumps({k: v for k, v in summary.items() if k != "incidents"},
                     indent=2, sort_keys=True))
    for incident in summary["incidents"][:25]:
        print("REVIEW:", incident["plugin"], incident["disposition"],
              incident.get("candidateReason", ""))
    if args.fail_on_quarantine and summary["requiresExplicitReleaseReview"]:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
