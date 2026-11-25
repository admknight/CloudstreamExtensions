#!/usr/bin/env python3
"""Prepare an isolated URL-only correction to an existing published snapshot.

A repair transaction is not a normal guarded release. Every changed URL must
serve the identical previously published SHA-256 and size, independently
verified after assembly. Existing unresolved identities are preserved verbatim
and explicitly reported, not approved. No new identities or metadata accepted.
"""
import argparse
import copy
import json
import os
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

import merge_upstreams as template
from audit_package_integrity import plugin_identity, verify_package
from plan_per_plugin_selection import canonical_sha256
from plan_published_repairs import exact_verified, safe_check
from verified_immutable_fallback import validated_recovery_locks, same_package_url

METHOD = "byte_identical_repair_only"
EDITED = {"repair_verified", "protect_verified"}
STATUSES = EDITED | {"unchanged_verified", "unresolved"}
OUTPUTS = ("plugins.json", "provenance.json", "repo.json", "merge-report.json",
           "release-diff.json", "STATUS.md", "README.md", "RELEASE_NOTES.md",
           "BUILD_HISTORY.md", "byte-repair-proof.json")


def prepare(plugins, origins, inventory, plan, *, checker=verify_package, workers=6):
    locks = validated_recovery_locks(inventory, plugins, origins)
    if not isinstance(plan, dict) or (
        plan.get("mode") != "READ_ONLY_PUBLISHED_URL_REPAIR_PLAN"
        or plan.get("autoPublicationAuthorized") is not False
        or plan.get("actualPublicationPerformed") is not False
        or plan.get("sourceCatalogDigest") != canonical_sha256(plugins)
        or plan.get("sourceProvenanceDigest") != canonical_sha256(origins)
        or plan.get("publishedCount") != len(plugins)
        or not isinstance(plan.get("rows"), list)
    ):
        raise ValueError("Repair plan does not bind the published snapshot")
    if not isinstance(inventory, dict) or not 1 <= workers <= 8:
        raise ValueError("Missing immutable evidence or invalid workers")
    old = {plugin_identity(x): x for x in plugins}
    origin_key = lambda x: str(x.get("plugin") or x.get("originalName") or "").strip().casefold()
    old_origins = {origin_key(x): x for x in origins}
    rows = {r["plugin"]: r for r in plan["rows"]}
    if (len(old) != len(plugins) or len(old_origins) != len(origins)
            or len(rows) != len(plan["rows"]) or set(old) != set(rows)
            or set(old) != set(old_origins)):
        raise ValueError("Repair identities do not reconcile")
    before, changed, recovered = set(), set(), set()
    new, new_origins = copy.deepcopy(old), copy.deepcopy(old_origins)
    for key, row in rows.items():
        p, origin = old[key], old_origins[key]
        verified = exact_verified(p, row.get("originalCheck"))
        status = row.get("status")
        if (status not in STATUSES or row.get("originalBytesVerified") is not verified
                or row.get("originalUrl") != p.get("url")
                or row.get("sourceId") != origin.get("sourceId")
                or any(row.get(f) != p.get(f) for f in ("version", "fileSize", "fileHash"))
                or row.get("provenanceUrlMatches") is not same_package_url(origin.get("packageUrl"), p.get("url"))):
            raise ValueError("Repair row differs from original published metadata: " + key)
        if verified:
            before.add(key)
        proposed = row.get("proposedUrl")
        if status in EDITED:
            lock = locks.get(key)
            if (not lock or proposed != lock["pinnedUrl"] or proposed == p.get("url")
                    or not same_package_url(origin.get("packageUrl"), p.get("url"))
                    or row.get("proposedBytesVerified") is not True
                    or row.get("commitSha") != lock["commitSha"]
                    or (status == "repair_verified") == verified):
                raise ValueError("Invalid immutable URL-only edit: " + key)
            new[key]["url"] = proposed
            new_origins[key]["packageUrl"] = proposed
            changed.add(key)
            if not verified:
                recovered.add(key)
        elif proposed != p.get("url") or row.get("proposedBytesVerified") is not verified:
            raise ValueError("Unverified repair would edit existing metadata: " + key)
    if (plan.get("originalHashMatches") != len(before)
            or plan.get("proposedHashMatches") != len(before | recovered)
            or plan.get("counts") != {s: sum(r["status"] == s for r in rows.values())
                                      for s in STATUSES}):
        raise ValueError("Repair plan counters do not reconcile")
    # Independent, complete scan of actual proposed installer URLs. Never rely
    # on a prior plan's success flag for the package being published.
    order = [plugin_identity(p) for p in plugins]
    with ThreadPoolExecutor(max_workers=workers) as pool:
        checks = dict(zip(order, pool.map(lambda k: safe_check(checker, new[k]), order)))
    for k, result in checks.items():
        result["plugin"] = k
        result["url"] = new[k]["url"]
    after = {k for k in order if exact_verified(new[k], checks[k])}
    if not changed.issubset(after) or not before.issubset(after):
        raise ValueError("Independent recheck failed a changed or previously verified package")
    final_plugins = [new[k] for k in order]
    final_origins = [new_origins[origin_key(o)] for o in origins]
    for k in order:
        if k in changed:
            if (new[k] != dict(old[k], url=rows[k]["proposedUrl"])
                    or new_origins[k] != dict(old_origins[k], packageUrl=rows[k]["proposedUrl"])):
                raise ValueError("Non-URL metadata altered")
        elif new[k] != old[k] or new_origins[k] != old_origins[k]:
            raise ValueError("Unresolved or unchanged entry modified")
    residual = [{"plugin": k, "sourceId": old_origins[k]["sourceId"],
                 "url": new[k]["url"], "check": checks[k],
                 "provenanceUrlMatches": same_package_url(new_origins[k].get("packageUrl"), new[k]["url"])}
                for k in order if k not in after or not same_package_url(new_origins[k].get("packageUrl"), new[k]["url"])]
    proof = {
        "mode": "BYTE_IDENTICAL_URL_REPAIR_ONLY",
        "normalGuardedReleaseAuthorized": False,
        "repairEligible": bool(changed),
        "publishedCount": len(plugins),
        "beforeHashMatches": len(before), "afterHashMatches": len(after),
        "fullByteIntegrityPassed": len(after) == len(plugins),
        "fullAuditPerformed": False, "previouslyVerifiedLost": [],
        "sourceCatalogDigest": canonical_sha256(plugins),
        "sourceProvenanceDigest": canonical_sha256(origins),
        "outputCatalogDigest": canonical_sha256(final_plugins),
        "outputProvenanceDigest": canonical_sha256(final_origins),
        "changed": [{"plugin": k, "sourceId": old_origins[k]["sourceId"],
                     "oldUrl": old[k]["url"], "newUrl": new[k]["url"],
                     "version": old[k]["version"], "fileHash": old[k]["fileHash"],
                     "fileSize": old[k]["fileSize"], "commitSha": locks[k]["commitSha"],
                     "repairedFailure": k in recovered} for k in sorted(changed)],
        "unresolved": residual, "checks": [checks[k] for k in order],
        "warning": "Only existing byte-identical URL repairs; unresolved entries remain unchanged and unapproved. Not a clean full audit."
    }
    return final_plugins, final_origins, proof


