#!/usr/bin/env python3
"""Recover failing existing packages under the unchanged standing publisher policy.

No new identity, source transfer, same-version rebuild, status change, API
change, or new publisher is authorized. Independently check old installer
URLs, the exact commit-pinned publisher index, mutable and pinned package
bytes, and the complete resulting catalog. No writes to production here.
"""
import argparse
import copy
import json
import os
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

from audit_package_integrity import plugin_identity, verify_package, fetch_json, DIGEST_RE
from index_immutable_recovery import github_resolve_ref
from plan_per_plugin_selection import canonical_sha256
from plan_published_repairs import safe_check, exact_verified
from prepare_published_url_repairs import make_bundle, probe_sources
from trusted_upstream_candidates import promote, _source_rules, _source_map
from verified_immutable_fallback import same_package_url
from verify_candidate_integrity import index_plugins, policy_approved

MODE = "TRUSTED_EXISTING_UPGRADE_REPAIR_ONLY"
FIELDS = ("version", "fileSize", "fileHash", "url")


def prepare(plugins, origins, sources, policy, *, checker=verify_package,
            fetch_index=fetch_json, resolve=github_resolve_ref, token=None, workers=6):
    if type(workers) is not int or not 1 <= workers <= 8:
        raise ValueError("Invalid worker count")
    old, old_src = index_plugins(plugins), _source_map(origins)
    if not old or set(old) != set(old_src) or len(old_src) != len(origins):
        raise ValueError("Published snapshot identity mismatch")
    rules = _source_rules(sources, policy)
    order = list(old)
    def scan(items):
        with ThreadPoolExecutor(max_workers=workers) as pool:
            checks = dict(zip(order, pool.map(lambda k: safe_check(checker, items[k]), order)))
        for k, result in checks.items():
            result.update(plugin=k, url=items[k].get("url"))
        return checks
    original = scan(old)
    before = {k for k in order if exact_verified(old[k], original[k])}
    candidate, candidate_src = copy.deepcopy(old), copy.deepcopy(old_src)
    pending, skipped, cache, pinned_indexes = set(), {}, {}, {}
    for k in order:
        if k in before:
            continue
        p, origin = old[k], old_src[k]
        source_id = origin.get("sourceId")
        if source_id not in rules:
            skipped[k] = "source not authorized by existing publisher policy"
            continue
        if not same_package_url(origin.get("packageUrl"), p.get("url")):
            skipped[k] = "published package/provenance URL differs"
            continue
        if origin.get("selectedVersion") != p.get("version"):
            skipped[k] = "published version/provenance mismatch"
            continue
        source = rules[source_id][0]
        if source_id not in cache:
            try:
                cache[source_id] = index_plugins(fetch_index(source["index"]))
            except Exception as exc:
                cache[source_id] = None
        index = cache[source_id]
        if index is None:
            skipped[k] = "registered source index unavailable"
            continue
        new = index.get(k)
        if not new:
            skipped[k] = "identity absent from registered source"
            continue
        if (type(p.get("version")) is not int or type(new.get("version")) is not int
                or new["version"] <= p["version"]):
            skipped[k] = "same or older upstream version"
            continue
        if (new.get("status") != p.get("status")
                or type(new.get("apiVersion")) is not int
                or new.get("apiVersion") != p.get("apiVersion")
                or new.get("authors") != origin.get("originalAuthors")
                or new.get("repositoryUrl") != origin.get("originalRepositoryUrl")
                or new.get("internalName") != p.get("internalName")):
            skipped[k] = "status/API/author/repository/identity change requires review"
            continue
        if (type(new.get("fileSize")) is not int or new["fileSize"] <= 0
                or not isinstance(new.get("fileHash"), str)
                or not DIGEST_RE.fullmatch(new["fileHash"])
                or not isinstance(new.get("url"), str)):
            skipped[k] = "candidate lacks valid package fingerprint"
            continue
        for field in FIELDS:
            candidate[k][field] = new[field]
        candidate[k]["url"] = new["url"].replace(" ", "%20")
        candidate_src[k]["packageUrl"] = candidate[k]["url"]
        candidate_src[k]["selectedVersion"] = new["version"]
        pending.add(k)
    def read_pin(url):
        data = fetch_index(url)
        pinned_indexes[url] = index_plugins(data)
        return data
    staged, staged_src, approvals, detail = promote(
        [candidate[k] for k in order], plugins,
        [candidate_src[k] for k in order], origins, sources, policy,
        resolve=resolve, fetch_index=read_pin,
        checker=lambda p: safe_check(checker, p), token=token)
    staged, staged_src = index_plugins(staged), _source_map(staged_src)
    final, final_src = copy.deepcopy(old), copy.deepcopy(old_src)
    approved = {}
    for row in detail["skipped"]:
        skipped[row["plugin"]] = row["reason"]
    for a in approvals:
        k = a["plugin"]
        if k not in pending or k in approved or k in before:
            raise ValueError("Unexpected trust attestation")
        pin = pinned_indexes[a["pinnedIndexUrl"]][k]
        p, origin = old[k], old_src[k]
        if (pin.get("apiVersion") != p.get("apiVersion")
                or pin.get("authors") != origin.get("originalAuthors")
                or pin.get("repositoryUrl") != origin.get("originalRepositoryUrl")
                or pin.get("internalName") != p.get("internalName")):
            skipped[k] = "pinned publisher metadata disagrees with preserved identity"
            continue
        new, new_src = staged[k], staged_src[k]
        if (new != dict(p, **{f: new[f] for f in FIELDS})
                or new_src != dict(origin, packageUrl=new["url"], selectedVersion=new["version"])):
            raise ValueError("Upgrade altered unrelated metadata")
        final[k], final_src[k], approved[k] = new, new_src, a
    for k in pending - set(approved):
        skipped.setdefault(k, "registered policy did not attest candidate")
    checks = scan(final)
    after = {k for k in order if exact_verified(final[k], checks[k])}
    if not before.issubset(after) or not set(approved).issubset(after):
        raise ValueError("Independent scan found changed or previously verified package failure")
    for k, a in approved.items():
        if not policy_approved(old[k], final[k], old_src[k]["sourceId"],
                               final_src[k]["sourceId"], checks[k], [a]):
            raise ValueError("Final immutable package fails existing trust gate")
    result = [final[k] for k in order]
    result_src = [final_src[str(o.get("plugin") or o.get("originalName")).strip().casefold()]
                  for o in origins]
    residual = [{"plugin": k, "sourceId": old_src[k]["sourceId"],
                 "url": final[k]["url"], "check": checks[k],
                 "provenanceUrlMatches": same_package_url(final_src[k].get("packageUrl"), final[k].get("url")),
                 "reason": skipped.get(k, "unresolved package or provenance")}
                for k in order if k not in after or not same_package_url(final_src[k].get("packageUrl"), final[k].get("url"))]
    proof = {
        "mode": MODE, "normalGuardedReleaseAuthorized": False,
        "repairEligible": bool(approved), "publishedCount": len(plugins),
        "beforeHashMatches": len(before), "afterHashMatches": len(after),
        "fullByteIntegrityPassed": len(after) == len(plugins),
        "fullAuditPerformed": False, "previouslyVerifiedLost": [],
        "sourceCatalogDigest": canonical_sha256(plugins),
        "sourceProvenanceDigest": canonical_sha256(origins),
        "outputCatalogDigest": canonical_sha256(result),
        "outputProvenanceDigest": canonical_sha256(result_src),
        "policyDigest": canonical_sha256(policy),
        "sourceRegistryDigest": canonical_sha256(sources),
        "eligibleCandidates": len(pending),
        "changed": [{"plugin": k, "sourceId": old_src[k]["sourceId"],
                     "oldUrl": old[k]["url"], "newUrl": final[k]["url"],
                     "oldVersion": old[k]["version"], "version": final[k]["version"],
                     "oldFileHash": old[k]["fileHash"], "fileHash": final[k]["fileHash"],
                     "oldFileSize": old[k]["fileSize"], "fileSize": final[k]["fileSize"],
                     "commitSha": approved[k]["immutableCommit"],
                     "repairedFailure": True} for k in sorted(approved)],
        "policyAttestations": [approved[k] for k in sorted(approved)],
        "unresolved": residual, "checks": [checks[k] for k in order],
        "warning": "Existing failing packages upgraded only under the standing publisher policy. Remaining exceptions are unchanged and unapproved. Not a clean full audit."
    }
    return result, result_src, proof


