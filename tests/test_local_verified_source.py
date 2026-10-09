"""Regression checks for the reviewed, locally pinned CloudStream source."""
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import merge_upstreams as merge
from verify_candidate_integrity import approved, gate


class LocalVerifiedSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = next(s for s in merge.SOURCES if s["id"] == "local-pinned-recovery")
        cls.pins = json.loads((ROOT / "local_verified_plugins.json").read_text())
        cls.approvals = json.loads((ROOT / "trusted_binary_approvals.json").read_text())

    def test_source_is_nine_checked_in_immutable_packages(self):
        items = merge.fetch_source_plugins(self.source)
        self.assertEqual(len(items), 9)
        self.assertEqual({p["internalName"] for p in items},
                         set(self.source["include"]))
        self.assertEqual(self.source["priority"], 0)
        self.assertTrue(all(len(p["fileHash"]) == 71 for p in items))

    def test_each_pin_has_one_exact_local_review(self):
        self.assertEqual(len(self.approvals), len(self.pins))
        for item in self.pins:
            with self.subTest(plugin=item["internalName"]):
                self.assertTrue(approved(
                    item, "local-pinned-recovery",
                    {"actualFileHash": item["fileHash"],
                     "actualFileSize": item["fileSize"]},
                    self.approvals
                ))
                self.assertTrue(
                    any(a["plugin"] == item["internalName"] and
                        a["evidenceUrl"].startswith("https://") for a in self.approvals)
                )

    def test_local_packages_pass_only_with_exact_reviewed_digests(self):
        provenance = [{"plugin":p["internalName"], "sourceId":"local-pinned-recovery",
                       "packageUrl":p["url"]} for p in self.pins]
        def check(item):
            trusted = next((p for p in self.pins
                            if p["internalName"] == item["internalName"]), None)
            if not trusted or item["fileHash"] != trusted["fileHash"] or item["fileSize"] != trusted["fileSize"]:
                return {"status":"mismatch", "reason":"fileHash"}
            return {"status":"hash_verified", "actualFileHash":trusted["fileHash"],
                    "actualFileSize":trusted["fileSize"]}
        report = gate(self.pins, [], provenance, [], self.approvals, checker=check, workers=1)
        self.assertEqual(report["blockedCount"], 0)
        self.assertEqual(report["reviewApproved"], 9)
        altered = copy.deepcopy(self.pins)
        altered[0]["fileHash"] = "sha256-" + "0"*64
        bad = gate(altered, [], provenance, [], self.approvals, checker=check, workers=1)
        self.assertEqual(bad["blockedCount"], 1)

    def test_netmovie_first_release_is_commit_pinned_and_scoped(self):
        pkg = next(x for x in self.pins if x["internalName"] == "NetMovie")
        self.assertEqual(pkg["version"], 1)
        self.assertEqual(pkg["fileSize"], 32088)
        self.assertEqual(pkg["fileHash"],
            "sha256-27320e91111d0a371614373dcd747baee5c4d3c5c4a6202de93c45cf9e44d576")
        self.assertEqual(pkg["url"],
            "https://raw.githubusercontent.com/Faisal0786/Desi/"
            "0af83282ff9d36e0cc7447582b7152cc948fd1cc/NetMovie.cs3")
        reviews = [x for x in self.approvals if x["plugin"] == "NetMovie"]
        self.assertEqual(len(reviews), 1)
        self.assertEqual(reviews[0]["sourceId"], "local-pinned-recovery")
        self.assertEqual(reviews[0]["fileHash"], pkg["fileHash"])

    def test_rejects_mutable_package_url(self):
        copied = copy.deepcopy(self.pins)
        copied[0]["url"] = copied[0]["url"].replace(
            "/ff22707cca6b1292d327a9aa9dcc60de5ee12492/", "/builds/"
        )
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "local_verified_plugins.json"
            path.write_text(json.dumps(copied))
            with patch.object(merge, "ROOT", Path(td)):
                with self.assertRaisesRegex(ValueError, "non-immutable"):
                    merge.fetch_source_plugins(self.source)

    def test_rejects_duplicate_or_missing_local_pin(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "local_verified_plugins.json"
            path.write_text(json.dumps(self.pins[:-1]))
            with patch.object(merge, "ROOT", Path(td)):
                with self.assertRaisesRegex(ValueError, "differ"):
                    merge.fetch_source_plugins(self.source)
            path.write_text(json.dumps(self.pins + [self.pins[0]]))
            with patch.object(merge, "ROOT", Path(td)):
                with self.assertRaisesRegex(ValueError, "Invalid"):
                    merge.fetch_source_plugins(self.source)

    def test_rejects_arbitrary_local_path(self):
        bad = dict(self.source, localFile="../secrets.json")
        with self.assertRaisesRegex(ValueError, "Unrecognized local source"):
            merge.fetch_source_plugins(bad)


if __name__ == "__main__":
    unittest.main()
