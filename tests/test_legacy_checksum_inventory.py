"""Legacy SHA-256 inventory: no inferred approval from a downloaded checksum."""
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import legacy_checksum_inventory as inv


def sample():
    items = [
        {"internalName": "A", "version": 1, "fileSize": 123,
         "url": "https://raw.githubusercontent.com/owner/repo/builds/A.cs3"},
        {"internalName": "B", "version": 2, "fileSize": 100,
         "url": "https://raw.githubusercontent.com/owner/repo/" + "a"*40 + "/B.cs3"},
        {"internalName": "C", "version": 1, "fileSize": 25,
         "fileHash": "sha256-" + "c"*64,
         "url": "https://raw.githubusercontent.com/other/repo/builds/C.cs3"},
    ]
    provenance = [
        {"plugin": "A", "sourceId": "s", "sourceRepository": "https://github.com/owner/repo"},
        {"plugin": "B", "sourceId": "s", "sourceRepository": "https://github.com/owner/repo"},
        {"plugin": "C", "sourceId": "other", "sourceRepository": "https://github.com/other/repo"}
    ]
    report = {"uniquePlugins": 3, "generatedAt": "2026-10-09 21:47:09 UTC",
              "integrityHealth": {"sizeOnlyLegacyCandidateAccepted": 2}}
    return items, provenance, report


class InventoryTests(unittest.TestCase):
    def test_groups_and_immutable_classification(self):
        plugins, provenance, report = sample()
        old = copy.deepcopy(plugins)
        data = inv.analyze(plugins, provenance, report)
        self.assertEqual(data["legacyWithoutSha256Count"], 2)
        self.assertEqual(data["affectedSourceCount"], 1)
        self.assertEqual(data["groups"][0]["mutableUrls"], 1)
        self.assertEqual(data["groups"][0]["immutableUrls"], 1)
        self.assertFalse(data["releaseAuthorized"])
        self.assertTrue(data["readOnly"])
        self.assertTrue(all(x["authenticatedReleaseDigest"] is None for x in data["entries"]))
        self.assertEqual(plugins, old)

    def test_pinned_and_mutable_urls_are_distinct(self):
        self.assertTrue(inv.immutable_url(
            "https://raw.githubusercontent.com/owner/repo/" + "a"*40 + "/Demo.cs3"))
        self.assertFalse(inv.immutable_url(
            "https://raw.githubusercontent.com/owner/repo/builds/Demo.cs3"))
        self.assertFalse(inv.immutable_url("http://foo/x.cs3"))
        self.assertFalse(inv.immutable_url("https://foo.example/other.cs3"))

    def test_rejects_duplicate_identity_and_missing_provenance(self):
        plugins, prov, report = sample()
        with self.assertRaises(ValueError):
            inv.analyze(plugins, prov[:-1], report)
        bad = copy.deepcopy(plugins)
        bad[1]["internalName"] = "A"
        with self.assertRaisesRegex(ValueError, "identities"):
            inv.analyze(bad, prov, report)

    def test_rejects_malformed_digest_or_changed_report(self):
        plugins, prov, report = sample()
        bad = copy.deepcopy(plugins)
        bad[0]["fileHash"] = "sha256-not-valid"
        with self.assertRaisesRegex(ValueError, "Malformed"):
            inv.analyze(bad, prov, report)
        bad_report = copy.deepcopy(report)
        bad_report["integrityHealth"]["sizeOnlyLegacyCandidateAccepted"] = 0
        with self.assertRaisesRegex(ValueError, "count disagrees"):
            inv.analyze(plugins, prov, bad_report)

    def test_markdown_method_does_not_certify_packages(self):
        data = inv.analyze(*sample())
        body = inv.markdown(data)
        self.assertIn("Advisory evidence only", body)
        self.assertIn("Missing SHA-256", body)
        self.assertIn("| s | 2 | 1 | 1 |", body)

    def test_cli_does_not_replace_inputs(self):
        plugins, prov, report = sample()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name, obj in (("plugins", plugins), ("provenance", prov), ("report", report)):
                (root / (name + ".json")).write_text(json.dumps(obj))
            command = [sys.executable, str(ROOT / "tools/legacy_checksum_inventory.py"),
                       "--plugins", str(root / "plugins.json"),
                       "--provenance", str(root / "provenance.json"),
                       "--report", str(root / "report.json"),
                       "--json", str(root / "inventory.json"),
                       "--markdown", str(root / "inventory.md")]
            p = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(p.returncode, 0, p.stderr)
            self.assertEqual(json.loads((root / "inventory.json").read_text())["legacyWithoutSha256Count"], 2)
            self.assertEqual(json.loads((root / "plugins.json").read_text()), plugins)
            command[command.index(str(root / "inventory.json"))] = str(root / "plugins.json")
            p = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(p.returncode, 0)
            self.assertEqual(json.loads((root / "plugins.json").read_text()), plugins)


if __name__ == "__main__":
    unittest.main()
