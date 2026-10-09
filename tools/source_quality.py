#!/usr/bin/env python3
"""Advisory source-quality score from exact published source/provenance evidence.

Does not alter source priority, extension selection, approvals or publication.
Score = 60% observed 30-day index-fetch availability + 25% current package
contribution verification ratio + 15% current unique contribution presence.
These are ingestion signals, NOT playback or package-authenticity guarantees.
"""
import argparse
import json
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

STAMP = "%Y-%m-%d %H:%M:%S UTC"
MAX_SNAPSHOTS = 90


def parse_stamp(value):
    return datetime.strptime(value, STAMP).replace(tzinfo=timezone.utc)


def _nonnegative(row, name):
    v = row.get(name)
    if type(v) is not int or v < 0:
        raise ValueError("Missing or invalid source field: " + name)
    return v


def analyze(report, provenance, past=None):
    """Fail closed if the source report and provenance disagree."""
    if not isinstance(report, dict) or not isinstance(provenance, list):
        raise ValueError("Missing published source report/provenance")
    stamp = report.get("generatedAt")
    now = parse_stamp(stamp)
    statuses = report.get("sourceStatus")
    totals = report.get("sourceHealth")
    if not isinstance(statuses, list) or not statuses or not isinstance(totals, dict):
        raise ValueError("Incomplete published source status")
    if type(report.get("uniquePlugins")) is not int or report["uniquePlugins"] != len(provenance):
        raise ValueError("Published catalog/provenance count mismatch")
    counts = Counter()
    for row in provenance:
        if not isinstance(row, dict) or not isinstance(row.get("sourceId"), str) or not row["sourceId"]:
            raise ValueError("Malformed provenance source")
        counts[row["sourceId"]] += 1
    ids = set()
    compact = []
    for row in statuses:
        if not isinstance(row, dict):
            raise ValueError("Malformed source status")
        source = row.get("id")
        if not isinstance(source, str) or not source or source in ids or type(row.get("ok")) is not bool:
            raise ValueError("Invalid/duplicate source ID or state")
        ids.add(source)
        included = _nonnegative(row, "includedCount")
        raw = _nonnegative(row, "rawCount")
        duplicates = _nonnegative(row, "duplicateSkipped")
        package_failed = _nonnegative(row, "packageFailed")
        if counts[source] != included or duplicates > raw:
            raise ValueError("Source contribution counts do not reconcile: " + source)
        compact.append({"id": source, "ok": row["ok"], "includedCount": included,
                        "rawCount": raw, "duplicateSkipped": duplicates,
                        "packageFailed": package_failed})
    if not set(counts).issubset(ids) or sum(counts.values()) != report["uniquePlugins"]:
        raise ValueError("Source IDs/provenance identities differ")
    if (type(totals.get("ok")) is not int or type(totals.get("failed")) is not int or
            totals["ok"] != sum(s["ok"] for s in compact) or
            totals["failed"] != sum(not s["ok"] for s in compact)):
        raise ValueError("Source health totals disagree")
    if past is None:
        past = {"version": 1, "snapshots": []}
    if not isinstance(past, dict) or not isinstance(past.get("snapshots"), list):
        raise ValueError("Invalid prior source-quality history")
    snapshots = []
    for snap in past["snapshots"]:
        if not isinstance(snap, dict) or not isinstance(snap.get("sources"), list):
            raise ValueError("Malformed historical source snapshot")
        instant = parse_stamp(snap["generatedAt"])
        if instant <= now and instant >= now - timedelta(days=30) and instant != now:
            snapshots.append(snap)
    snapshots.append({"generatedAt": stamp,
                      "sources": [{"id": s["id"], "ok": s["ok"]} for s in compact]})
    snapshots.sort(key=lambda item: parse_stamp(item["generatedAt"]))
    snapshots = snapshots[-MAX_SNAPSHOTS:]
    latest = {s["id"]: s for s in compact}
    observed = {source: [] for source in ids}
    for snap in snapshots:
        seen = set()
        for value in snap["sources"]:
            if not isinstance(value, dict) or type(value.get("ok")) is not bool:
                raise ValueError("Malformed historical source observation")
            source = value.get("id")
            if source in observed and source not in seen:
                observed[source].append(value["ok"])
                seen.add(source)
    results = []
    for source, current in latest.items():
        history = observed[source]
        availability = sum(history) / len(history) if history else 0.0
        included, pkg_fail = current["includedCount"], current["packageFailed"]
        denominator = included + pkg_fail
        check_ratio = included / denominator if denominator else 0.0
        contributes = included > 0
        score = round(60 * availability + 25 * check_ratio + 15 * int(contributes), 1)
        results.append({
            "id": source, "indexHealthyNow": current["ok"],
            "rawCandidates": current["rawCount"],
            "uniqueContributed": included,
            "duplicateSkipped": current["duplicateSkipped"],
            "duplicateOnly": (current["rawCount"] > 0 and included == 0 and
                              current["duplicateSkipped"] >= current["rawCount"]),
            "reportedPackageFailures": pkg_fail,
            "observations30d": len(history),
            "failedIndexObservations30d": len(history) - sum(history),
            "indexAvailability30dPct": round(availability * 100, 1),
            "currentPackageContributionRatioPct": (round(check_ratio * 100, 1)
                                                   if denominator else None),
            "score": score,
        })
    results.sort(key=lambda s: (-s["score"], -s["uniqueContributed"], s["id"]))
    return {
        "report": {
            "version": 1, "generatedAt": stamp, "sourceCount": len(results),
            "publishedPluginCount": report["uniquePlugins"], "healthySources": totals["ok"],
            "sources": results,
            "scoring": {
                "basis": "Advisory upstream ingestion evidence only; not playback or authenticity",
                "weights": {"indexAvailability30d": 60,
                            "currentPackageContributionRatio": 25,
                            "uniqueContributionPresent": 15},
                "noEvidenceContributionRatio": 0,
                "sourceSelectionAffected": False,
            },
        },
        "history": {"version": 1, "updatedAt": stamp, "snapshots": snapshots},
    }


