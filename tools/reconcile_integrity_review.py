#!/usr/bin/env python3
"""Reconcile a per-plugin integrity selection into coherent REVIEW-ONLY artifacts.

Two modes:
  quarantine: omit rejected existing entries from the preview only.
  compatibility: carry forward EXACT previous published records/provenance
                 for rejected/absent existing entries, marked UNVERIFIED.
Neither mode authorizes publication, changes real manifests, or changes issues.
Source manifests, reports and every generated display are derived from ONE
reconciled set. All input/output snapshot digests must match.
"""
import argparse
import copy
import json
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from audit_package_integrity import plugin_identity
from plan_per_plugin_selection import canonical_sha256


def _load(path, expected):
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(value, expected):
        raise ValueError(f"Unexpected JSON type in {path}")
    return value


def _identity_index(records, *, provenance=False):
    if not isinstance(records, list):
        raise ValueError("Expected JSON list")
    result = {}
    for row in records:
        if not isinstance(row, dict):
            raise ValueError("A catalog entry must be a JSON object")
        key = (
            str(row.get("plugin") or row.get("originalName") or "").strip().casefold()
            if provenance else plugin_identity(row)
        )
        if not key or key in result:
            raise ValueError("Missing/duplicate identity: " + str(key))
        result[key] = row
    return result


def _match_sets(plugins, provenance):
    a = _identity_index(plugins)
    b = _identity_index(provenance, provenance=True)
    if set(a) != set(b):
        raise ValueError("Catalog and provenance identities differ")
    if any(not str(row.get("sourceId") or "").strip() for row in b.values()):
        raise ValueError("Provenance with empty sourceId")
    return a, b


def validate_snapshots(candidate, previous, candidate_provenance,
                       previous_provenance, preview, preview_provenance,
                       selection, verification):
    """Bind report to exact JSON inputs; reject forged or stale selection files."""
    current, cur_src = _match_sets(candidate, candidate_provenance)
    old, old_src = _match_sets(previous, previous_provenance)
    chosen, chosen_src = _match_sets(preview, preview_provenance)

    expected_in = {
        "candidate": candidate,
        "previous": previous,
        "candidateProvenance": candidate_provenance,
        "previousProvenance": previous_provenance,
    }
    expected_out = {
        "previewPlugins": preview,
        "previewProvenance": preview_provenance,
    }
    for label, records in expected_in.items():
        if selection.get("inputDigests", {}).get(label) != canonical_sha256(records):
            raise ValueError("Selection does not bind original input: " + label)
    for label, records in expected_out.items():
        if selection.get("outputDigests", {}).get(label) != canonical_sha256(records):
            raise ValueError("Selection does not bind preview output: " + label)
    if (
        selection.get("mode") != "PREVIEW_ONLY_NO_PUBLICATION"
        or selection.get("automaticPublicationAuthorized") is not False
        or selection.get("candidateCount") != len(candidate)
        or selection.get("previousCount") != len(previous)
        or selection.get("previewCount") != len(preview)
        or selection.get("previewProvenanceCount") != len(preview_provenance)
        or verification.get("checked") != len(candidate)
        or verification.get("blockedCount") != selection.get("blockedCandidateCount")
        or not isinstance(selection.get("incidents"), list)
    ):
        raise ValueError("Malformed or incomplete integrity selection evidence")
    blocked = verification.get("blocked", [])
    if not isinstance(blocked, list) or len(blocked) != verification["blockedCount"]:
        raise ValueError("Blocked candidate ledger does not reconcile")
    blocked_by_key = _identity_index(
        [{"internalName": x.get("plugin")} for x in blocked]
    )
    if not set(blocked_by_key).issubset(current):
        raise ValueError("Blocked item missing from candidate")
    incident_by_key = {}
    for incident in selection["incidents"]:
        key = str(incident.get("plugin") or "").strip().casefold()
        if not key or key in incident_by_key:
            raise ValueError("Duplicate/missing integrity incident: " + key)
        incident_by_key[key] = incident
    for key, plugin in chosen.items():
        inc = incident_by_key.get(key)
        if inc and inc["disposition"] == "retained_immutable_previous":
            if key not in old or plugin != old[key] or chosen_src[key] != old_src[key]:
                raise ValueError("Previous immutable fallback does not match original: " + key)
        elif (key not in current or plugin != current[key]
              or chosen_src[key] != cur_src[key] or key in blocked_by_key):
            raise ValueError("Untrusted/modified candidate in selected preview: " + key)
    for key in current:
        incident = incident_by_key.get(key)
        if key in blocked_by_key:
            if incident is None or incident["disposition"] not in (
                "quarantined_existing", "withheld_new", "retained_immutable_previous"
            ):
                raise ValueError("Blocked plugin without matching disposition: " + key)
        elif key in incident_by_key or key not in chosen:
            raise ValueError("Unblocked plugin excluded or given exception: " + key)
    for key, incident in incident_by_key.items():
        if key not in current and (
            key not in old or incident["disposition"] != "previous_absent_from_candidate"
        ):
            raise ValueError("Invalid missing-candidate incident: " + key)
    counters = Counter(incident["disposition"] for incident in selection["incidents"])
    expected = {
        "retained_immutable_previous": "retainedImmutablePrevious",
        "quarantined_existing": "quarantinedExisting",
        "withheld_new": "withheldNew",
        "previous_absent_from_candidate": "previousRemovedByCandidate",
    }
    for disposition, field in expected.items():
        if counters[disposition] != selection.get("selection", {}).get(field):
            raise ValueError("Selection counts disagree for " + disposition)
    if set(old) - set(current) != {
        k for k, inc in incident_by_key.items()
        if inc["disposition"] == "previous_absent_from_candidate"
    }:
        raise ValueError("Missing old entry removal evidence")
    if any(x not in expected for x in counters):
        raise ValueError("Unexpected disposition in integrity report")
    if len(chosen) != (
        len(current) - counters["quarantined_existing"] - counters["withheld_new"]
    ):
        raise ValueError("Proposed catalog coverage does not reconcile")
    return current, old, cur_src, old_src, chosen, chosen_src, incident_by_key


