"""Strict fail-closed test cases for 79-package read-only provenance verifier."""
import io
import json
import sys
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import probe_major_legacy_cohort as probe


def zipped():
    file = io.BytesIO()
    with zipfile.ZipFile(file, mode="w") as z:
        z.writestr("data", b"sample extension")
    return file.getvalue()


def make_case(*, mutable_changed=False, version=3, invalid_zip=False):
    repo, sha, name = "test/original", "a"*40, "Example"
    base = f"https://raw.githubusercontent.com/{repo}/"
    body = b"x"*len(zipped()) if invalid_zip else zipped()
    manifest = [{"internalName": name, "version": version, "fileSize": len(body), "status": 1}]
    published = [{"internalName": name, "version": 3, "fileSize": len(body),
                  "status": 1, "url": base + "builds/" + name + ".cs3"}]
    provenance = [{"plugin": name, "sourceId": "test"}]
    def get(url, limit):
        if url == base + sha + "/plugins.json":
            return json.dumps(manifest).encode()
        if url == base + sha + "/" + name + ".cs3":
            return body
        if url == base + "builds/" + name + ".cs3":
            return body[:-1] + b"!" if mutable_changed else body
        raise AssertionError("Unexpected URL: " + url)
    return ("test", repo, sha, "builds", 1), published, provenance, get


class CohortTests(unittest.TestCase):
    def test_verified_results_are_not_approvals(self):
        source, plugins, prov, get = make_case()
        with patch.object(probe, "SOURCES", (source,)):
            result = probe.analyze(plugins, prov, get, workers=1)
        self.assertEqual(result["expectedCount"], 1)
        self.assertEqual(result["verifiedCount"], 1)
        self.assertFalse(result["releaseAuthorized"])
        self.assertEqual(result["withheldCount"], 0)
        self.assertTrue(result["verified"][0]["byteIdenticalToPublished"])
        self.assertEqual(len(result["verified"][0]["sha256"]), 71)
        self.assertFalse(result["verified"][0]["releaseAuthorized"])

    def test_changed_mutable_bytes_are_withheld(self):
        source, plugins, prov, get = make_case(mutable_changed=True)
        with patch.object(probe, "SOURCES", (source,)):
            result = probe.analyze(plugins, prov, get, workers=1)
        self.assertEqual(result["verifiedCount"], 0)
        self.assertEqual(result["withheldCount"], 1)
        self.assertIn("not byte-identical", result["withheld"][0]["reason"])

    def test_upstream_version_drift_is_withheld(self):
        source, plugins, prov, get = make_case(version=4)
        with patch.object(probe, "SOURCES", (source,)):
            result = probe.analyze(plugins, prov, get, workers=1)
        self.assertEqual(result["withheldCount"], 1)
        self.assertIn("metadata", result["withheld"][0]["reason"])

    def test_invalid_zip_binary_is_withheld(self):
        source, plugins, prov, get = make_case(invalid_zip=True)
        with patch.object(probe, "SOURCES", (source,)):
            result = probe.analyze(plugins, prov, get, workers=1)
        self.assertEqual(result["withheldCount"], 1)
        self.assertIn("ZIP", result["withheld"][0]["reason"])

    def test_provenance_duplicates_fail_closed(self):
        source, plugins, prov, get = make_case()
        with patch.object(probe, "SOURCES", (source,)):
            with self.assertRaisesRegex(ValueError, "Duplicate provenance"):
                probe.analyze(plugins, prov + prov, get, workers=1)

    def test_unexpected_catalog_count_blocks_review(self):
        source, plugins, prov, get = make_case()
        source = (*source[:-1], 2)
        with patch.object(probe, "SOURCES", (source,)):
            with self.assertRaisesRegex(ValueError, "Unexpected legacy count"):
                probe.analyze(plugins, prov, get, workers=1)


if __name__ == "__main__":
    unittest.main()
