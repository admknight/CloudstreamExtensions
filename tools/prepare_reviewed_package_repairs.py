#!/usr/bin/env python3
"""Exact, individually reviewed recovery of failing existing identities only.

An exception is a single immutable release and old fingerprint, not standing
publisher trust. Build metadata, source declaration and separate package
downloads are checked again. The entire proposed catalog must not regress.
"""
import argparse
import copy
import json
import os
import re
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote

from audit_package_integrity import plugin_identity, verify_package, fetch_json, DIGEST_RE
from index_immutable_recovery import parse_package_url, pinned_url, _http_json, _http_bytes, GIT_SHA
from plan_per_plugin_selection import canonical_sha256
from plan_published_repairs import safe_check, exact_verified
from prepare_published_url_repairs import make_bundle, probe_sources
from trusted_upstream_candidates import _source_map, REPO
from verified_immutable_fallback import same_package_url
from verify_candidate_integrity import index_plugins

MODE = "REVIEWED_EXISTING_PACKAGE_REPAIR_ONLY"
FINGERPRINT = ("version", "status", "apiVersion", "fileSize", "fileHash", "url")
EDIT_FIELDS = ("version", "status", "fileSize", "fileHash", "url")


def fingerprint(p):
    return {field: p.get(field) for field in FINGERPRINT}


def declaration(text):
    version = re.findall(r"(?m)^\s*version\s*=\s*(\d+)\s*(?://.*)?$", text)
    status = re.findall(r"(?m)^\s*status\s*=\s*(\d+)\s*(?://.*)?$", text)
    authors = re.findall(r"(?m)^\s*authors\s*=\s*listOf\(([^\n]*)\)", text)
    if len(version) != 1 or len(status) != 1 or len(authors) != 1:
        raise ValueError("Source manifest declaration is ambiguous")
    return int(version[0]), int(status[0]), re.findall(r'"([^"]*)"', authors[0])


