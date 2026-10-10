"""Offline trust and immutability regression checks for the recovery index."""
import copy
import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from index_immutable_recovery import (
    index_recovery, parse_package_url, pinned_url, compare_pinned_bytes,
    MAX_FILE_BYTES
)

HEAD = "a" * 40
EARLIER = "b" * 40
ORIGIN = "https://raw.githubusercontent.com/testowner/testrepo/builds/"


def plugin(name, payload=b"prior", **kwargs):
    value = {
        "internalName": name,
        "name": name,
        "version": 1,
        "url": ORIGIN + name + ".cs3",
        "fileSize": len(payload),
        "fileHash": "sha256-" + hashlib.sha256(payload).hexdigest(),
    }
    value.update(kwargs)
    return value


def src(item):
    return {"plugin": item["internalName"], "originalName": item["internalName"],
            "sourceId": "test",
            "packageUrl": item["url"],
            "sourceIndex": "https://raw.githubusercontent.com/testowner/testrepo/builds/plugins.json",
            "sourceRepository": "https://github.com/testowner/testrepo"}


def run(entries, stored, history=None, head=HEAD):
    calls = []
    def get(url):
        calls.append(url)
        if url not in stored:
            raise OSError("not found")
        return stored[url]
    r = index_recovery(
        entries, [src(x) for x in entries],
        lambda owner, repo, ref: head,
        lambda info, limit: history or [],
        get, max_history=4, workers=1,
    )
    return r, calls


class ParseTests(unittest.TestCase):
    def test_branch_style(self):
        result = parse_package_url(ORIGIN + "One.cs3")
        self.assertEqual(result, {"owner": "testowner", "repo": "testrepo",
                                  "ref": "builds", "path": "One.cs3"})

    def test_long_ref_style(self):
        result = parse_package_url(
            "https://raw.githubusercontent.com/testowner/testrepo/refs/heads/builds/One.cs3")
        self.assertEqual(result["ref"], "builds")
        self.assertEqual(result["path"], "One.cs3")

    def test_nested_relative_path(self):
        result = parse_package_url(ORIGIN + "lib/nested/Thing.cs3")
        self.assertEqual(result["path"], "lib/nested/Thing.cs3")
        self.assertEqual(pinned_url(result, HEAD), 
            "https://raw.githubusercontent.com/testowner/testrepo/" + HEAD + "/lib/nested/Thing.cs3")

    def test_reject_non_github_and_invalid_urls(self):
        for url in ("http://raw.githubusercontent.com/test/x/builds/P.cs3",
                    "https://notgithub.example/a/b/builds/P.cs3",
                    "https://raw.githubusercontent.com/a/b/builds/P.cs3?x=1",
                    "https://raw.githubusercontent.com/a/b/builds/P.cs3#anything",
                    "https://raw.githubusercontent.com/a/b/builds/P.zip",
                    "https://raw.githubusercontent.com/a/b/builds/../P.cs3",
                    "https://raw.githubusercontent.com/a/b/builds/P.cs3/.."):
            self.assertIsNone(parse_package_url(url), url)

    def test_commit_sha_requires_full_hash(self):
        with self.assertRaisesRegex(ValueError, "full immutable"):
            pinned_url(parse_package_url(ORIGIN+"P.cs3"), "v1")


