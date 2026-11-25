"""Regression coverage for the separate, non-regressing repair transaction."""
import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from test_plan_published_repairs import fixture, ROOT, SHA
from plan_published_repairs import plan_repairs
from prepare_published_url_repairs import prepare


class TransactionTests(unittest.TestCase):
    def setUp(self):
        self.plugins, self.origins, self.inventory, self.store, self.checker = fixture()
        self.plan = plan_repairs(self.plugins, self.origins, self.inventory,
                                 checker=self.checker, workers=1)

    def run_prepare(self):
        return prepare(self.plugins, self.origins, self.inventory, self.plan,
                       checker=self.checker, workers=1)

    def test_exact_url_delta_with_remaining_failure(self):
        previous = copy.deepcopy((self.plugins, self.origins, self.inventory, self.plan))
        new, origins, proof = self.run_prepare()
        self.assertEqual(previous, (self.plugins, self.origins, self.inventory, self.plan))
        self.assertEqual(new[0], dict(self.plugins[0], url=ROOT+SHA+"/A.cs3"))
        self.assertEqual(origins[0], dict(self.origins[0], packageUrl=new[0]["url"]))
        self.assertEqual(new[2], self.plugins[2])
        self.assertEqual(origins[2], self.origins[2])
        self.assertEqual(proof["beforeHashMatches"], 1)
        self.assertEqual(proof["afterHashMatches"], 2)
        self.assertFalse(proof["fullByteIntegrityPassed"])
        self.assertFalse(proof["normalGuardedReleaseAuthorized"])
        self.assertEqual([r["plugin"] for r in proof["unresolved"]], ["c"])

    def test_wrong_pin_rejected(self):
        self.plan["rows"][0]["proposedUrl"] = ROOT + "b"*40 + "/A.cs3"
        with self.assertRaisesRegex(ValueError, "Invalid immutable"):
            self.run_prepare()

    def test_changed_bytes_rejected_after_planning(self):
        self.store[ROOT+SHA+"/A.cs3"] = b"different"
        with self.assertRaisesRegex(ValueError, "Independent recheck"):
            self.run_prepare()

    def test_previously_healthy_cannot_regress(self):
        self.store[ROOT+SHA+"/B.cs3"] = b"different"
        with self.assertRaisesRegex(ValueError, "Independent recheck"):
            self.run_prepare()

    def test_plan_metadata_tampering_rejected(self):
        self.plan["rows"][0]["version"] = 2
        with self.assertRaisesRegex(ValueError, "differs"):
            self.run_prepare()

    def test_plan_snapshot_tampering_rejected(self):
        self.plugins[0]["version"] = 2
        with self.assertRaises(ValueError):
            self.run_prepare()


if __name__ == "__main__":
    unittest.main()