def render_markdown(report):
    def cell(x):
        return str(x).replace("|", "\\|").replace("\n", " ")
    lines = ["# MegaRepo Upstream Source Quality (Advisory)", "",
             f"Updated: **{report['generatedAt']}** · Published plugins: **{report['publishedPluginCount']}**",
             "", "> These are ingestion-quality signals only. Scores never modify source priorities, remove plugins or approve binaries.",
             "", "Score = 60% recent index availability + 25% current verified package contribution ratio + 15% unique contribution presence.",
             "Where there are no package-contribution observations, that 25-point portion is zero rather than inferred healthy.",
             "", "| Source ID | Score /100 | Healthy | 30d index fetch success | Unique plugins | Duplicates skipped | Reported package failures |",
             "| --- | ---: | --- | ---: | ---: | ---: | ---: |"]
    for row in report["sources"]:
        lines.append("| " + " | ".join(map(cell, [
            row["id"], row["score"], "Yes" if row["indexHealthyNow"] else "No",
            f"{row['indexAvailability30dPct']}% ({row['observations30d']} observations)",
            row["uniqueContributed"], row["duplicateSkipped"], row["reportedPackageFailures"],
        ])) + " |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--provenance", required=True, type=Path)
    parser.add_argument("--history-in", type=Path)
    parser.add_argument("--history-out", required=True, type=Path)
    parser.add_argument("--json", required=True, type=Path)
    parser.add_argument("--markdown", required=True, type=Path)
    args = parser.parse_args()
    get = lambda path: json.loads(path.read_text(encoding="utf-8"))
    old = get(args.history_in) if args.history_in and args.history_in.exists() else None
    result = analyze(get(args.report), get(args.provenance), old)
    for path, content in (
        (args.json, json.dumps(result["report"], indent=2, ensure_ascii=False) + "\n"),
        (args.history_out, json.dumps(result["history"], indent=2, ensure_ascii=False) + "\n"),
        (args.markdown, render_markdown(result["report"])),
    ):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    print(json.dumps({"sources": result["report"]["sourceCount"],
                      "published": result["report"]["publishedPluginCount"],
                      "historySnapshots": len(result["history"]["snapshots"])}))


if __name__ == "__main__":
    main()
