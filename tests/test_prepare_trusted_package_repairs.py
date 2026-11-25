"""Regression tests for trusted-existing failure-only recovery."""
import copy
import hashlib
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from prepare_trusted_package_repairs import prepare
from test_trusted_upstream_candidates import package, provenance, SOURCES, POLICY, URL, PIN, INDEX, SHA, REPO, DATA, HASH

AUTHOR = ["Example"]
OLD = b"old release"
PININDEX = INDEX.replace("/builds/", "/" + SHA + "/")


class TrustedRepairTests(unittest.TestCase):
    def setUp(self):
        self.old = package(version=1, content=OLD, apiVersion=1)
        self.origin = provenance(selectedVersion=1, originalAuthors=AUTHOR,
                                 originalRepositoryUrl=REPO)
        self.candidate = package(apiVersion=1, authors=AUTHOR, repositoryUrl=REPO)
        self.pinned = copy.deepcopy(self.candidate)
        self.storage = {URL: DATA, PIN: DATA}
        self.calls = []

    def check(self, p):
        self.calls.append(p["url"])
        b = self.storage.get(p["url"])
        if b is None:
            return {"status":"error"}
        h = "sha256-"+hashlib.sha256(b).hexdigest()
        return {"status":"hash_verified" if h==p["fileHash"] and len(b)==p["fileSize"] else "mismatch",
                "actualFileSize":len(b),"actualFileHash":h}

    def run_case(self, policy=POLICY):
        return prepare([self.old], [self.origin], SOURCES, policy, checker=self.check,
                       resolve=lambda *a:SHA,
                       fetch_index=lambda u:[self.candidate if u==INDEX else self.pinned], workers=1)

    def test_verified_new_release_repairs_failure_only(self):
        old = copy.deepcopy((self.old,self.origin))
        plugins, origins, proof = self.run_case()
        self.assertEqual((self.old,self.origin),old)
        self.assertEqual(plugins[0],dict(self.old,version=2,fileSize=len(DATA),fileHash=HASH,url=PIN))
        self.assertEqual(origins[0],dict(self.origin,packageUrl=PIN,selectedVersion=2))
        self.assertEqual(proof["beforeHashMatches"],0)
        self.assertEqual(proof["afterHashMatches"],1)
        self.assertEqual(len(proof["policyAttestations"]),1)
        self.assertGreaterEqual(self.calls.count(PIN),2)
        self.assertFalse(proof["normalGuardedReleaseAuthorized"])

    def test_trusted_bundle_does_not_mislabel_changed_binary(self):
        from prepare_published_url_repairs import make_bundle
        from unittest.mock import patch
        plugins, origins, proof = self.run_case()
        origins[0].update(sourceName="Example", category="Other")
        proof["outputProvenanceDigest"] = __import__("plan_per_plugin_selection").canonical_sha256(origins)
        statuses = [{"id":"example","name":"Example","ok":True,"includedCount":1}]
        prior = {"generatedAt":"2026-01-01", "candidateStatus":"READY",
                 "sourceHealth":{"ok":1,"failed":0},"sourceStatus":statuses}
        history = "# History\n\n| header |\n| --- |\n"
        with patch("prepare_published_url_repairs.template.build_status",return_value="## Source health"), \
             patch("prepare_published_url_repairs.template.build_readme",return_value="## 📊 Current dashboard"):
            bundle = make_bundle(plugins, origins, prior, {}, history, proof, statuses, generated_at="2026-11-26")
        self.assertIn("trusted-repair-proof.json",bundle)
        self.assertEqual(bundle["merge-report.json"]["integrityHealth"]["trustedExistingUpgrades"],1)
        self.assertEqual(bundle["merge-report.json"]["changeDetails"]["updated"][0]["fromVersion"],1)
        self.assertIn("v1 to v2",bundle["RELEASE_NOTES.md"])
        self.assertNotIn("No new binary or provider approved",bundle["STATUS.md"])

    def test_same_version_cannot_be_updated(self):
        self.candidate["version"]=1
        self.pinned["version"]=1
        p,o,proof=self.run_case()
        self.assertEqual(p,[self.old])
        self.assertFalse(proof["repairEligible"])

    def test_status_change_is_blocked(self):
        self.candidate["status"]=0
        self.assertFalse(self.run_case()[2]["repairEligible"])

    def test_pinned_author_mismatch_is_blocked(self):
        self.pinned["authors"]=["Unknown"]
        self.assertFalse(self.run_case()[2]["repairEligible"])

    def test_wrong_pinned_bytes_are_blocked(self):
        self.storage[PIN]=b"incorrect"
        self.assertFalse(self.run_case()[2]["repairEligible"])

    def test_no_upgrade_of_healthy_package(self):
        self.storage[URL]=OLD
        p,o,proof=self.run_case()
        self.assertEqual(p,[self.old])
        self.assertFalse(proof["repairEligible"])
        self.assertEqual(proof["beforeHashMatches"],1)

    def test_unlisted_source_cannot_be_promoted(self):
        p,o,proof=self.run_case({"schemaVersion":1,"rules":[]})
        self.assertEqual(p,[self.old])
        self.assertFalse(proof["repairEligible"])

    def test_fresh_scan_cannot_lose_approved_package(self):
        def changed(p):
            result=self.check(p)
            if p["url"]==PIN and self.calls.count(PIN)>1:
                return {"status":"error"}
            return result
        with self.assertRaisesRegex(ValueError,"Independent scan"):
            prepare([self.old],[self.origin],SOURCES,POLICY,checker=changed,
                    resolve=lambda *a:SHA,fetch_index=lambda u:[self.candidate],workers=1)


if __name__=="__main__":
    unittest.main()
