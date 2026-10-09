import importlib.util
import hashlib
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(ROOT))
import plan_integrity_recovery as plan
import sync_integrity_issues as issues
import verify_candidate_integrity as gate


def plugin(version=1):
    return {"internalName": "Demo", "name": "Demo", "url": "https://raw.githubusercontent.com/test/Demo.cs3",
            "version": version, "fileSize": 10, "fileHash": "sha256-" + "a" * 64}


def incident():
    return {"pass": False, "scan": {"mode": "rotation"}, "publishedCount": 543,
            "metadataDrift": [{"plugin": "Demo", "sourceId": "trusted", "differences": {"version": {"published": 1, "upstream": 2}}}],
            "packageProblems": [{"plugin": "Demo", "sourceId": "trusted", "status": "mismatch", "reason": "fileSize"}],
            "sourceErrors": []}


class RecoveryPlanTests(unittest.TestCase):
    def test_overlapping_findings_are_one_incident(self):
        p = plan.plan(incident())
        self.assertEqual(len(p["incidents"]), 1)
        self.assertEqual(set(p["incidents"][0]["categories"]), {"metadata", "package"})
        self.assertTrue(p["candidatePreflightNeeded"])

    def test_unicode_and_spaces_have_stable_keys(self):
        self.assertEqual(plan.issue_key("Shakzz TV"), plan.issue_key("shakzz tv"))
        self.assertNotEqual(plan.issue_key("Shakzz TV"), plan.issue_key("Shakzz-TV"))

    def decision(self, audit=None, gate=None, source="trusted"):
        return plan.decide_dispatch(
            audit or incident(),
            gate or {"passed": True, "checked": 1, "blockedCount": 0},
            [plugin(version=2)], [plugin(version=1)],
            [{"plugin": "Demo", "sourceId": source}],
            {"candidateStatus": "READY", "sourceHealth": {"failed": 0}}
        )

    def test_dispatch_requires_full_valid_gate_and_same_provenance(self):
        self.assertTrue(self.decision()["dispatch"])
        self.assertEqual(len(self.decision()["candidateDigest"]), 64)
        self.assertFalse(self.decision(gate={"passed": False, "checked": 1, "blockedCount": 1})["dispatch"])
        self.assertFalse(self.decision(source="impersonator")["dispatch"])

    def test_full_gate_to_trusted_dispatch_end_to_end(self):
        old = plugin(version=1)
        digest = "sha256-" + hashlib.sha256(b"approved bytes").hexdigest()
        old["fileHash"] = digest
        new = dict(old, version=2)
        provenance = [{"plugin": "Demo", "sourceId": "trusted"}]
        check = lambda row: {"status": "hash_verified", "actualFileSize": 10,
                             "actualFileHash": digest}
        result = gate.gate([new], [old], provenance, provenance, [],
                           checker=check, workers=1)
        self.assertTrue(result["passed"])
        decision = plan.decide_dispatch(incident(), result, [new], [old],
                                        provenance, {"candidateStatus":"READY",
                                        "sourceHealth":{"failed":0}})
        self.assertTrue(decision["dispatch"])

    def test_new_untrusted_digest_cannot_trigger_recovery(self):
        old = plugin(version=1)
        new = dict(old, version=2, fileHash="sha256-"+"b"*64)
        provenance = [{"plugin":"Demo","sourceId":"trusted"}]
        result = gate.gate([new], [old], provenance, provenance, [],
                           checker=lambda row: {"status":"hash_verified",
                           "actualFileSize":10,"actualFileHash":new["fileHash"]},
                           workers=1)
        self.assertFalse(result["passed"])
        self.assertFalse(plan.decide_dispatch(incident(), result,
            [new],[old],provenance,{"candidateStatus":"READY",
            "sourceHealth":{"failed":0}})["dispatch"])

    def test_no_drift_never_dispatches(self):
        audit = incident()
        audit["metadataDrift"] = []
        self.assertFalse(self.decision(audit=audit)["dispatch"])


class IssueSyncTests(unittest.TestCase):
    def test_create_one_ticket_for_overlapping_incidents(self):
        calls = []
        def runner(cmd):
            calls.append(cmd)
            if cmd[2] == "list":
                return "[]"
            if cmd[2] == "create":
                return "https://github.com/example/issues/1"
            return ""
        changes = issues.sync(incident(), runner)
        self.assertEqual(len([x for x in calls if x[2] == "create"]), 1)
        self.assertEqual(changes[0]["action"], "created")

    def test_unchanged_incident_no_repeated_issue_writes(self):
        item = plan.plan(incident())["incidents"][0]
        existing = {"number": 42, "state": "OPEN",
                    "title": "[Integrity] " + item["key"],
                    "body": issues._body(item)}
        calls = []
        def runner(cmd):
            calls.append(cmd)
            if cmd[2] == "list":
                return json.dumps([existing])
            return ""
        updates = issues.sync(incident(), runner)
        self.assertEqual(updates[0]["action"], "unchanged")
        self.assertEqual(len(calls), 1)

    def test_only_full_success_resolves_prior_incidents(self):
        base = {"number": 12, "state": "OPEN", "title": "[Integrity] x",
                "body": issues.MARKER + "\nold"}
        def run(report):
            calls = []
            def runner(cmd):
                calls.append(cmd)
                return json.dumps([base]) if cmd[2] == "list" else ""
            issues.sync(report, runner)
            return calls
        rotation = {"pass": True, "scan": {"mode": "rotation"}, "publishedCount": 543}
        full = {"pass": True, "scan": {"mode": "all"}, "publishedCount": 543}
        self.assertFalse(any(x[2]=="close" for x in run(rotation)))
        self.assertTrue(any(x[2]=="close" for x in run(full)))
        self.assertFalse(any(x[2]=="close" for x in run({"pass":True,"scan":{"mode":"all"},"publishedCount":1})))


if __name__ == "__main__":
    unittest.main()
