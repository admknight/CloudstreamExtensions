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

    def test_source_is_hundred_nineteen_checked_in_immutable_packages(self):
        items = merge.fetch_source_plugins(self.source)
        self.assertEqual(len(items), 119)
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
        self.assertEqual(report["reviewApproved"], 119)
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

    def test_new_release_and_corrected_iptv_pin_are_exact(self):
        expected = {
            "StreamHubOne": {
                "version": 63,
                "size": 1099831,
                "hash": "sha256-d099a75fe33a918eea0500b08bdbeff34566b10664b9c84cb7c2e53e472b9a57",
                "url": "https://raw.githubusercontent.com/Faisal0786/Desi/"
                       "0af83282ff9d36e0cc7447582b7152cc948fd1cc/StreamHubOne.cs3",
            },
            "IPTVProvider": {
                "version": 9,
                "size": 32311,
                "hash": "sha256-8760295a88dd29011add8fa04e542209412100631dee948c18559e9e1acc565a",
                "url": "https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/"
                       "05b8e0b8c7b3aa43665fc57c477862cc0888f917/IPTVProvider.cs3",
            },
        }
        for name, item in expected.items():
            with self.subTest(plugin=name):
                plugin = next(p for p in self.pins if p["internalName"] == name)
                self.assertEqual(plugin["version"], item["version"])
                self.assertEqual(plugin["fileSize"], item["size"])
                self.assertEqual(plugin["fileHash"], item["hash"])
                self.assertEqual(plugin["url"], item["url"])
                records = [a for a in self.approvals if a["plugin"] == name]
                self.assertEqual(len(records), 1)
                self.assertEqual(records[0]["sourceId"], "local-pinned-recovery")
                self.assertEqual(records[0]["fileSize"], item["size"])
                self.assertEqual(records[0]["fileHash"], item["hash"])
        tearrs = next(x for x in merge.SOURCES if x["id"] == "tearrs-vietnamese")
        self.assertNotIn("IPTVProvider", tearrs.get("exclude", []))
        iptv = next(p for p in self.pins if p["internalName"] == "IPTVProvider")
        self.assertEqual(iptv["authors"], ["anhdaden"])
        self.assertEqual(iptv["tvTypes"], ["Live"])
        self.assertEqual(iptv["status"], 1)

    def test_four_raghav_binary_releases_have_original_immutable_commit(self):
        source_commit = "bbd6dcf1318d7c76bbf8c853ec120b857dd7df22"
        expected = {
            "AnimeTH": (1, 51648,
                "sha256-588900960340944467b3dcd9895425aca0db65df75f65b680be446293c0a7b2d"),
            "Anv": (1, 59454,
                "sha256-94a1716c3677bc0c2877d89634e7c5da66c39bc306b3dd34758f0e5a06f4c443"),
            "JustPlay": (12, 421355,
                "sha256-31d865e11f19b984e39baa48fa6234b9dd24f70c4a8ff9a1ca178a0dc56966e5"),
            "TorrentsV1": (22, 123332,
                "sha256-083368334ae440237fc0d799ea3dd087c782f46b177915b477267f7f326a05a1"),
        }
        for name, (version, size, digest) in expected.items():
            with self.subTest(plugin=name):
                item = next(p for p in self.pins if p["internalName"] == name)
                self.assertEqual(item["version"], version)
                self.assertEqual(item["fileSize"], size)
                self.assertEqual(item["fileHash"], digest)
                self.assertEqual(item["url"],
                    f"https://raw.githubusercontent.com/KSHITIJ8473/raghav/{source_commit}/{name}.cs3")
                reviews = [a for a in self.approvals if a["plugin"] == name]
                self.assertEqual(len(reviews), 1)
                self.assertEqual(reviews[0]["sourceId"], "local-pinned-recovery")
                self.assertEqual(reviews[0]["fileHash"], digest)
                self.assertEqual(reviews[0]["fileSize"], size)

    def test_nine_identical_legacy_binaries_now_have_exact_original_source_pins(self):
        expected = {
            "AltadefinizioneProvider": ("Gian-Fr/ItalianProvider", "e34c60c1139308f2265e57631abf2576addf73b0", 1, 10734),
            "GuardaSerieProvider": ("Gian-Fr/ItalianProvider", "e34c60c1139308f2265e57631abf2576addf73b0", 1, 10898),
            "BingedReview": ("rockhero1234/cinephile", "9aebfedfde585c7312edb1378ac287a6a847a59e", 3, 17968),
            "SkymoviesHD": ("rockhero1234/cinephile", "9aebfedfde585c7312edb1378ac287a6a847a59e", 1, 23971),
            "AniyomiProvider": ("CranberrySoup/AniyomiCompatExtension", "0a539fd454179399dd34b38ef5d4d3db49ce4971", 8, 36701),
            "MegaProvider": ("self-similarity/MegaRepo", "d8b64bab636845a89503469f1c3cb13fdc750a8a", 2, 6929),
            "DramaDrip": ("kim20598/cloudstream-extensions-test", "3e2792be0585090691a9d3421d902c29d7736c0f", 3, 43695),
            "IndianTVProvider": ("kim20598/cloudstream-extensions-test", "3e2792be0585090691a9d3421d902c29d7736c0f", 6, 21427),
            "UltimaBeta": ("kim20598/cloudstream-extensions-test", "3e2792be0585090691a9d3421d902c29d7736c0f", 7, 314672),
        }
        for name, (repo, revision, version, length) in expected.items():
            with self.subTest(name=name):
                item = next(p for p in self.pins if p["internalName"] == name)
                reviews = [a for a in self.approvals if a["plugin"] == name]
                self.assertEqual(len(reviews), 1)
                self.assertEqual(item["version"], version)
                self.assertEqual(item["fileSize"], length)
                self.assertEqual(item["url"],
                    f"https://raw.githubusercontent.com/{repo}/{revision}/{name}.cs3")
                self.assertEqual(item["fileHash"], reviews[0]["fileHash"])
                self.assertEqual(reviews[0]["fileSize"], length)
                self.assertEqual(reviews[0]["sourceId"], "local-pinned-recovery")
        self.assertEqual(next(p for p in self.pins if p["internalName"] == "UltimaBeta")["status"], 2)

    def test_all_79_major_legacy_upgrades_are_exact_original_commit_pins(self):
        original_sources = {
            "maarrem/cs-Kekik": ("51034df969e134e1347f4a3abe1e57063474a95f", 42),
            "errorcode26/Ayu-CloudStream-Games": ("3945b17e94a0176bf25ee527519ed427db2ce5ec", 21),
            "t23-02/cloudstream": ("72ea428321cd0f3749773933c42c9a1c3573ab25", 14),
            "nuyuls79/StreamPlay-movie": ("e065fad55d0acf955036102dddec92162c56f6ed", 2),
        }
        self.assertEqual(len(self.pins), 119)
        for repo, (commit, count) in original_sources.items():
            prefix = f"https://raw.githubusercontent.com/{repo}/{commit}/"
            records = [x for x in self.pins if x["url"].startswith(prefix)]
            with self.subTest(repo=repo):
                self.assertEqual(len(records), count)
                self.assertEqual(len({x["internalName"] for x in records}), count)
                for entry in records:
                    self.assertTrue(entry["url"].endswith("/"+entry["internalName"]+".cs3"))
                    self.assertEqual(len(entry["fileHash"]), 71)
                    approvals = [a for a in self.approvals if a["plugin"] == entry["internalName"]]
                    self.assertEqual(len(approvals), 1)
                    self.assertEqual(approvals[0]["url"], entry["url"])
                    self.assertEqual(approvals[0]["fileHash"], entry["fileHash"])
                    self.assertEqual(approvals[0]["fileSize"], entry["fileSize"])
                    self.assertEqual(approvals[0]["version"], entry["version"])

    def test_final_16_original_source_byte_identical_pins(self):
        sources = {
            "https://raw.githubusercontent.com/Asm0d3usX/CloudX-V2/"
              "c73809693bc8406a6f8cffd98039e8278e92a95a/": 13,
            "https://raw.githubusercontent.com/RowdyRushya/rowdy-cs-extensions/"
              "0143d69ce3ec9b7cf09f6d6216212694dc5890a0/": 1,
            "https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/"
              "05b8e0b8c7b3aa43665fc57c477862cc0888f917/": 6,
        }
        # GitLab already had 4 pinned packages before the last two legacy upgrades.
        for prefix, count in sources.items():
            with self.subTest(prefix=prefix):
                records = [p for p in self.pins if p["url"].startswith(prefix)]
                self.assertEqual(len(records), count)
                for item in records:
                    reviews = [x for x in self.approvals if x["plugin"]==item["internalName"]]
                    self.assertEqual(len(reviews), 1)
                    self.assertEqual(reviews[0]["fileHash"], item["fileHash"])
                    self.assertEqual(reviews[0]["fileSize"], item["fileSize"])
                    self.assertEqual(reviews[0]["url"], item["url"])
        self.assertEqual(next(x for x in self.pins if x["internalName"]=="Rowdy")["version"], 21)
        self.assertEqual(next(x for x in self.pins if x["internalName"]=="MonPlayerProvider")["authors"], ["anhdaden"])
        self.assertEqual(next(x for x in self.pins if x["internalName"]=="SyncProvider")["name"], "Cross-Device Sync")

    def test_rejects_mutable_package_url(self):
        copied = copy.deepcopy(self.pins)
        donghub = next(x for x in copied if x["internalName"] == "Donghub")
        donghub["url"] = donghub["url"].replace(
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