def _md(value):
    return str(value or "").replace("|", r"\|").replace("\n", " ")


def _plugin_display(plugin, origin):
    return {
        "name": plugin.get("name") or plugin.get("internalName"),
        "originalName": origin.get("originalName") or plugin.get("internalName"),
        "category": origin.get("category") or "Other",
        "version": plugin.get("version"),
        "language": plugin.get("language") or "",
        "tvTypes": ", ".join(str(x) for x in plugin.get("tvTypes", [])),
        "sourceId": origin.get("sourceId"),
        "sourceName": origin.get("sourceName") or origin.get("sourceId"),
    }


def reconcile(candidate, previous, candidate_provenance, previous_provenance,
              preview, preview_provenance, selection, verification,
              candidate_report, previous_report, candidate_repo, previous_repo,
              *, mode="compatibility"):
    if mode not in ("compatibility", "quarantine"):
        raise ValueError("Unrecognized reconciliation mode")
    if not isinstance(candidate_report, dict) or not isinstance(previous_report, dict):
        raise ValueError("Missing candidate/previous reporting metadata")
    if not isinstance(candidate_repo, dict) or candidate_repo != previous_repo:
        raise ValueError("Repo manifest changed unexpectedly; review before reconciliation")
    if candidate_report.get("uniquePlugins") != len(candidate):
        raise ValueError("Source merger report doesn't match candidate")
    if candidate_report.get("sourceHealth", {}).get("failed") != 0:
        raise ValueError("Unhealthy source index; refuse derived publication plan")
    if candidate_report.get("candidateStatus") != "READY":
        raise ValueError("Candidate merger safety state is not READY")

    current, old, cur_src, old_src, chosen, chosen_src, incidents = validate_snapshots(
        candidate, previous, candidate_provenance, previous_provenance,
        preview, preview_provenance, selection, verification
    )
    final = {k: copy.deepcopy(v) for k, v in chosen.items()}
    final_src = {k: copy.deepcopy(v) for k, v in chosen_src.items()}
    deferred = []
    if mode == "compatibility":
        for key, incident in incidents.items():
            if incident["disposition"] in (
                "quarantined_existing", "previous_absent_from_candidate"
            ):
                if key not in old or key in final:
                    raise ValueError("Deferred entry missing old source or already selected")
                final[key] = copy.deepcopy(old[key])
                final_src[key] = copy.deepcopy(old_src[key])
                deferred.append({
                    "plugin": old[key].get("internalName") or old[key].get("name"),
                    "sourceId": old_src[key].get("sourceId"),
                    "reason": incident.get("candidateReason") or incident["disposition"],
                    "disposition": "carried_forward_unverified_mutable_or_missing_candidate",
                    "url": old[key].get("url"),
                    "warning": (
                        "Previous metadata preserved verbatim, NOT cryptographically "
                        "verified as an immutable available binary."
                    ),
                })
    # Canonical identity order: no changes to any source plugin object.
    ordered = sorted(final)
    plugins = [final[k] for k in ordered]
    provenance = [final_src[k] for k in ordered]
    _match_sets(plugins, provenance)
    if len(plugins) < 50:
        raise ValueError("Reconciled catalog below 50-entry safety floor")
    if len(plugins) < int(len(previous) * 0.80):
        raise ValueError("Reconciled catalog suffers excessive drop")
    if mode == "compatibility" and set(old) - set(final):
        raise ValueError("Compatibility mode silently removes previously published entries")

    original_src = _identity_index(previous_provenance, provenance=True)
    added, updated, unchanged, removed = [], [], [], []
    rows = []
    for key in ordered:
        entry, origin = final[key], final_src[key]
        display = _plugin_display(entry, origin)
        old_entry = old.get(key)
        if old_entry is None:
            added.append({
                "plugin": entry.get("internalName") or entry.get("name"),
                "name": origin.get("originalName"), "version": entry.get("version"),
                "sourceId": origin.get("sourceId"), "sourceName": origin.get("sourceName"),
                "category": origin.get("category"),
            })
            marker = "Added"
        elif old_entry != entry or original_src[key] != origin:
            previous_origin = original_src[key]
            updated.append({
                "plugin": entry.get("internalName") or entry.get("name"),
                "name": origin.get("originalName"), "version": entry.get("version"),
                "sourceId": origin.get("sourceId"), "sourceName": origin.get("sourceName"),
                "category": origin.get("category"),
                "fromVersion": old_entry.get("version"), "toVersion": entry.get("version"),
                "fromSourceId": previous_origin.get("sourceId"),
                "fromSource": previous_origin.get("sourceName"),
                "toSourceId": origin.get("sourceId"),
                "toSource": origin.get("sourceName"),
            })
            marker = "Updated"
        else:
            unchanged.append(key)
            marker = "Deferred (unverified)" if any(
                str(item["plugin"]).casefold() == key for item in deferred
            ) else "Unchanged"
        rows.append({**display, "change": marker})
    for key in sorted(set(old) - set(final)):
        source = original_src[key]
        removed.append({
            "plugin": old[key].get("internalName") or old[key].get("name"),
            "name": source.get("originalName"), "version": old[key].get("version"),
            "sourceId": source.get("sourceId"), "sourceName": source.get("sourceName"),
            "category": source.get("category"),
        })

    src_count = Counter(row["sourceId"] for row in final_src.values())
    status_by_id = {
        row["id"]: copy.deepcopy(row)
        for row in candidate_report.get("sourceStatus", [])
        if isinstance(row, dict) and row.get("id")
    }
    for source_id, count in src_count.items():
        if source_id not in status_by_id:
            related = next(row for row in final_src.values()
                           if row["sourceId"] == source_id)
            status_by_id[source_id] = {
                "id": source_id, "name": related.get("sourceName", source_id),
                "repo": related.get("sourceRepository"), "index": related.get("sourceIndex"),
                "ok": False, "rawCount": None,
                "error": "Former selected source absent in current merge; manual review",
            }
    issue_by_src = Counter(
        inc.get("candidateSourceId") or inc.get("previousSourceId") or ""
        for inc in selection["incidents"] if inc.get("plugin") in current
    )
    for source_id, status in status_by_id.items():
        status["includedCount"] = src_count.get(source_id, 0)
        status["integrityExceptions"] = issue_by_src.get(source_id, 0)
        status["deferredUnverified"] = sum(
            item["sourceId"] == source_id for item in deferred
        )
    source_status = list(status_by_id.values())
    sources_actual = {
        "ok": sum(bool(row.get("ok")) for row in candidate_report["sourceStatus"]),
        "failed": sum(not bool(row.get("ok")) for row in candidate_report["sourceStatus"]),
    }
    cat_count = Counter(row.get("category") or "Other" for row in provenance)
    change_count = {
        "added": len(added), "updated": len(updated),
        "unchanged": len(unchanged), "removed": len(removed),
    }
    change_details = {
        "added": added, "recovered": [],
        "updated": updated, "removed": removed,
    }
    previous_source_status = {
        row.get("id"): row for row in previous_report.get("sourceStatus", [])
        if isinstance(row, dict) and row.get("id")
    }
    source_changes = {
        "added": [
            {"id": x, "name": y.get("name"), "repo": y.get("repo")}
            for x, y in status_by_id.items() if x not in previous_source_status
        ],
        "removed": [
            {"id": x, "name": y.get("name"), "repo": y.get("repo")}
            for x, y in previous_source_status.items() if x not in status_by_id
        ],
        "healthChanged": [
            {"id": x, "name": y.get("name"),
             "fromOk": bool(previous_source_status[x].get("ok")), "toOk": bool(y.get("ok"))}
            for x, y in status_by_id.items() if x in previous_source_status
            and bool(previous_source_status[x].get("ok")) != bool(y.get("ok"))
        ],
    }
    custom_changes = [
        {**detail, "action": action}
        for action, values in (("added", added), ("updated", updated), ("removed", removed))
        for detail in values
        if detail.get("sourceId") == "adam-custom" or
           detail.get("fromSourceId") == "adam-custom"
    ]
    blocked = selection["blockedCandidateCount"]
    report = {
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
        "maintainer": candidate_report.get("maintainer"),
        "shortcode": candidate_report.get("shortcode"),
        "candidateStatus": "REVIEW ONLY - NOT APPROVED FOR PUBLICATION",
        "releaseAuthorized": False,
        "previewPolicy": mode,
        "previewOfCandidateStatus": candidate_report["candidateStatus"],
        "sourceHealth": sources_actual,
        "sourceStatus": source_status,
        "categoryCounts": dict(cat_count),
        "previousPlugins": len(previous),
        "uniquePlugins": len(plugins),
        "packageHealth": {
            "reachable": len(preview),
            "failed": blocked,
            "warning": (
                "Reached based on candidate binary verification. Carried-forward "
                "mutable old URLs have NOT been independently verified and "
                "are excluded from the reachable count."
            ),
        },
        "integrityHealth": {
            "candidateChecked": len(candidate),
            "candidateAccepted": selection["selection"]["acceptedCandidates"],
            "immutableOldRetained": selection["selection"]["retainedImmutablePrevious"],
            "unverifiedPreviousCarried": len(deferred),
            "quarantinedNotInProposal": len(removed),
            "changedOrFailedCandidates": blocked,
            "sizeOnlyLegacyCandidateAccepted": selection["selection"]["legacySizeOnlyCandidates"],
            "previewPolicy": mode,
        },
        "duplicates": [],
        "candidateDuplicateDecisions": candidate_report.get("duplicates", []),
        "failedPlugins": [
            {"plugin": item.get("plugin"), "sourceName": item.get("sourceId"),
             "sourceId": item.get("sourceId"), "version": old.get(
                 str(item.get("plugin") or "").casefold(), {}
             ).get("version"), "error": item["reason"]}
            for item in deferred
        ],
        "changes": change_count,
        "changeDetails": change_details,
        "sourceChanges": source_changes,
        "customProviderChanges": custom_changes,
        "removedPlugins": [x["plugin"] for x in removed],
        "deferredUnverified": deferred,
        "quarantineIncidents": selection["incidents"],
        "originalSourceReportDigest": canonical_sha256(candidate_report),
        "inputSnapshotDigests": copy.deepcopy(selection["inputDigests"]),
    }
    diff = {
        "generatedAt": report["generatedAt"],
        "candidateStatus": report["candidateStatus"],
        "catalog": {
            "plugins": len(plugins),
            "healthySources": sources_actual["ok"],
            "failedSources": sources_actual["failed"],
            "reachablePackages": len(preview),
            "packageFailures": blocked,
        },
        "changes": change_count,
        "changeDetails": change_details,
        "sourceChanges": source_changes,
        "customProviderChanges": custom_changes,
        "integrityHealth": report["integrityHealth"],
        "releaseAuthorized": False,
    }
    return {
        "plugins.json": plugins,
        "provenance.json": provenance,
        "repo.json": copy.deepcopy(candidate_repo),
        "merge-report.json": report,
        "release-diff.json": diff,
        "STATUS.md": build_status(report, rows, selection),
        "RELEASE_NOTES.md": build_release_notes(report),
        "README.md": build_readme(report, rows),
        "reconciliation.json": {
            "releaseAuthorized": False,
            "mode": mode,
            "expectedPluginCount": len(plugins),
            "outputDigests": {
                "plugins": canonical_sha256(plugins),
                "provenance": canonical_sha256(provenance),
                "mergeReport": canonical_sha256(report),
                "releaseDiff": canonical_sha256(diff),
            },
            "changesReconciled": sum(change_count.values()) == len(set(old) | set(final)),
            "issues": copy.deepcopy(selection["incidents"]),
        },
    }