def prepare(plugins, origins, sources, review, *, checker=verify_package,
            fetch_index=fetch_json, fetch_source=_http_bytes, fetch_api=_http_json,
            token=None, workers=6):
    if type(workers) is not int or not 1 <= workers <= 8:
        raise ValueError("Invalid worker count")
    old, old_src = index_plugins(plugins), _source_map(origins)
    if not old or set(old) != set(old_src) or len(old_src) != len(origins):
        raise ValueError("Published snapshot identity mismatch")
    definitions = sources.get("sources")
    if not isinstance(definitions,list) or len({s["id"] for s in definitions}) != len(definitions):
        raise ValueError("Invalid source registry")
    registry = {s["id"]:s for s in definitions}
    if (review.get("schemaVersion") != 1 or not GIT_SHA.fullmatch(str(review.get("baselineCommit","")))
            or not isinstance(review.get("targets"),list)):
        raise ValueError("Invalid exact review registry")
    targets = {r["plugin"]:r for r in review["targets"]}
    if len(targets) != len(review["targets"]) or set(targets)-set(old):
        raise ValueError("Unknown or duplicate reviewed identity")
    order = list(old)
    def scan(items):
        with ThreadPoolExecutor(max_workers=workers) as pool:
            checks = dict(zip(order, pool.map(lambda k: safe_check(checker, items[k]), order)))
        for k, result in checks.items():
            result.update(plugin=k,url=items[k].get("url"))
        return checks
    original = scan(old)
    before = {k for k in order if exact_verified(old[k], original[k])}
    final, final_src = copy.deepcopy(old),copy.deepcopy(old_src)
    approvals, skipped, caches = {}, {}, {}
    for key, target in targets.items():
        if key in before:
            continue
        p, origin = old[key],old_src[key]
        try:
            if fingerprint(p) != target.get("old"):
                raise ValueError("Original fingerprint changed since review")
            source_id=target["sourceId"]
            source=registry[source_id]
            match=REPO.fullmatch(target["repository"])
            if (not match or origin.get("sourceId") != source_id
                    or source["repo"].casefold()!=target["repository"].casefold()
                    or origin.get("sourceRepository","").casefold()!=target["repository"].casefold()
                    or origin.get("sourceIndex")!=source["index"]
                    or not same_package_url(origin.get("packageUrl"),p["url"])
                    or origin.get("selectedVersion")!=p["version"]
                    or target["internalName"]!=p["internalName"]
                    or target["authors"]!=origin.get("originalAuthors")
                    or target["sourceConfigPath"]!=p["internalName"]+"/build.gradle.kts"):
                raise ValueError("Source identity or original provenance differs")
            b,s=target["buildCommit"],target["sourceCommit"]
            if not GIT_SHA.fullmatch(b) or not GIT_SHA.fullmatch(s):
                raise ValueError("Full immutable build and source commits required")
            replacement=target["replacement"]
            if (set(replacement)!=set(FINGERPRINT)
                    or type(replacement["version"]) is not int or replacement["version"]<p["version"]
                    or replacement["apiVersion"]!=p["apiVersion"]
                    or type(replacement["fileSize"]) is not int or replacement["fileSize"]<=0
                    or not DIGEST_RE.fullmatch(str(replacement["fileHash"]))
                    or replacement["fileHash"]==p["fileHash"]):
                raise ValueError("Invalid exact replacement fingerprint")
            kind=target["reviewKind"]
            if kind=="publisher_version_upgrade":
                if replacement["version"]<=p["version"] or replacement["status"]!=p["status"]:
                    raise ValueError("Version-only review does not authorize status change")
            elif kind=="publisher_status_upgrade":
                if (key!="onlineserietv" or replacement["version"]<=p["version"]
                        or (p["status"],replacement["status"])!=(0,1)):
                    raise ValueError("Unreviewed status transition")
            elif kind=="publisher_rebuild":
                if (source_id!="cakestwix" or replacement["version"]!=p["version"]
                        or replacement["status"]!=p["status"]):
                    raise ValueError("Unreviewed same-version rebuild")
            else:
                raise ValueError("Unknown review kind")
            pair="/".join(match.groups())
            raw="https://raw.githubusercontent.com/"+pair+"/"
            mutable=copy.deepcopy(replacement)
            mutable["url"]=mutable["url"].replace(" ","%20")
            info=parse_package_url(mutable["url"])
            if (not info or info["ref"]!="builds"
                    or (info["owner"]+"/"+info["repo"]).casefold()!=pair.casefold()):
                raise ValueError("Package outside reviewed publisher builds")
            pinned=pinned_url(info,b)
            index_url=raw+b+"/plugins.json"
            cache_key=(pair,b,s,target["buildRunId"])
            if cache_key not in caches:
                api="https://api.github.com/repos/"+pair
                commit=fetch_api(api+"/commits/"+b,token)
                if commit.get("sha")!=b or commit.get("commit",{}).get("message","").strip()!="Build "+s:
                    raise ValueError("Publisher build no longer binds reviewed source commit")
                if target["buildRunId"] is not None:
                    run=fetch_api(api+"/actions/runs/"+str(target["buildRunId"]),token)
                    if (run.get("head_sha")!=s or run.get("conclusion")!="success"
                            or run.get("name")!="Build"):
                        raise ValueError("Reviewed upstream build execution not confirmed")
                else:
                    if kind!="publisher_rebuild" or source_id!="cakestwix":
                        raise ValueError("Missing publisher build evidence")
                    workflow=fetch_source(raw+s+"/.forgejo/workflows/build.yml").decode("utf-8")
                    if not all(x in workflow for x in ('./gradlew make makePluginsJson','git commit --amend -m "Build $GITHUB_SHA"','git push --force')):
                        raise ValueError("Reviewed full-rebuild publication pattern no longer confirmed")
                caches[cache_key]=index_plugins(fetch_index(index_url))
            candidate=caches[cache_key].get(key)
            if (not candidate or fingerprint(candidate)!=replacement
                    or candidate.get("internalName")!=p["internalName"]
                    or candidate.get("authors")!=target["authors"]
                    or candidate.get("repositoryUrl")!=origin.get("originalRepositoryUrl")):
                raise ValueError("Pinned index differs from reviewed release")
            config=fetch_source(raw+s+"/"+quote(target["sourceConfigPath"],safe="/")).decode("utf-8")
            if declaration(config)!=(replacement["version"],replacement["status"],target["authors"]):
                raise ValueError("Source declaration differs from pinned release")
            m=dict(p,**{f:mutable[f] for f in EDIT_FIELDS})
            n=dict(m,url=pinned)
            if not exact_verified(m,safe_check(checker,m)) or not exact_verified(n,safe_check(checker,n)):
                raise ValueError("Independent mutable or pinned package bytes do not match reviewed digest")
            final[key]=n
            final_src[key]=dict(origin,packageUrl=pinned,selectedVersion=n["version"])
            approvals[key]=dict(target,pinnedUrl=pinned,pinnedIndexUrl=index_url,
                                separateBytesVerified=True,sourceDeclarationVerified=True)
        except Exception as exc:
            skipped[key]=type(exc).__name__+": "+str(exc)
    checks=scan(final)
    after={k for k in order if exact_verified(final[k],checks[k])}
    if not before.issubset(after) or not set(approvals).issubset(after):
        raise ValueError("Independent scan found changed or previously verified package failure")
    for k in order:
        if k in approvals:
            if (final[k]!=dict(old[k],**{f:final[k][f] for f in EDIT_FIELDS})
                    or final_src[k]!=dict(old_src[k],packageUrl=final[k]["url"],selectedVersion=final[k]["version"])):
                raise ValueError("Reviewed repair altered unrelated fields")
        elif final[k]!=old[k] or final_src[k]!=old_src[k]:
            raise ValueError("Non-reviewed entry changed")
    result=[final[k] for k in order]
    result_src=[final_src[str(o.get("plugin") or o.get("originalName")).strip().casefold()] for o in origins]
    residual=[{"plugin":k,"sourceId":old_src[k]["sourceId"],"url":final[k]["url"],
               "check":checks[k],"provenanceUrlMatches":same_package_url(final_src[k].get("packageUrl"),final[k].get("url")),
               "reason":skipped.get(k,"no exact reviewed replacement")} for k in order
              if k not in after or not same_package_url(final_src[k].get("packageUrl"),final[k].get("url"))]
    proof={"mode":MODE,"normalGuardedReleaseAuthorized":False,"repairEligible":bool(approvals),
           "publishedCount":len(plugins),"beforeHashMatches":len(before),"afterHashMatches":len(after),
           "fullByteIntegrityPassed":len(after)==len(plugins),"fullAuditPerformed":False,"previouslyVerifiedLost":[],
           "sourceCatalogDigest":canonical_sha256(plugins),"sourceProvenanceDigest":canonical_sha256(origins),
           "outputCatalogDigest":canonical_sha256(result),"outputProvenanceDigest":canonical_sha256(result_src),
           "reviewDigest":canonical_sha256(review),"sourceRegistryDigest":canonical_sha256(sources),
           "changed":[{"plugin":k,"sourceId":old_src[k]["sourceId"],"oldUrl":old[k]["url"],
                       "newUrl":final[k]["url"],"oldVersion":old[k]["version"],"version":final[k]["version"],
                       "oldStatus":old[k]["status"],"status":final[k]["status"],
                       "oldFileHash":old[k]["fileHash"],"fileHash":final[k]["fileHash"],
                       "oldFileSize":old[k]["fileSize"],"fileSize":final[k]["fileSize"],
                       "commitSha":approvals[k]["buildCommit"],"reviewKind":approvals[k]["reviewKind"],
                       "repairedFailure":True} for k in sorted(approvals)],
           "reviewAttestations":[approvals[k] for k in sorted(approvals)],
           "unresolved":residual,"checks":[checks[k] for k in order],
           "warning":"Exact individually reviewed publisher releases only. No standing trust expansion; no build reproducibility or playback claim. Remaining exceptions unchanged. Not a clean full audit."}
    return result,result_src,proof


