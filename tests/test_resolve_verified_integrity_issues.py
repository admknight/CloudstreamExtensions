"""Full-audit verified per-plugin issue resolution: fail closed with open upstream holds."""
import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from plan_integrity_recovery import issue_key
from resolve_verified_integrity_issues import (
    validate_proven_resolution, resolve, MANAGED
)


def fixture():
    plugins = [
        {"internalName": "P"+str(i), "name": "P"+str(i),
         "url": "https://raw.githubusercontent.com/demo/repo/builds/P"+str(i)+".cs3",
         "version": 1, "fileHash": "sha256-" + "a"*64, "fileSize": 10}
        for i in range(50)
    ]
    plugins[2].pop("fileHash")
    origins = [
        {"plugin": p["internalName"], "sourceId": "demo",
         "packageUrl": p["url"]}
        for p in plugins
    ]
    report = {
        "releaseEligible": True,
        "publicationMethod": "guarded_per_plugin_compatibility",
        "uniquePlugins": 50,
        "changes": {"removed": 0},
        "deferredUnverified": [{"plugin":"P0"}],
        "integrityHealth": {"unverifiedPreviousCarried":1,"pinnedPreviousRecovered":0},
        "quarantineIncidents": [{"plugin":"P0", "disposition":"quarantined_existing"}]
    }
    audit = {
        "publishedCount": 50, "pass": False,
        "scan": {"mode":"all", "checked":50},
        "sourceErrors": [],
        "metadataDrift": [{"plugin":"P0", "sourceId":"demo","reason":"upstream changed"}],
        "packageProblems": [{"plugin":"P0", "status":"mismatch","sourceId":"demo"}],
    }
    verdict = {
        "result":"GUARDED_PUBLICATION_VERIFIED_WITH_KNOWN_OPEN_EXCEPTIONS",
        "unexpectedAuditAnomalyCount":0,
        "knownAuditAnomalyCount":2,
        "heldUpstreamExceptions":["p0"],
        "upstreamProblemsFullyResolved":False,
        "fullAuditPassed":False
    }
    return audit, verdict, report, plugins, origins


def issue(name, number, state="OPEN", managed=True):
    return {
        "number":number,
        "title":"[Integrity] " + issue_key(name),
        "state":state,
        "body":MANAGED+"\n**Maintainer evidence and investigation retained**" if managed else "Private notes"
    }