class LockTests(unittest.TestCase):

    def test_orphan_revision_matches_previous_bytes_only(self):
        p = plugin("LK21", payload=b"old-published-bytes")
        historic = "3" * 40
        url = ORIGIN.replace("/builds/", "/" + historic + "/") + "LK21.cs3"
        result = index_recovery([p], [src(p)], lambda *a: HEAD,
            lambda info, limit: [], lambda u: b"old-published-bytes" if u == url else b"new-candidate",
            max_history=1, workers=1, supplemental_revisions={"lk21": [historic]})
        self.assertEqual(result["immutableMatches"], 1)
        self.assertEqual(result["locks"][0]["commitSha"], historic)
        self.assertFalse(result["autoPublicationAuthorized"])

    def test_orphan_revision_cannot_approve_different_binary(self):
        p = plugin("LK21", payload=b"old-published-bytes")
        historic = "3" * 40
        result = index_recovery([p], [src(p)], lambda *a: HEAD,
            lambda info, limit: [], lambda u: b"new-candidate",
            max_history=1, workers=1, supplemental_revisions={"lk21": [historic]})
        self.assertEqual(result["immutableMatches"], 0)
        self.assertEqual(result["unmatchedCount"], 1)

    def test_malformed_orphan_revision_rejected(self):
        p = plugin("LK21")
        with self.assertRaisesRegex(ValueError, "full Git commit"):
            index_recovery([p], [src(p)], lambda *a: HEAD,
                lambda info, limit: [], lambda u: b"prior",
                supplemental_revisions={"lk21": ["builds"]})

    def test_current_published_bytes_match_pinned_commit(self):
        p = plugin("P")
        url = ORIGIN.replace("/builds/", "/"+HEAD+"/") + "P.cs3"
        result, calls = run([p], {url:b"prior"})
        self.assertEqual(result["immutableMatches"], 1)
        self.assertEqual(result["matchedCurrentCommit"], 1)
        self.assertEqual(result["matchedPriorCommit"], 0)
        self.assertEqual(result["unmatchedCount"], 0)
        self.assertFalse(result["autoPublicationAuthorized"])
        self.assertEqual(result["locks"][0]["pinnedUrl"],url)
        self.assertFalse(result["locks"][0]["releaseAuthorVerified"])

    def test_mutated_latest_binary_recovers_exact_previous_hash_from_history(self):
        p = plugin("P")
        oldurl = ORIGIN.replace("/builds/", "/"+EARLIER+"/") + "P.cs3"
        headurl = ORIGIN.replace("/builds/", "/"+HEAD+"/") + "P.cs3"
        r, calls = run([p],{headurl:b"new-version",oldurl:b"prior"},[EARLIER])
        self.assertEqual(r["matchedPriorCommit"],1)
        self.assertTrue(r["locks"][0]["historyLookupUsed"])
        self.assertEqual(r["locks"][0]["fileHash"],p["fileHash"])
        self.assertIn(headurl,calls)
        self.assertIn(oldurl,calls)

    def test_changed_bytes_with_same_length_do_not_qualify(self):
        p = plugin("P")
        new=b"after"
        self.assertEqual(len(new),len(b"prior"))
        url=ORIGIN.replace("/builds/", "/"+HEAD+"/")+"P.cs3"
        result,_=run([p],{url:new})
        self.assertEqual(result["immutableMatches"],0)
        self.assertEqual(result["unmatchedReasons"]["published_bytes_not_found_in_bounded_immutable_history"],1)

    def test_missing_hash_does_not_create_forged_baseline(self):
        p=plugin("P",fileHash=None)
        r,calls=run([p],{})
        self.assertEqual(r["immutableMatches"],0)
        self.assertEqual(r["unmatchedReasons"]["published_without_sha256"],1)
        self.assertEqual(calls,[])

    def test_invalid_sha256_does_not_create_baseline(self):
        p=plugin("P",fileHash="sha256-not-a-hash")
        r,calls=run([p],{})
        self.assertEqual(r["unmatchedReasons"]["invalid_published_hash"],1)
        self.assertFalse(calls)

    def test_missing_size_is_excluded(self):
        p=plugin("P")
        del p["fileSize"]
        r,_=run([p],{})
        self.assertEqual(r["unmatchedReasons"]["invalid_published_size"],1)

    def test_reject_digest_from_new_candidate_not_prior_catalog(self):
        p=plugin("P",payload=b"old")
        newest=b"new"
        url=ORIGIN.replace("/builds/", "/"+HEAD+"/")+"P.cs3"
        result,_=run([p],{url:newest})
        self.assertEqual(result["immutableMatches"],0)

    def test_pinned_bytes_do_not_follow_unverified_change(self):
        p=plugin("P")
        original=p["fileHash"]
        url=ORIGIN.replace("/builds/", "/"+HEAD+"/")+"P.cs3"
        r,_=run([p],{url:b"prior"})
        self.assertEqual(r["locks"][0]["fileHash"],original)
        self.assertEqual(r["locks"][0]["originalUrl"],p["url"])
        self.assertEqual(p["url"],ORIGIN+"P.cs3")

    def test_provenance_mismatch_stops_source_pin(self):
        p=plugin("P")
        bad=src(p)
        bad["packageUrl"]="https://evil.example/plugin.cs3"
        result=index_recovery(
            [p],[bad],
            lambda *x: HEAD,lambda *x:[],
            lambda *x:b"prior",max_history=2,workers=1)
        self.assertEqual(result["unmatchedReasons"]["previous_provenance_package_url_mismatch"],1)
        self.assertEqual(result["immutableMatches"],0)

    def test_duplicate_plugin_identity_rejected(self):
        p=plugin("P")
        with self.assertRaisesRegex(ValueError,"duplicate"):
            index_recovery([p,copy.deepcopy(p)],[src(p)],
                           lambda *x:HEAD,lambda *x:[],lambda *x:b"prior")

    def test_no_resolvable_source_retains_failure_evidence(self):
        p=plugin("P")
        r=index_recovery(
            [p],[src(p)],
            lambda *x: (_ for _ in ()).throw(RuntimeError("GitHub 403")),
            lambda *x:[],lambda *x:b"prior",workers=1)
        self.assertEqual(r["immutableMatches"],0)
        self.assertEqual(r["unmatchedReasons"]["unresolvable_repository_commit"],1)

    def test_two_plugins_have_distinct_pins_and_total_reconciles(self):
        p1=plugin("P1")
        p2=plugin("P2", fileHash=None)
        url=ORIGIN.replace("/builds/", "/"+HEAD+"/")+"P1.cs3"
        r,_=run([p1,p2],{url:b"prior"})
        self.assertEqual(r["immutableMatches"]+r["unmatchedCount"],2)
        self.assertEqual(r["publishedCount"],2)
        self.assertEqual(r["locks"][0]["plugin"],"P1")

    def test_reject_bad_config_bounds(self):
        p=plugin("P")
        for limit in (-1,51):
            with self.assertRaisesRegex(ValueError,"history limit"):
                index_recovery([p],[src(p)],lambda *x:HEAD,
                               lambda *x:[],lambda *x:b"prior",
                               max_history=limit)
        with self.assertRaisesRegex(ValueError,"workers"):
            index_recovery([p],[src(p)],lambda *x:HEAD,
                           lambda *x:[],lambda *x:b"prior",workers=0)


if __name__=="__main__":
    unittest.main()