def main(argv=None):
    cli=argparse.ArgumentParser(description=__doc__)
    for n in ("previous-dir","sources","review","output-dir"):
        cli.add_argument("--"+n,type=Path,required=True)
    args=cli.parse_args(argv)
    prev,dest=args.previous_dir.resolve(),args.output_dir.resolve()
    if dest.exists() or dest==prev or dest.is_relative_to(prev):
        cli.error("Output must be a new separate staging directory")
    read=lambda p:json.loads(p.read_text(encoding="utf-8"))
    plugins,origins,proof=prepare(read(prev/"plugins.json"),read(prev/"provenance.json"),
                                  read(args.sources),read(args.review),token=os.getenv("GH_TOKEN"))
    dest.mkdir(parents=True)
    if proof["repairEligible"]:
        statuses=probe_sources(origins,read(prev/"merge-report.json"),{r["plugin"]:r for r in proof["checks"]})
        bundle=make_bundle(plugins,origins,read(prev/"merge-report.json"),read(prev/"repo.json"),
                    (prev/"BUILD_HISTORY.md").read_text(),proof,statuses,
                    generated_at=datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
                    run_id=os.getenv("GITHUB_RUN_ID",""),code_sha=os.getenv("GITHUB_SHA",""))
        for name,value in bundle.items():
            (dest/name).write_text((json.dumps(value,indent=2,ensure_ascii=False)+"\n")
                               if not isinstance(value,str) else value+"\n",encoding="utf-8")
    else:
        (dest/"reviewed-repair-proof.json").write_text(json.dumps(proof,indent=2)+"\n")
    print(json.dumps({k:v for k,v in proof.items() if k not in ("checks","changed","unresolved","reviewAttestations")},indent=2))


if __name__=="__main__":
    main()
