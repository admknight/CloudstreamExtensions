"""Simulate Level-1 recovery permission using the real candidate-integrity gate."""
import hashlib
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import plan_integrity_recovery as recovery
import verify_candidate_integrity as integrity


def entry(version=1, source="demo", data=b"approved bytes"):
    return {
        "internalName": "Demo", "name": "Demo",
        "url": "https://raw.githubusercontent.com/demo/Demo.cs3",
        "version": version, "fileSize": len(data),
        "fileHash": "sha256-" + hashlib.sha256(data).hexdigest(),
        "status": 1,
    }


def report():
    return {"pass": False, "metadataDrift": [
        {"plugin": "Demo", "sourceId": "demo",
         "differences": {"version": {"published": 1, "upstream": 2}}}
    ], "packageProblems": [], "sourceErrors": []}


def provenance(source="demo"):
    return [{"plugin": "Demo", "sourceId": source}]


def checked(plugin):
    return {"status": "hash_verified", "actualFileSize": plugin["fileSize"],
            "actualFileHash": plugin["fileHash"]}


def verdict(candidate, previous, src="demo"):
    return integrity.gate(
        [candidate], [previous], provenance(src), provenance(), [],
        checker=checked, workers=1
    )


def decide(audit, gate, candidate, old, source="demo"):
    return recovery.decide_dispatch(
        audit, gate, [candidate], [old], provenance(source),
        {"candidateStatus": "READY", "sourceHealth": {"failed": 0}}
    )


class SafeDispatchTests(unittest.TestCase):
    def test_existing_trusted_digest_allows_same_url_version_update(self):
        old=entry(version=1)
        new=entry(version=2)
        gate=verdict(new,old)
        self.assertTrue(gate["passed"])
        d=decide(report(),gate,new,old)
        self.assertTrue(d["dispatch"])
        self.assertEqual(len(d["candidateDigest"]), 64)

    def test_changed_binary_hash_does_not_authorize_update(self):
        old=entry(version=1)
        new=entry(version=2,data=b"new untrusted release")
        gate=verdict(new,old)
        self.assertFalse(gate["passed"])
        self.assertFalse(decide(report(),gate,new,old)["dispatch"])

    def test_source_transfer_is_not_approved(self):
        old=entry(version=1)
        new=entry(version=2)
        gate=verdict(new,old,src="other")
        self.assertFalse(gate["passed"])
        self.assertFalse(decide(report(),gate,new,old,source="other")["dispatch"])

    def test_pass_from_incomplete_gate_never_dispatches(self):
        old=entry(version=1)
        new=entry(version=2)
        incomplete={"passed": True, "blockedCount":0, "checked":0}
        self.assertFalse(decide(report(),incomplete,new,old)["dispatch"])

    def test_missing_audited_drift_never_dispatches(self):
        old=entry(version=1)
        new=entry(version=2)
        gate=verdict(new,old)
        self.assertFalse(decide({"pass": True,"metadataDrift":[]},
                                gate,new,old)["dispatch"])

    def test_source_errors_block_dispatch(self):
        old=entry(version=1)
        new=entry(version=2)
        gate=verdict(new,old)
        result=recovery.decide_dispatch(
            report(),gate,[new],[old],provenance(),
            {"candidateStatus":"BLOCKED","sourceHealth":{"failed":1}}
        )
        self.assertFalse(result["dispatch"])


if __name__=="__main__":
    unittest.main()