def main(argv=None):
    cli = argparse.ArgumentParser(description=__doc__)
    for n in ("previous-dir", "sources", "policy", "output-dir"):
        cli.add_argument("--" + n, required=True, type=Path)
    args = cli.parse_args(argv)
    prev, dest = args.previous_dir.resolve(), args.output_dir.resolve()
    if dest.exists() or dest == prev or dest.is_relative_to(prev):
        cli.error("Output must be a new separate staging directory")
    read = lambda p: json.loads(p.read_text(encoding="utf-8"))
    plugins, origins, proof = prepare(read(prev/"plugins.json"), read(prev/"provenance.json"),
                    read(args.sources), read(args.policy), token=os.getenv("GH_TOKEN"))
    dest.mkdir(parents=True)
    if proof["repairEligible"]:
        prior = read(prev/"merge-report.json")
        statuses = probe_sources(origins, prior, {r["plugin"]: r for r in proof["checks"]})
        bundle = make_bundle(plugins, origins, prior, read(prev/"repo.json"),
                    (prev/"BUILD_HISTORY.md").read_text(), proof, statuses,
                    generated_at=datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
                    run_id=os.getenv("GITHUB_RUN_ID", ""), code_sha=os.getenv("GITHUB_SHA", ""))
        for name, value in bundle.items():
            (dest/name).write_text((json.dumps(value, indent=2, ensure_ascii=False)+"\n")
                        if not isinstance(value, str) else value+"\n", encoding="utf-8")
    else:
        (dest/"trusted-repair-proof.json").write_text(json.dumps(proof,indent=2)+"\n")
    print(json.dumps({k:v for k,v in proof.items() if k not in ("checks","changed","unresolved","policyAttestations")},indent=2))


if __name__ == "__main__":
    main()