def probe_sources(provenance, previous_report, checks, *, fetch=template.fetch_source_plugins):
    """Index health is independent evidence; a failed index does not approve bytes."""
    by_id = {s["id"]: s for s in previous_report["sourceStatus"]}
    published_counts = Counter(o["sourceId"] for o in provenance)
    failing = Counter(o["sourceId"] for o in provenance
                      if checks[str(o.get("plugin") or o.get("originalName")).casefold()].get("status") == "error")
    configured = {s["id"]: s for s in template.SOURCES}
    if set(published_counts) - set(configured):
        raise ValueError("Published origin no longer exists in configured source registry")
    def probe(s):
        ident = s["id"]
        row = {"id": ident, "name": s["name"], "repo": s["repo"], "index": s["index"],
               "priority": s.get("priority", 100), "ok": False, "rawCount": None,
               "includedCount": published_counts[ident], "packageFailed": failing[ident],
               "duplicateSkipped": by_id.get(ident, {}).get("duplicateSkipped", 0)}
        try:
            data = fetch(s)
            if not isinstance(data, list):
                raise ValueError("Source index not a list")
            row.update(ok=True, rawCount=len(data))
        except Exception as exc:
            row["error"] = type(exc).__name__ + ": " + str(exc)
        return row
    with ThreadPoolExecutor(max_workers=6) as pool:
        return list(pool.map(probe, template.SOURCES))


