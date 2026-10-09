#!/usr/bin/env python3
"""Construct a release-eligible, provenance-consistent guarded catalog candidate.

NO GIT WRITES are performed. This must run only after full published-byte
verification, selection, and read-only reconciliation. A guarded candidate:
* applies safe, previously trusted changes from the per-plugin gate;
* retains every existing plugin identity (no removal);
* may keep the exact previous metadata for a blocked update and flags it as
  UNVERIFIED if its mutable URL does not prove preservation of old bytes;
* never auto-approves a different hash, unsigned change, or new provider.
The release is blocked if exceptions exceed the bounded preservation policy.
"""
import argparse
import copy
import json
from pathlib import Path

import merge_upstreams as template
from plan_per_plugin_selection import canonical_sha256
from reconcile_integrity_review import reconcile, write_bundle


EXPECTED_FILES = (
    "plugins.json", "provenance.json", "repo.json", "merge-report.json",
    "release-diff.json", "RELEASE_NOTES.md", "STATUS.md", "README.md",
)


def _plain(value):
    return str(value).replace("\n", " ").replace("|", r"\|")


def make_guarded_candidate(candidate, previous, candidate_provenance,
                           previous_provenance, preview, preview_provenance,
                           selection, verification,
                           candidate_report, previous_report,
                           candidate_repo, previous_repo,
                           *, max_deferred=24, deferred_url_checker=None):
    if not isinstance(max_deferred, int) or not (0 <= max_deferred <= 100):
        raise ValueError("Deferred exception ceiling must be 0 to 100")
    bundle = reconcile(
        candidate, previous, candidate_provenance, previous_provenance,
        preview, preview_provenance, selection, verification,
        candidate_report, previous_report, candidate_repo, previous_repo,
        mode="compatibility"
    )
    assurance = bundle["reconciliation.json"]
    report = copy.deepcopy(bundle["merge-report.json"])
    plugins = copy.deepcopy(bundle["plugins.json"])
    provenance = copy.deepcopy(bundle["provenance.json"])
    original = {str(p.get("internalName") or p.get("name") or "").casefold(): p
                for p in previous}
    final = {str(p.get("internalName") or p.get("name") or "").casefold(): p
             for p in plugins}
    if set(original) - set(final):
        raise ValueError("Guarded release cannot remove a previously published plugin")
    if len(plugins) < len(previous) or len(plugins) < 50:
        raise ValueError("Guarded release reduces previous catalog availability")
    if (
        report["changes"]["removed"] != 0
        or report["sourceHealth"]["failed"] != 0
        or not assurance["changesReconciled"]
        or report["previousPlugins"] != len(previous)
        or report["integrityHealth"]["candidateChecked"] != len(candidate)
        or report["integrityHealth"]["changedOrFailedCandidates"]
             != verification.get("blockedCount")
    ):
        raise ValueError("Incomplete upstream/source/integrity safety evidence")

    unknown = report["integrityHealth"]["unverifiedPreviousCarried"]
    if unknown > max_deferred:
        raise ValueError(
            f"Deferred existing binaries exceed safety ceiling: {unknown}>{max_deferred}"
        )
    if report["changes"]["updated"] > max(25, len(previous) // 5):
        raise ValueError("Unexpectedly large metadata upgrade requires independent review")
    deferred_keys = {
        str(item["plugin"]).casefold() for item in report["deferredUnverified"]
    }
    if len(deferred_keys) != unknown:
        raise ValueError("Deferred identity count does not reconcile")
    for key in deferred_keys:
        if key not in original or key not in final or original[key] != final[key]:
            raise ValueError("Deferred candidate modified prior published metadata")
    if deferred_url_checker is not None:
        for item in report["deferredUnverified"]:
            key = str(item["plugin"]).casefold()
            url = final[key].get("url")
            result = deferred_url_checker(url)
            if not isinstance(result, dict) or result.get("ok") is not True:
                raise ValueError("Previously published deferred URL is no longer reachable: " + key)
    if candidate_repo != previous_repo or bundle["repo.json"] != previous_repo:
        raise ValueError("CloudStream repo manifest must not change implicitly")

    report["candidateStatus"] = (
        "READY - GUARDED, " + str(unknown) + " UPSTREAM UPDATES DEFERRED FOR REVIEW"
        if unknown else "READY - GUARDED, NO DEFERRED UPDATES"
    )
    report["releaseEligible"] = True
    report["publicationMethod"] = "guarded_per_plugin_compatibility"
    report["candidateStatusDisclaimer"] = (
        "A deferred mutable URL does NOT guarantee the old bytes remain available. "
        "Unverified metadata is held from the previous release only. "
        "Publication never approves a different binary on this basis."
    )
    report["packageHealth"] = copy.deepcopy(candidate_report["packageHealth"])
    report["failedPlugins"] = copy.deepcopy(candidate_report.get("failedPlugins", []))
    report["duplicates"] = copy.deepcopy(candidate_report.get("duplicates", []))
    report["sourceStatus"] = copy.deepcopy(report["sourceStatus"])
    for source in report["sourceStatus"]:
        source.setdefault("includedCount", 0)
        source.setdefault("rawCount", None)
        source.setdefault("duplicateSkipped", 0)
        source.setdefault("packageFailed", 0)
        source.setdefault("name", source.get("id") or "Unknown")
        source.setdefault("repo", "")
    report["integrityHealth"]["unverifiedPreviousCarried"] = unknown
    report["integrityHealth"]["releaseEligibleWithWarnings"] = unknown > 0
    original_src = {
        str(p.get("plugin") or p.get("originalName") or "").casefold(): p
        for p in previous_provenance
    }
    rows = []
    for plugin, source in zip(plugins, provenance):
        key = str(plugin.get("internalName") or plugin.get("name") or "").casefold()
        if key in deferred_keys:
            change = "Deferred: previous metadata, unverified upstream bytes"
        elif key not in original:
            change = "New (verified by candidate policy)"
        elif original[key] != plugin or original_src.get(key) != source:
            change = "Updated (verified by candidate policy)"
        else:
            change = "Unchanged"
        rows.append({
            "name": plugin.get("name") or plugin.get("internalName"),
            "originalName": source.get("originalName") or plugin.get("internalName"),
            "category": source.get("category") or "Other",
            "version": plugin.get("version"),
            "language": plugin.get("language") or "",
            "tvTypes": template.tv_types_text(plugin),
            "sourceId": source.get("sourceId"),
            "sourceName": source.get("sourceName") or source.get("sourceId"),
            "change": change,
        })
    status = template.build_status(report, rows)
    notes = template.build_release_notes(report)
    readme = template.build_readme(report, rows)
    section = [
        "## Integrity status — separate from package reachability", "",
        f"- **{verification['checked']} candidate packages** inspected for declared length and SHA-256 when present.",
        f"- **{report['integrityHealth']['candidateAccepted']} candidates** accepted under the published trust rules.",
        f"- **{unknown} existing plugin entries** retained with old metadata because newer upstream candidates were rejected.",
        f"- **{report['integrityHealth']['pinnedPreviousRecovered']} older package URLs** pinned to freshly reverified immutable, byte-identical releases.",
        f"- **{report['integrityHealth']['sizeOnlyLegacyCandidateAccepted']} unchanged legacy entries** have size-only checks without an authenticated SHA-256.",
        "",
        "Unverified deferred plugin URLs are still mutable upstream links. Keeping an "
        "old manifest entry does **not** freeze the bytes it serves, and does not "
        "approve a changed binary. Unresolved incidents remain open for upstream "
        "correction and independent release verification.", "",
    ]
    note = "\n".join(section)
    status = status.replace("## Source health", note + "## Source health", 1)
    notes += "\n\n" + note
    position = "## 🗂️ Browse by section"
    if position not in readme:
        raise ValueError("Expected existing MegaRepo README section missing")
    readme = readme.replace(position, note + position, 1)
    duplicate_intro = "## 🔁 Duplicate handling"
    if duplicate_intro not in readme:
        raise ValueError("Expected duplicate-handling section missing")
    readme = readme.replace(
        duplicate_intro,
        "Source duplicate decisions below describe initial upstream selection; "
        "the guarded integrity gate may defer that candidate or preserve a verified "
        "older binary.\n\n" + duplicate_intro, 1
    )
    release_diff = copy.deepcopy(bundle["release-diff.json"])
    release_diff["candidateStatus"] = report["candidateStatus"]
    release_diff["catalog"]["reachablePackages"] = report["packageHealth"]["reachable"]
    release_diff["catalog"]["packageFailures"] = report["packageHealth"]["failed"]
    release_diff["integrityHealth"] = copy.deepcopy(report["integrityHealth"])
    release_diff["releaseEligible"] = True
    if report["changes"] != release_diff["changes"]:
        raise ValueError("Plugin change diff does not reconcile")
    if len(plugins) != len(provenance) or len(plugins) != report["uniquePlugins"]:
        raise ValueError("Plugin/provenance/count inconsistency")
    if sum(row["includedCount"] for row in report["sourceStatus"]) != len(plugins):
        raise ValueError("Source included counts differ from chosen catalog")

    final_bundle = {
        "plugins.json": plugins, "provenance.json": provenance,
        "repo.json": copy.deepcopy(candidate_repo),
        "merge-report.json": report, "release-diff.json": release_diff,
        "README.md": readme, "STATUS.md": status, "RELEASE_NOTES.md": notes,
    }
    evidence = {
        "mode": "GUARDED_RELEASE_CANDIDATE_NOT_YET_PUBLISHED",
        "releaseEligible": True,
        "publishedCountBefore": len(previous),
        "selectedCount": len(plugins),
        "deferredUnverifiedCount": unknown,
        "blockedCandidateCount": verification["blockedCount"],
        "immutableRecoveredCount": report["integrityHealth"]["pinnedPreviousRecovered"],
        "originalPreviousCatalogDigest": canonical_sha256(previous),
        "publishedCandidateCatalogDigest": canonical_sha256(plugins),
        "sourcePreviousProvenanceDigest": canonical_sha256(previous_provenance),
        "selectedProvenanceDigest": canonical_sha256(provenance),
        "inputSelectionDigest": canonical_sha256(selection),
        "reportDigest": canonical_sha256(report),
        "releaseDiffDigest": canonical_sha256(release_diff),
        "files": list(EXPECTED_FILES),
        "warning": report["candidateStatusDisclaimer"],
        "actualPublicationPerformed": False,
    }
    return final_bundle, evidence


def _load(folder, filename, expected_type):
    payload = json.loads((Path(folder)/filename).read_text(encoding="utf-8"))
    if not isinstance(payload, expected_type):
        raise ValueError("Unexpected input JSON structure " + filename)
    return payload


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("candidate-dir","previous-dir","selection-dir","output-dir"):
        parser.add_argument("--"+name,required=True,type=Path)
    parser.add_argument("--max-deferred",type=int,default=24)
    parser.add_argument("--verify-deferred-urls",action="store_true",
                        help="Fail publication if any existing deferred package URL is unreachable")
    args=parser.parse_args(argv)
    folders=[args.candidate_dir.resolve(), args.previous_dir.resolve(),
             args.selection_dir.resolve()]
    dest=args.output_dir.resolve()
    if (dest.name.lower() in ("builds","merged","production","source")
            or any(dest==folder or dest.is_relative_to(folder) for folder in folders)):
        parser.error("Cannot write release candidate into production or source input")
    bundle,evidence=make_guarded_candidate(
        _load(args.candidate_dir,"plugins.json",list),
        _load(args.previous_dir,"plugins.json",list),
        _load(args.candidate_dir,"provenance.json",list),
        _load(args.previous_dir,"provenance.json",list),
        _load(args.selection_dir,"preview.plugins.json",list),
        _load(args.selection_dir,"preview.provenance.json",list),
        _load(args.selection_dir,"integrity-selection.json",dict),
        _load(args.selection_dir,"verification-report.json",dict),
        _load(args.candidate_dir,"merge-report.json",dict),
        _load(args.previous_dir,"merge-report.json",dict),
        _load(args.candidate_dir,"repo.json",dict),
        _load(args.previous_dir,"repo.json",dict),
        max_deferred=args.max_deferred,
        deferred_url_checker=(template.check_package_url
                              if args.verify_deferred_urls else None)
    )
    bundle["guarded-release-assurance.json"]=evidence
    write_bundle(dest,bundle)
    print(json.dumps({k:v for k,v in evidence.items() if k not in (
        "originalPreviousCatalogDigest","publishedCandidateCatalogDigest",
        "sourcePreviousProvenanceDigest","selectedProvenanceDigest",
        "inputSelectionDigest","reportDigest","releaseDiffDigest"
    )},indent=2))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
