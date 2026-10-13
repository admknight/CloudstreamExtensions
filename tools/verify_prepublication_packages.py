#!/usr/bin/env python3
"""Independent full-byte prepublication gate for guarded MegaRepo artifacts.

All selected packages are downloaded and independently SHA-256 checked against
their final installer-facing metadata, not merely candidate sources. Only
explicitly recorded legacy deferred identities may fail, subject to a strict
limit. A policy-approved upgrade or immutable fallback can NEVER use this
legacy exception. No catalog mutation or binary approval is performed.
"""
import argparse
import json
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from audit_package_integrity import plugin_identity, verify_package


def check_release(plugins, provenance, report, *, checker=verify_package,
                  max_deferred=1, workers=8,
                  previous_plugins=None, previous_provenance=None):
    if type(max_deferred) is not int or not 0 <= max_deferred <= 24:
        raise ValueError("Invalid bounded legacy exception limit")
    if type(workers) is not int or not 1 <= workers <= 12:
        raise ValueError("Invalid worker count")
    if not all(isinstance(x, list) for x in (plugins, provenance)):
        raise ValueError("Catalog/provenance must be lists")
    if not isinstance(report, dict):
        raise ValueError("Missing guarded release evidence")
    keys = [plugin_identity(p) for p in plugins]
    origins = {str(s.get("plugin") or s.get("originalName") or "").strip().casefold():s
               for s in provenance}
    if (len(plugins) < 50 or len(plugins) != len(provenance)
            or len(origins) != len(provenance)
            or not all(keys) or len(keys) != len(set(keys))
            or set(keys) != set(origins)
            or report.get("uniquePlugins") != len(keys)
            or report.get("releaseEligible") is not True):
        raise ValueError("Incomplete or inconsistent production candidate")
    holds = report.get("deferredUnverified")
    if not isinstance(holds, list):
        raise ValueError("Missing deferred exception ledger")
    exceptions = [str(d.get("plugin") or "").casefold()
                  for d in holds if isinstance(d, dict)]
    if (len(exceptions) != len(holds) or not all(exceptions)
            or len(exceptions) != len(set(exceptions))
            or not set(exceptions).issubset(keys)
            or len(exceptions) > max_deferred
            or report.get("integrityHealth", {}).get("unverifiedPreviousCarried")
               != len(exceptions)):
        raise ValueError("Unverified legacy exceptions exceed release allowance")
    upgraded = {
        str(d.get("plugin") or "").casefold()
        for d in report.get("trustedPolicyUpgrades", [])
    }
    pinned = {
        str(d.get("plugin") or "").casefold()
        for d in report.get("quarantineIncidents", [])
        if d.get("disposition") == "retained_immutable_previous"
        and d.get("fallbackVerified") is True
    }
    if set(exceptions) & (upgraded | pinned):
        raise ValueError("Exception attempted to mask verified upgraded or pinned plugin")
    if (previous_plugins is None) != (previous_provenance is None):
        raise ValueError("Previous catalog/provenance must be supplied together")
    previous_by_key = {}
    previous_src = {}
    if previous_plugins is not None:
        previous_by_key = {plugin_identity(p):p for p in previous_plugins}
        previous_src = {
            str(s.get("plugin") or s.get("originalName") or "").strip().casefold():s
            for s in previous_provenance
        }
        if (len(previous_by_key) != len(previous_plugins)
                or len(previous_src) != len(previous_provenance)
                or set(previous_by_key) != set(previous_src)):
            raise ValueError("Previous catalog/provenance identity mismatch")
    preexisting_url_mismatches = []
    for item in plugins:
        key = plugin_identity(item)
        if origins[key].get("packageUrl") != item.get("url"):
            prior = previous_by_key.get(key)
            old_src = previous_src.get(key)
            if (not prior or not old_src
                    or prior != item or old_src != origins[key]):
                raise ValueError("New catalog/provenance package URL mismatch for " + key)
            preexisting_url_mismatches.append(key)
    checks = {}
    with ThreadPoolExecutor(max_workers=workers) as pool:
        jobs = {pool.submit(checker, p):plugin_identity(p) for p in plugins}
        for job in as_completed(jobs):
            key = jobs[job]
            try:
                checks[key] = job.result()
            except Exception as e:
                checks[key] = {"status":"error","error":type(e).__name__}
    unexpected, known = [], []
    by_key = {plugin_identity(p):p for p in plugins}
    for key in keys:
        check = checks[key]
        expected = by_key[key]
        verified = (
            isinstance(check, dict) and check.get("status")=="hash_verified"
            and check.get("actualFileSize")==expected.get("fileSize")
            and str(check.get("actualFileHash") or "").lower()
                == str(expected.get("fileHash") or "").lower()
        )
        if not verified:
            entry = {"plugin":key,"reason":check.get("reason") or check.get("error")
                     or check.get("status") or "invalid hash result"}
            (known if key in exceptions else unexpected).append(entry)
    if unexpected:
        raise ValueError("Unexpected published-byte mismatch: " +
                         ", ".join(x["plugin"] for x in unexpected))
    # Permitted exceptions are publicly reported, NEVER called checksum verified.
    return {
        "checked":len(keys), "hashVerified":len(keys)-len(known),
        "knownLegacyFailures":known, "unexpectedFailures":[],
        "fullByteIntegrityPassed":not bool(known),
        "releaseAllowedWithDocumentedLegacyException":True,
        "exceptionLimit":max_deferred,
        "unchangedPreviousUrlProvenanceDiscrepancies": preexisting_url_mismatches,
    }


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-dir",required=True,type=Path)
    parser.add_argument("--report",required=True,type=Path)
    parser.add_argument("--max-deferred",type=int,default=1)
    parser.add_argument("--previous-dir",type=Path)
    args=parser.parse_args()
    root=args.release_dir
    read=lambda name:json.loads((root/name).read_text(encoding="utf-8"))
    previous_plugins = previous_provenance = None
    if args.previous_dir:
        previous_plugins=json.loads((args.previous_dir/"plugins.json").read_text())
        previous_provenance=json.loads((args.previous_dir/"provenance.json").read_text())
    verdict=check_release(read("plugins.json"),read("provenance.json"),
                          read("merge-report.json"),max_deferred=args.max_deferred,
                          previous_plugins=previous_plugins,
                          previous_provenance=previous_provenance)
    args.report.write_text(json.dumps(verdict,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({k:v for k,v in verdict.items()
                      if k!="knownLegacyFailures"},indent=2))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
