"""Test exact reviewed repair scope and final non-regression."""
import copy
import hashlib
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
from prepare_reviewed_package_repairs import prepare, fingerprint, declaration
from test_trusted_upstream_candidates import package, provenance, SOURCES, URL, PIN, SHA, REPO, DATA, HASH

SOURCE="1"*40
INDEX=URL.rsplit("/",1)[0]+"/plugins.json"

class ReviewTests(unittest.TestCase):
    def setUp(self):
        self.old=package(version=1,content=b"old",apiVersion=1)
        self.origin=provenance(selectedVersion=1,originalAuthors=["Example"],originalRepositoryUrl=REPO)
        self.new=package(apiVersion=1,authors=["Example"],repositoryUrl=REPO)
        self.store={URL:DATA,PIN:DATA}
        self.review={"schemaVersion":1,"baselineCommit":"a"*40,"targets":[{
            "plugin":"demo","internalName":"Demo","sourceId":"example","repository":REPO,
            "buildCommit":SHA,"sourceCommit":SOURCE,"sourceConfigPath":"Demo/build.gradle.kts",
            "buildRunId":42,"reviewKind":"publisher_version_upgrade","authors":["Example"],
            "old":fingerprint(self.old),"replacement":fingerprint(self.new),"reason":"fixture"}]}

    def verify(self,p):
        data=self.store.get(p["url"])
        if data is None:return {"status":"error"}
        h="sha256-"+hashlib.sha256(data).hexdigest()
        return {"status":"hash_verified" if h==p["fileHash"] and len(data)==p["fileSize"] else "mismatch",
                "actualFileHash":h,"actualFileSize":len(data)}

    def api(self,url,token):
        if "/commits/" in url:return {"sha":SHA,"commit":{"message":"Build "+SOURCE}}
        return {"head_sha":SOURCE,"name":"Build","conclusion":"success"}

    def run_case(self,checker=None):
        return prepare([self.old],[self.origin],SOURCES,self.review,checker=checker or self.verify,
            fetch_index=lambda u:[self.new],fetch_source=lambda u:b'version = 2\ncloudstream {\n authors = listOf("Example")\n status = 1\n}\n',
            fetch_api=self.api,workers=1)

    def test_exact_reviewed_release(self):
        original=copy.deepcopy((self.old,self.origin,self.review))
        p,o,proof=self.run_case()
        self.assertEqual((self.old,self.origin,self.review),original)
        self.assertEqual(p[0],dict(self.old,version=2,fileHash=HASH,fileSize=len(DATA),url=PIN))
        self.assertEqual(o[0],dict(self.origin,packageUrl=PIN,selectedVersion=2))
        self.assertEqual(proof["beforeHashMatches"],0)
        self.assertEqual(proof["afterHashMatches"],1)
        self.assertFalse(proof["normalGuardedReleaseAuthorized"])

    def test_old_fingerprint_change_is_not_authorized(self):
        self.old["version"]=0
        self.assertFalse(self.run_case()[2]["repairEligible"])

    def test_different_pinned_index_digest_is_not_authorized(self):
        self.new["fileHash"]="sha256-"+"0"*64
        self.assertFalse(self.run_case()[2]["repairEligible"])

    def test_wrong_pinned_bytes_is_not_authorized(self):
        self.store[PIN]=b"wrong"
        self.assertFalse(self.run_case()[2]["repairEligible"])

    def test_status_change_requires_exact_review(self):
        self.new["status"]=0
        self.review["targets"][0]["replacement"]=fingerprint(self.new)
        self.assertFalse(self.run_case()[2]["repairEligible"])

    def test_healthy_identity_unchanged(self):
        self.store[URL]=b"old"
        self.assertFalse(self.run_case()[2]["repairEligible"])

    def test_independent_recheck_failure_blocks(self):
        count=0
        def check(p):
            nonlocal count
            if p["url"]==PIN:
                count+=1
                if count>1:return {"status":"error"}
            return self.verify(p)
        with self.assertRaisesRegex(ValueError,"Independent scan"):
            self.run_case(check)

    def test_declaration(self):
        self.assertEqual(declaration('version = 3\n authors = listOf("A", "B")\n status = 1 // current\n'),(3,1,["A","B"]))

if __name__=="__main__":
    unittest.main()