def make_bundle(plugins, origins, previous_report, repo_manifest, history, proof,
                source_status, *, generated_at, run_id="", code_sha=""):
    if (proof["outputCatalogDigest"] != canonical_sha256(plugins)
            or proof["outputProvenanceDigest"] != canonical_sha256(origins)):
        raise ValueError("Output snapshot differs from independent proof")
    trusted = proof.get("mode") == "TRUSTED_EXISTING_UPGRADE_REPAIR_ONLY"
    if proof.get("mode") not in ("BYTE_IDENTICAL_URL_REPAIR_ONLY", "TRUSTED_EXISTING_UPGRADE_REPAIR_ONLY"):
        raise ValueError("Unrecognized repair proof")
    method = "trusted_existing_upgrade_repair_only" if trusted else METHOD
    by_key = {plugin_identity(p): p for p in plugins}
    origin_by_key = {str(o.get("plugin") or o.get("originalName")).casefold(): o for o in origins}
    checks = {r["plugin"]: r for r in proof["checks"]}
    changed = {r["plugin"]: r for r in proof["changed"]}
    residual = {r["plugin"]: r for r in proof["unresolved"]}
    if len(checks) != len(plugins) or set(checks) != set(by_key):
        raise ValueError("Incomplete independent package scan")
    if sum(s["includedCount"] for s in source_status) != len(plugins):
        raise ValueError("Source inclusion counts do not reconcile")
    report = copy.deepcopy(previous_report)
    report["lastFullAggregation"] = previous_report.get("lastFullAggregation", {
        "generatedAt": previous_report["generatedAt"],
        "candidateStatus": previous_report["candidateStatus"],
        "sourceHealth": previous_report["sourceHealth"]})
    report.update(generatedAt=generated_at, candidateStatus="REPAIR ONLY - NOT A FULL GUARDED RELEASE",
                  publicationMethod=method, releaseEligible=False, releaseAuthorized=False,
                  previousPlugins=len(plugins), uniquePlugins=len(plugins),
                  sourceHealth={"ok": sum(s["ok"] for s in source_status),
                                "failed": sum(not s["ok"] for s in source_status)},
                  sourceStatus=source_status, sourceHealthObservedAt=generated_at,
                  sourceHealthCurrentVerified=True,
                  changes={"added": 0, "updated": len(changed), "unchanged": len(plugins)-len(changed), "removed": 0},
                  removedPlugins=[], deferredUnverified=list(residual.values()),
                  candidateStatusDisclaimer=proof["warning"])
    # Source duplicate decisions and old trust records are historical only.
    report["priorGuardedEvidenceHistorical"] = True
    report["previewPolicy"] = method
    if trusted:
        report["trustedPolicyUpgrades"] = copy.deepcopy(proof["policyAttestations"])
    report["previewOfCandidateStatus"] = "NOT_APPLICABLE_REPAIR_ONLY"
    report["sourceChanges"] = {"added": [], "removed": [], "healthChanged": []}
    old_status = {s["id"]: s for s in previous_report["sourceStatus"]}
    for s in source_status:
        if s["id"] in old_status and bool(old_status[s["id"]]["ok"]) != s["ok"]:
            report["sourceChanges"]["healthChanged"].append({
                "id": s["id"], "name": s["name"],
                "fromOk": bool(old_status[s["id"]]["ok"]), "toOk": s["ok"]})
    unreachable = [k for k, check in checks.items() if check.get("status") == "error"]
    report["packageHealth"] = {"reachable": len(plugins)-len(unreachable), "failed": len(unreachable)}
    report["failedPlugins"] = [{"plugin": by_key[k]["internalName"],
        "sourceName": origin_by_key[k]["sourceName"], "sourceId": origin_by_key[k]["sourceId"],
        "version": by_key[k]["version"], "error": checks[k].get("error", "Download error")}
        for k in unreachable]
    report["integrityHealth"] = {
        "candidateChecked": len(plugins), "candidateAccepted": proof["afterHashMatches"],
        "pinnedPreviousRecovered": 0 if trusted else len(changed),
        "immutableOldRetained": 0 if trusted else len(changed),
        "trustedExistingUpgrades": len(changed) if trusted else 0,
        "unverifiedPreviousCarried": len(residual), "quarantinedNotInProposal": 0,
        "changedOrFailedCandidates": len(residual), "sizeOnlyLegacyCandidateAccepted": 0,
        "previewPolicy": method, "releaseEligibleWithWarnings": False,
        "publishedHashVerified": proof["afterHashMatches"],
        "fullByteIntegrityPassed": proof["fullByteIntegrityPassed"]}
    details = []
    for k, row in changed.items():
        p, origin = by_key[k], origin_by_key[k]
        details.append(dict(row, name=p.get("name"), fromVersion=row.get("oldVersion", p["version"]), toVersion=p["version"],
                            fromSource=origin["sourceName"], toSource=origin["sourceName"],
                            fromSourceId=origin["sourceId"], toSourceId=origin["sourceId"],
                            sourceName=origin["sourceName"], category=origin.get("category")))
    report["changeDetails"] = {"added": [], "recovered": [], "updated": details, "removed": []}
    report["customProviderChanges"] = [dict(d, action="updated") for d in details if d["sourceId"] == "adam-custom"]
    report["repairOnly"] = {k: v for k, v in proof.items() if k != "checks"}
    report["categoryCounts"] = dict(Counter(o.get("category") or "Other" for o in origins))
    rows = [{"name": p.get("name") or p["internalName"],
             "originalName": origin_by_key[k].get("originalName") or p["internalName"],
             "category": origin_by_key[k].get("category") or "Other",
             "version": p["version"], "language": p.get("language") or "",
             "tvTypes": template.tv_types_text(p), "sourceId": origin_by_key[k]["sourceId"],
             "sourceName": origin_by_key[k]["sourceName"],
             "change": ("Trusted existing version upgrade" if trusted else "Byte-identical URL repair") if k in changed else
                       "Unresolved - unchanged" if k in residual else "Unchanged"}
            for k, p in by_key.items()]
    label = "Trusted existing version recovery" if trusted else "URL-only recovery"
    banner = (f"**{label}; not a full guarded catalog release.** "
              f"{proof['afterHashMatches']}/{len(plugins)} installer URLs matched the published SHA-256. "
              f"{len(residual)} unresolved entries retained unchanged. "
              "Source indexes checked separately; duplicate decisions are historical. "
              ("Only independently verified, standing-policy existing upgrades adopted; no new provider approved. " if trusted else
               "No new binary or provider approved. ") + "Runtime playback is not verified.\n\n")
    status = template.build_status(report, rows).replace("## Source health", banner + "## Source health", 1)
    readme = template.build_readme(report, rows).replace(
        "Last successful refresh:", "Last repair verification:" if trusted else "Last URL-repair verification:").replace(
        "plugins are currently published and package-reachable.", "plugin identities are currently published.")
    readme = readme.replace("## 📊 Current dashboard", banner + "## 📊 Current dashboard", 1)
    notes = "# " + label + "\n\n" + banner + "\n" + "\n".join(
        (f"- {template.md(r['plugin'])}: v{r['oldVersion']} to v{r['version']}, publisher-policy SHA-256 verified at immutable revision"
         if trusted else f"- {template.md(r['plugin'])}: unchanged v{r['version']}, SHA-256 verified at immutable revision")
        for r in proof["changed"]) + "\n"
    diff = {"generatedAt": generated_at, "candidateStatus": report["candidateStatus"],
            "catalog": {"plugins": len(plugins), "healthySources": report["sourceHealth"]["ok"],
                        "failedSources": report["sourceHealth"]["failed"],
                        "reachablePackages": report["packageHealth"]["reachable"],
                        "packageFailures": report["packageHealth"]["failed"]},
            "changes": report["changes"], "changeDetails": report["changeDetails"],
            "sourceChanges": report["sourceChanges"], "customProviderChanges": report["customProviderChanges"],
            "integrityHealth": report["integrityHealth"], "releaseEligible": False}
    history_lines = history.splitlines()
    if len(history_lines) < 4 or not history_lines[3].startswith("| ---"):
        raise ValueError("Unrecognized production build history")
    run_link = (f"[{run_id}](https://github.com/admknight/CloudstreamExtensions/actions/runs/{run_id})"
                if run_id else "local validation")
    row = (f"| {generated_at} | {run_link} | {code_sha[:8]} | {len(plugins)} | "
           f"{report['sourceHealth']['ok']} | {report['packageHealth']['failed']} | 0 | {len(changed)} | 0 | {label}; full release blocked |")
    history = "\n".join(history_lines[:4] + [row] + history_lines[4:104]) + "\n"
    return {"plugins.json": plugins, "provenance.json": origins, "repo.json": repo_manifest,
            "merge-report.json": report, "release-diff.json": diff, "STATUS.md": status,
            "README.md": readme, "RELEASE_NOTES.md": notes, "BUILD_HISTORY.md": history,
            ("trusted-repair-proof.json" if trusted else "byte-repair-proof.json"): proof}