def _table(rows):
    return "\n".join(
        "| " + " | ".join(_md(x) for x in row) + " |" for row in rows
    )


def build_status(report, rows, selection):
    health = report["integrityHealth"]
    lines = [
        "# Integrity selection review - NOT PUBLISHED", "",
        "**Preview only. Production and installer catalogs have not changed.**", "",
        "## Reconciled counts", "",
        _table([
            ["Candidates checked", "Selected entries in this preview",
             "Accepted candidates", "Unverified previous carried", "Blocked candidate updates"],
            ["---:", "---:", "---:", "---:", "---:"],
            [health["candidateChecked"], report["uniquePlugins"],
             health["candidateAccepted"], health["unverifiedPreviousCarried"],
             health["changedOrFailedCandidates"]],
        ]), "",
        f"- Preview policy: **{report['previewPolicy']}**",
        f"- Legacy candidate entries checked by size only (no authenticated SHA): "
        f"**{health['sizeOnlyLegacyCandidateAccepted']}**",
        f"- Source indexes reachable: **{report['sourceHealth']['ok']}**",
        f"- Source index errors: **{report['sourceHealth']['failed']}**",
        f"- Changes from previous metadata: **{report['changes']['added']} added**, "
        f"**{report['changes']['updated']} updated**, "
        f"**{report['changes']['unchanged']} unchanged**, "
        f"**{report['changes']['removed']} removed in preview**.", "",
        "## Deferred/quarantined plugin decisions", "",
    ]
    if report["quarantineIncidents"]:
        lines += [
            _table([["Plugin", "Disposition", "Reason"], ["---", "---", "---"]]),
        ]
        for item in report["quarantineIncidents"]:
            lines.append(_table([[
                item["plugin"], item["disposition"],
                item.get("candidateReason") or item.get("fallbackAssessment") or "",
            ]]))
    else:
        lines.append("No candidate exceptions.")
    lines += [
        "", "## All reconciled plugins (preview)", "",
        _table([["Plugin", "Version", "Source", "Change"], ["---", "---:", "---", "---"]])
    ]
    for item in rows:
        lines.append(_table([[
            item["name"], item["version"], item["sourceName"], item["change"],
        ]]))
    lines += [
        "", "> A previous mutable package URL may deliver different bytes from "
        "its published metadata. Carrying it forward in compatibility preview "
        "does not authenticate or preserve the package binary. These documents "
        "do not authorize publishing any removal or binary change.", "",
    ]
    return "\n".join(lines)


