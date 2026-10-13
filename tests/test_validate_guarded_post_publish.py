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
        "scan":{"mode":"all","checked":2,"failed":1},
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
        data[0]["scan"]["failed"]=2
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
        audit["scan"]["failed"]=0
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
        audit["scan"]["failed"]=0
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
        audit["scan"]["failed"]=0
        audit["pass"]=False
        with self.assertRaises(ValueError):
            validate_post_publish(audit,r,plugins,p)


    def test_blank_identity_fails_even_when_source_is_also_blank(self):
        audit,report,plugins,provenance=sample()
        plugins[0]["internalName"]=""
        provenance[0]["plugin"]=""
        with self.assertRaisesRegex(ValueError,"inconsistent"):
            validate_post_publish(audit,report,plugins,provenance)

    def test_duplicate_provenance_fails_even_when_identity_sets_match(self):
        audit,report,plugins,provenance=sample()
        provenance.append(dict(provenance[0]))
        with self.assertRaisesRegex(ValueError,"inconsistent"):
            validate_post_publish(audit,report,plugins,provenance)

    def test_unreported_package_failure_count_fails(self):
        audit,report,plugins,provenance=sample()
        audit["scan"]["failed"]=2
        with self.assertRaisesRegex(ValueError,"package-failure count"):
            validate_post_publish(audit,report,plugins,provenance)

    def test_missing_failed_scan_counter_fails(self):
        audit,report,plugins,provenance=sample()
        del audit["scan"]["failed"]
        with self.assertRaisesRegex(ValueError,"package-failure count"):
            validate_post_publish(audit,report,plugins,provenance)

    def test_missing_source_error_evidence_fails(self):
        audit,report,plugins,provenance=sample()
        del audit["sourceErrors"]
        with self.assertRaisesRegex(ValueError,"source-error evidence"):
            validate_post_publish(audit,report,plugins,provenance)


    def _policy_fixture(self):
        audit, report, plugins, provenance = sample()
        sha = "a" * 40
        url = "https://raw.githubusercontent.com/Example/Repo/" + sha + "/One.cs3"
        original = "https://raw.githubusercontent.com/Example/Repo/builds/One.cs3"
        plugins[0].update({"url":url,"version":3,"fileSize":123,"fileHash":"sha256-"+"e"*64})
        provenance[0].update({"sourceId":"trusted","packageUrl":url})
        report["trustedPolicyUpgrades"]=[{
            "authorizationKind":"trusted_upstream_policy_v1", "plugin":"one",
            "sourceId":"trusted","url":url,"originalUrl":original,
            "version":3,"fileSize":123,"fileHash":"sha256-"+"e"*64,
            "immutableCommit":sha,"pinnedIndexVerified":True,
            "separateBytesVerified":True
        }]
        audit["metadataDrift"].append({
            "plugin":"One","problem":"stale_metadata",
            "differences":{"url":{"published":url,"upstream":original}}
        })
        return audit,report,plugins,provenance

    def test_commit_pinned_policy_url_only_drift_is_expected(self):
        audit, report, plugins, provenance = self._policy_fixture()
        result=validate_post_publish(audit,report,plugins,provenance)
        self.assertEqual(result["trustedPolicyUpgrades"],["one"])
        self.assertEqual(result["knownAuditAnomalyCount"],3)
        self.assertFalse(result["fullAuditPassed"])

    def test_policy_cannot_excuse_new_package_checksum_mismatch(self):
        audit, report, plugins, provenance = self._policy_fixture()
        audit["packageProblems"].append({"plugin":"One","status":"mismatch"})
        audit["scan"]["failed"]=2
        with self.assertRaisesRegex(ValueError,"Unexpected integrity anomalies"):
            validate_post_publish(audit,report,plugins,provenance)

    def test_policy_cannot_excuse_version_drift(self):
        audit, report, plugins, provenance = self._policy_fixture()
        audit["metadataDrift"][-1]["differences"]["version"]={
            "published":3,"upstream":4
        }
        with self.assertRaisesRegex(ValueError,"Unexpected integrity anomalies"):
            validate_post_publish(audit,report,plugins,provenance)

    def test_policy_cannot_excuse_wrong_original_link(self):
        audit, report, plugins, provenance = self._policy_fixture()
        audit["metadataDrift"][-1]["differences"]["url"]["upstream"]="https://attacker/test"
        with self.assertRaisesRegex(ValueError,"Unexpected integrity anomalies"):
            validate_post_publish(audit,report,plugins,provenance)


if __name__=="__main__":
    unittest.main()
