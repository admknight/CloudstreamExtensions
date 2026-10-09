import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
from validate_guarded_post_publish import validate_post_publish


def sample():
    plugins=[{"internalName":"One","url":"https://example/1.cs3"},
             {"internalName":"Two","url":"https://example/2.cs3"}]
    provenance=[{"plugin":"One","sourceId":"s"},
                {"plugin":"Two","sourceId":"s"}]
    production={
        "releaseEligible":True,"publicationMethod":"guarded_per_plugin_compatibility",
        "uniquePlugins":2,"changes":{"removed":0},
        "integrityHealth":{"unverifiedPreviousCarried":1},
        "deferredUnverified":[{"plugin":"Two"}]
    }
    audit={
        "publishedCount":2,"pass":False,
        "scan":{"mode":"all","checked":2},
        "sourceErrors":[],"metadataDrift":[{"plugin":"Two","type":"version"}],
        "packageProblems":[{"plugin":"Two","status":"mismatch"}]
    }
    return audit,production,plugins,provenance


class PostPublishTests(unittest.TestCase):
    def test_known_exception_stays_open_but_other_catalog_verified(self):
        result=validate_post_publish(*sample())
        self.assertEqual(result["knownAuditAnomalyCount"],2)
        self.assertFalse(result["upstreamProblemsFullyResolved"])
        self.assertFalse(result["fullAuditPassed"])
        self.assertEqual(result["unexpectedAuditAnomalyCount"],0)

    def test_novel_mismatched_plugin_is_not_silenced(self):
        data=sample()
        data[0]["packageProblems"].append({"plugin":"One","status":"mismatch"})
        with self.assertRaisesRegex(ValueError,"Unexpected integrity anomalies"):
            validate_post_publish(*data)

    def test_wrong_source_fails(self):
        data=sample()
        data[0]["sourceErrors"].append({"sourceId":"x","error":"unavailable"})
        with self.assertRaisesRegex(ValueError,"upstream index"):
            validate_post_publish(*data)

    def test_incomplete_scan_fails(self):
        data=sample()
        data[0]["scan"]["checked"]=1
        with self.assertRaisesRegex(ValueError,"did not complete"):
            validate_post_publish(*data)

    def test_old_manifest_missing_plugin_fails(self):
        data=sample()
        data[2].pop()
        with self.assertRaises(ValueError):
            validate_post_publish(*data)

    def test_source_provenance_mismatch_fails(self):
        data=sample()
        data[3][1]["plugin"]="Other"
        with self.assertRaisesRegex(ValueError,"inconsistent"):
            validate_post_publish(*data)

    def test_no_guards_fails(self):
        data=sample()
        data[1]["releaseEligible"]=False
        with self.assertRaisesRegex(ValueError,"not a complete"):
            validate_post_publish(*data)

    def test_prior_pinned_source_drift_is_expected_but_not_declared_resolved(self):
        audit,r,plugins,p=sample()
        r["deferredUnverified"]=[]
        r["integrityHealth"]["unverifiedPreviousCarried"]=0
        r["quarantineIncidents"]=[{
            "plugin":"One","disposition":"retained_immutable_previous",
            "fallbackVerified":True,"fallbackPin":"https://raw.githubusercontent.com/x/y/"+("a"*40)+"/One.cs3",
        }]
        audit["metadataDrift"]=[{"plugin":"One","reason":"commit-pinned previous bytes"}]
        audit["packageProblems"]=[]
        audit["pass"]=False
        result=validate_post_publish(audit,r,plugins,p)
        self.assertEqual(result["knownAuditAnomalyCount"],1)
        self.assertFalse(result["upstreamProblemsFullyResolved"])

    def test_corrupt_prior_pinned_package_remains_unexpected(self):
        audit,r,plugins,p=sample()
        r["deferredUnverified"]=[]
        r["integrityHealth"]["unverifiedPreviousCarried"]=0
        r["quarantineIncidents"]=[{
            "plugin":"One","disposition":"retained_immutable_previous",
            "fallbackVerified":True
        }]
        audit["metadataDrift"]=[]
        audit["packageProblems"]=[{"plugin":"One","status":"mismatch"}]
        audit["pass"]=False
        with self.assertRaisesRegex(ValueError,"Unexpected integrity"):
            validate_post_publish(audit,r,plugins,p)

    def test_no_known_exceptions_with_full_pass(self):
        audit,r,plugins,p=sample()
        r["deferredUnverified"]=[]
        r["integrityHealth"]["unverifiedPreviousCarried"]=0
        audit["metadataDrift"]=[]
        audit["packageProblems"]=[]
        audit["pass"]=True
        result=validate_post_publish(audit,r,plugins,p)
        self.assertTrue(result["upstreamProblemsFullyResolved"])
        self.assertTrue(result["fullAuditPassed"])

    def test_empty_metadata_drift_no_exceptions_does_not_mask_failed_audit(self):
        audit,r,plugins,p=sample()
        r["deferredUnverified"]=[]
        r["integrityHealth"]["unverifiedPreviousCarried"]=0
        audit["metadataDrift"]=[]
        audit["packageProblems"]=[]
        audit["pass"]=False
        with self.assertRaises(ValueError):
            validate_post_publish(audit,r,plugins,p)


if __name__=="__main__":
    unittest.main()