def build_release_notes(report):
    ch = report["changes"]
    h = report["integrityHealth"]
    lines = [
        "# MegaRepo integrity reconciliation - REVIEW DRAFT", "",
        "**Not production release notes. No plugin was installed or published.**", "",
        f"Draft plugin count: **{report['uniquePlugins']}**", "",
        f"- Added: {ch['added']}",
        f"- Updated: {ch['updated']}",
        f"- Unchanged (includes unverified old metadata): {ch['unchanged']}",
        f"- Removed in preview: {ch['removed']}",
        f"- Unverified previous entries carried forward: **{h['unverifiedPreviousCarried']}**",
        f"- Candidate checks blocked: **{h['changedOrFailedCandidates']}**",
        "", "## Updates that satisfy current candidate gate", "",
    ]
    for row in report["changeDetails"]["updated"][:200]:
        lines.append(f"- {_md(row['plugin'])} v{row['fromVersion']} to v{row['toVersion']} - {_md(row['sourceName'])}")
    if not report["changeDetails"]["updated"]:
        lines.append("No plugin metadata updates.")
    lines += [
        "", "## Still requires human release review", "",
        "A new upstream-reported hash matching downloaded bytes is not by itself "
        "an independently authorized release. Deferred mutable packages may "
        "fail to install and have not been replaced by verified immutable old bytes.",
        "",
    ]
    return "\n".join(lines)


