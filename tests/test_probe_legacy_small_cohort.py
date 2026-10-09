"""Check that evidence requires exact upstream commit and mutable-byte equality."""
import io
import json
import sys
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import probe_legacy_small_cohort as probe


def archive():
    out = io.BytesIO()
    with zipfile.ZipFile(out, mode="w") as z:
        z.writestr("manifest.txt", b"immutable upstream release")
    return out.getvalue()


class EvidenceTests(unittest.TestCase):
    def data(self, changed=False, version=None, digest=None):
        repo, revision, name = "example/Original", "a"*40, "Example"
        blob = archive()
        root = f"https://raw.githubusercontent.com/{repo}/"
        manifest = [{"internalName": name, "version": 3 if version is None else version,
                     "fileSize": len(blob), "status": 1,
                     **({"fileHash": digest} if digest else {})}]
        published = [{"internalName": name, "version": 3, "fileSize": len(blob),
                      "status": 1, "url": root + "builds/" + name + ".cs3"}]
        def get(url):
            if url == root + revision + "/plugins.json":
                return json.dumps(manifest).encode()
            if url == root + revision + "/" + name + ".cs3":
                return blob
            if url == root + "builds/" + name + ".cs3":
                return blob[:-1] + b"x" if changed else blob
            raise AssertionError("Unexpected URL: " + url)
        return (repo, revision, (name,)), published, get

    def test_exact_original_and_live_bytes_generate_advisory_digest(self):
        cohort, published, get = self.data()
        with patch.object(probe, "COHORT", (cohort,)):
            out = probe.analyze(published, get)
        self.assertEqual(out["cohortSize"], 1)
        self.assertFalse(out["releaseAuthorized"])
        item = out["packages"][0]
        self.assertFalse(item["releaseAuthorized"])
        self.assertTrue(item["immutableMatchesCurrentPublishedBytes"])
        self.assertEqual(len(item["sha256"]), 71)
        self.assertEqual(item["revision"], "a"*40)

    def test_change_in_mutable_bytes_prevents_verification(self):
        cohort, published, get = self.data(changed=True)
        with patch.object(probe, "COHORT", (cohort,)):
            with self.assertRaisesRegex(ValueError, "differs"):
                probe.analyze(published, get)

    def test_upstream_version_mismatch_blocks_cohort(self):
        cohort, published, get = self.data(version=4)
        with patch.object(probe, "COHORT", (cohort,)):
            with self.assertRaisesRegex(ValueError, "metadata differ"):
                probe.analyze(published, get)

    def test_fake_original_sha256_is_rejected(self):
        cohort, published, get = self.data(digest="sha256-" + "0"*64)
        with patch.object(probe, "COHORT", (cohort,)):
            with self.assertRaisesRegex(ValueError, "hash disagrees"):
                probe.analyze(published, get)

    def test_invalid_nonzip_is_rejected(self):
        cohort, published, get = self.data()
        def corrupt(url):
            if url.endswith(".cs3"):
                return b"a"*len(archive())
            return get(url)
        with patch.object(probe, "COHORT", (cohort,)):
            with self.assertRaisesRegex(ValueError, "differs"):
                probe.analyze(published, corrupt)

    def test_missing_original_release_is_rejected(self):
        cohort, published, get = self.data()
        with patch.object(probe, "COHORT", ((cohort[0], cohort[1], ("NoSuchProvider",)),)):
            with self.assertRaisesRegex(ValueError, "Missing/duplicate"):
                probe.analyze(published, get)


if __name__ == "__main__":
    unittest.main()
