"""Offline tests for independent, URL-only published recovery planning."""
import copy
import hashlib
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

from index_immutable_recovery import index_recovery
from plan_published_repairs import plan_repairs

ROOT = "https://raw.githubusercontent.com/testowner/testrepo/"
SHA = "a" * 40


def fixture():
    data = b"old-bytes"
    digest = "sha256-" + hashlib.sha256(data).hexdigest()
    entries = [{"internalName": k, "name": k, "version": 1,
                "url": ROOT + "builds/" + k + ".cs3",
                "fileHash": digest, "fileSize": len(data)}
               for k in ("A", "B", "C")]
    origins = [{"plugin": p["internalName"], "sourceId": "test",
                "packageUrl": p["url"], "sourceIndex": ROOT + "builds/plugins.json",
                "sourceRepository": "https://github.com/testowner/testrepo"}
               for p in entries]
    store = {ROOT + SHA + "/A.cs3": data, ROOT + SHA + "/B.cs3": data,
             entries[1]["url"]: data, entries[0]["url"]: b"new-bytes"}
    inventory = index_recovery(entries, origins, lambda *a: SHA,
                               lambda *a: [], lambda u: store[u], workers=1)

    def checker(p):
        if p["url"] not in store:
            return {"status": "error"}
        raw = store[p["url"]]
        actual = "sha256-" + hashlib.sha256(raw).hexdigest()
        return {"status": "hash_verified" if actual == p["fileHash"] else "mismatch",
                "actualFileHash": actual, "actualFileSize": len(raw)}
    return entries, origins, inventory, store, checker


class RepairTests(unittest.TestCase):
    def test_partial_repair_and_protection_preserve_inputs(self):
        entries, origins, inventory, _, checker = fixture()
        old = copy.deepcopy((entries, origins, inventory))
        report = plan_repairs(entries, origins, inventory, checker=checker, workers=1)
        self.assertEqual((entries, origins, inventory), old)
        self.assertEqual(report["counts"], {"repair_verified": 1, "protect_verified": 1,
                                            "unchanged_verified": 0, "unresolved": 1})
        self.assertEqual(report["originalHashMatches"], 1)
        self.assertEqual(report["proposedHashMatches"], 2)
        self.assertFalse(report["fullByteIntegrityPassed"])
        self.assertFalse(report["autoPublicationAuthorized"])

    def test_stale_inventory_rejected(self):
        entries, origins, inventory, _, checker = fixture()
        entries[0]["version"] = 2
        with self.assertRaisesRegex(ValueError, "stale"):
            plan_repairs(entries, origins, inventory, checker=checker, workers=1)

    def test_pin_rechecked_not_inherited(self):
        entries, origins, inventory, store, checker = fixture()
        store[ROOT + SHA + "/A.cs3"] = b"bad-bytes"
        store[ROOT + SHA + "/B.cs3"] = b"bad-bytes"
        report = plan_repairs(entries, origins, inventory, checker=checker, workers=1)
        self.assertEqual(report["counts"]["repair_verified"], 0)
        self.assertEqual(report["counts"]["unchanged_verified"], 1)
        self.assertEqual(report["counts"]["unresolved"], 2)

    def test_failed_checker_is_accounted_for(self):
        entries, origins, inventory, _, _ = fixture()
        def failed(_):
            raise OSError("offline")
        report = plan_repairs(entries, origins, inventory, checker=failed, workers=1)
        self.assertEqual(report["counts"]["unresolved"], 3)
        self.assertEqual(report["proposedHashMatches"], 0)


if __name__ == "__main__":
    unittest.main()