class ResolutionTests(unittest.TestCase):
    def test_only_independently_verified_nonheld_sha_entries_eligible(self):
        eligible=validate_proven_resolution(*fixture())
        self.assertEqual(len(eligible),48)
        self.assertNotIn("p0",eligible)
        self.assertNotIn("p2",eligible)
        self.assertIn("p1",eligible)

    def test_close_only_resolved_managed_issues_with_audit_link(self):
        data=fixture()
        initial=[issue("P0",18),issue("P1",19),issue("P2",20),
                 issue("P3",21,managed=False),issue("P4",22,state="CLOSED")]
        calls=[]
        def runner(command):
            calls.append(command)
            return json.dumps(initial) if command[2]=="list" else ""
        result=resolve(*data,verifier_run_id=37978659426,runner=runner)
        self.assertEqual(len(result["closedIssues"]),1)
        self.assertEqual(result["closedIssues"][0]["issue"],19)
        self.assertEqual([x[2] for x in calls],["list","comment","close"])
        self.assertIn("runs/37978659426",calls[1][-1])
        self.assertIn("runtime playback",calls[1][-1])
        self.assertEqual(calls[2][-1],"completed")

    def test_no_eligible_open_issue_has_no_mutations(self):
        data=fixture()
        calls=[]
        def runner(command):
            calls.append(command)
            return json.dumps([issue("P0",18),issue("P2",20)]) if command[2]=="list" else ""
        result=resolve(*data,verifier_run_id=12,runner=runner)
        self.assertEqual(result["closedIssues"],[])
        self.assertEqual(len(calls),1)

    def test_incomplete_or_rotating_audit_never_closes(self):
        for mode,checked in [("rotation",8),("all",1)]:
            data=fixture()
            data[0]["scan"]={"mode":mode,"checked":checked}
            with self.subTest(mode=mode,checked=checked),self.assertRaisesRegex(ValueError,"Incomplete"):
                validate_proven_resolution(*data)

    def test_no_sha256_never_considered_valid_recovery(self):
        data=fixture()
        data[3][1]["fileHash"]=None
        self.assertNotIn("p1",validate_proven_resolution(*data))

    def test_wrong_deferred_accounting_rejected(self):
        data=fixture()
        data[2]["integrityHealth"]["unverifiedPreviousCarried"]=0
        with self.assertRaisesRegex(ValueError,"Deferred"):
            validate_proven_resolution(*data)

    def test_forged_verdict_rejected(self):
        data=fixture()
        data[1]["knownAuditAnomalyCount"]=0
        with self.assertRaisesRegex(ValueError,"does not match"):
            validate_proven_resolution(*data)

    def test_new_unaccounted_binary_mismatch_blocks_every_close(self):
        data=fixture()
        data[0]["packageProblems"].append({"plugin":"P1","status":"mismatch"})
        with self.assertRaisesRegex(ValueError,"Unexpected unapproved"):
            validate_proven_resolution(*data)

    def test_deferred_plugin_remains_open_even_with_valid_declared_sha(self):
        eligible=validate_proven_resolution(*fixture())
        self.assertNotIn("p0",eligible)

    def test_missing_upstream_source_causes_failure(self):
        data=fixture()
        data[0]["sourceErrors"]=[{"sourceId":"demo","error":"outage"}]
        with self.assertRaisesRegex(ValueError,"Incomplete"):
            validate_proven_resolution(*data)

    def test_snapshot_identity_and_provenance_must_reconcile(self):
        data=fixture()
        data[4].pop()
        with self.assertRaisesRegex(ValueError,"coverage"):
            validate_proven_resolution(*data)

    def test_no_closed_issue_if_published_integrity_is_unsigned(self):
        data=fixture()
        data[3][1]["fileHash"]="sha256-"+"z"*64
        self.assertNotIn("p1",validate_proven_resolution(*data))

    def test_untrusted_new_source_id_never_closes(self):
        data=fixture()
        data[4][1]["sourceId"]=""
        self.assertNotIn("p1",validate_proven_resolution(*data))

    def test_pinned_previous_metadata_drift_does_not_close_issue(self):
        data=fixture()
        data[2]["deferredUnverified"]=[]
        data[2]["integrityHealth"]["unverifiedPreviousCarried"]=0
        data[2]["integrityHealth"]["pinnedPreviousRecovered"]=1
        data[2]["quarantineIncidents"]=[{
            "plugin":"P0","disposition":"retained_immutable_previous",
            "fallbackVerified":True,"fallbackThroughRecoveryLock":True,
        }]
        data[0]["packageProblems"]=[]
        data[1]["knownAuditAnomalyCount"]=1
        data[1]["heldUpstreamExceptions"]=[]
        self.assertIn("p1",validate_proven_resolution(*data))
        self.assertNotIn("p0",validate_proven_resolution(*data))

    def test_pinned_previous_binary_corruption_rejected(self):
        data=fixture()
        data[2]["deferredUnverified"]=[]
        data[2]["integrityHealth"]["unverifiedPreviousCarried"]=0
        data[2]["integrityHealth"]["pinnedPreviousRecovered"]=1
        data[2]["quarantineIncidents"]=[{
            "plugin":"P0","disposition":"retained_immutable_previous",
            "fallbackVerified":True,"fallbackThroughRecoveryLock":True,
        }]
        data[0]["metadataDrift"]=[]
        data[1]["knownAuditAnomalyCount"]=1
        data[1]["heldUpstreamExceptions"]=[]
        with self.assertRaisesRegex(ValueError,"Unexpected unapproved"):
            validate_proven_resolution(*data)

    def test_completed_clean_report_resolves_all_hashed_plugins(self):
        data=fixture()
        data[0]["metadataDrift"]=[]
        data[0]["packageProblems"]=[]
        data[0]["pass"]=True
        data[1].clear()
        data[1].update({
            "result":"GUARDED_PUBLICATION_VERIFIED_NO_DEFERRED_EXCEPTIONS",
            "unexpectedAuditAnomalyCount":0,
            "knownAuditAnomalyCount":0,
            "heldUpstreamExceptions":[],
            "upstreamProblemsFullyResolved":True,
            "fullAuditPassed":True
        })
        data[2]["deferredUnverified"]=[]
        data[2]["integrityHealth"]["unverifiedPreviousCarried"]=0
        self.assertEqual(len(validate_proven_resolution(*data)),49)

    def test_bad_issue_run_id_rejected(self):
        with self.assertRaisesRegex(ValueError,"run ID"):
            resolve(*fixture(),verifier_run_id=0,runner=lambda x:"[]")


if __name__=="__main__":
    unittest.main()