def main(argv=None):
    cli = argparse.ArgumentParser(description=__doc__)
    for n in ("previous-dir", "inventory", "plan", "output-dir"):
        cli.add_argument("--" + n, required=True, type=Path)
    args = cli.parse_args(argv)
    previous, dest = args.previous_dir.resolve(), args.output_dir.resolve()
    if dest.exists() or dest == previous or dest.is_relative_to(previous):
        cli.error("Output must be a new separate staging directory")
    read = lambda p: json.loads(p.read_text(encoding="utf-8"))
    plugins, origins, proof = prepare(read(previous/"plugins.json"), read(previous/"provenance.json"),
                                      read(args.inventory), read(args.plan))
    dest.mkdir(parents=True)
    if not proof["repairEligible"]:
        (dest/"byte-repair-proof.json").write_text(json.dumps(proof, indent=2)+"\n")
        return
    prior_report = read(previous/"merge-report.json")
    sources = probe_sources(origins, prior_report, {r["plugin"]: r for r in proof["checks"]})
    bundle = make_bundle(plugins, origins, prior_report, read(previous/"repo.json"),
                         (previous/"BUILD_HISTORY.md").read_text(), proof, sources,
                         generated_at=datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
                         run_id=os.getenv("GITHUB_RUN_ID", ""), code_sha=os.getenv("GITHUB_SHA", ""))
    if set(bundle) != set(OUTPUTS):
        raise ValueError("Incomplete repair bundle")
    for name, value in bundle.items():
        (dest/name).write_text((json.dumps(value, indent=2, ensure_ascii=False)+"\n")
                               if not isinstance(value, str) else value+"\n", encoding="utf-8")


if __name__ == "__main__":
    main()