def build_readme(report, rows):
    h = report["integrityHealth"]
    return "\n".join([
        "# MegaRepo - integrity-selected CATALOG PREVIEW (NOT PUBLISHED)", "",
        "**This is a development artifact, not an installable new release.**", "",
        f"- Selected plugin identities: **{report['uniquePlugins']}**",
        f"- Candidate entries accepted by the existing integrity gate: "
        f"**{h['candidateAccepted']}**",
        f"- Deferred old entries preserved only as unverified metadata: "
        f"**{h['unverifiedPreviousCarried']}**",
        f"- Blocked candidate changes: **{h['changedOrFailedCandidates']}**",
        f"- Legacy size-only entries: **{h['sizeOnlyLegacyCandidateAccepted']}**",
        f"- Candidate source indexes healthy: **{report['sourceHealth']['ok']}**", "",
        "The real MegaRepo installer catalog is unchanged. A mutable old URL "
        "may refer to different bytes and is not a trustworthy binary fallback. "
        "See STATUS.md and reconciliation.json for the audit trail.", "",
    ])


def write_bundle(directory, bundle):
    directory = Path(directory)
    if directory.exists():
        raise FileExistsError("Preview output exists; cannot overwrite or publish over existing files")
    directory.mkdir(parents=True, exist_ok=False)
    for name, value in bundle.items():
        path = directory / name
        if not name or "/" in name or "\\" in name:
            raise ValueError("Invalid bundle file name")
        text = (
            json.dumps(value, indent=2, ensure_ascii=False) + "\n"
            if name.endswith(".json") else str(value).rstrip() + "\n"
        )
        path.write_text(text, encoding="utf-8")
    return directory


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("candidate-dir", "previous-dir", "selection-dir", "output-dir"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--mode", choices=("compatibility", "quarantine"),
                        default="compatibility")
    args = parser.parse_args(argv)
    source_dir, previous_dir, selection_dir = [
        args.candidate_dir.resolve(), args.previous_dir.resolve(),
        args.selection_dir.resolve()
    ]
    destination = args.output_dir.resolve()
    if any(destination == d or destination.is_relative_to(d)
           for d in (source_dir, previous_dir, selection_dir)):
        parser.error("Output must be outside all existing input directories")
    if destination.name.lower() in ("builds", "merged", "production", "source"):
        parser.error("Output directory cannot be a production working directory")
    read = lambda root, file, typ: _load(root / file, typ)
    bundle = reconcile(
        read(source_dir, "plugins.json", list),
        read(previous_dir, "plugins.json", list),
        read(source_dir, "provenance.json", list),
        read(previous_dir, "provenance.json", list),
        read(selection_dir, "preview.plugins.json", list),
        read(selection_dir, "preview.provenance.json", list),
        read(selection_dir, "integrity-selection.json", dict),
        read(selection_dir, "verification-report.json", dict),
        read(source_dir, "merge-report.json", dict),
        read(previous_dir, "merge-report.json", dict),
        read(source_dir, "repo.json", dict),
        read(previous_dir, "repo.json", dict),
        mode=args.mode,
    )
    write_bundle(destination, bundle)
    summary = bundle["merge-report.json"]
    print(json.dumps({
        "mode": args.mode,
        "candidatePlugins": summary["integrityHealth"]["candidateChecked"],
        "proposedPlugins": summary["uniquePlugins"],
        "carriedUnverified": summary["integrityHealth"]["unverifiedPreviousCarried"],
        "blockedCandidateUpdates": summary["integrityHealth"]["changedOrFailedCandidates"],
        "releaseAuthorized": False,
    }, sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
