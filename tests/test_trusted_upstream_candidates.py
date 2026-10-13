import hashlib
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from trusted_upstream_candidates import promote
from verify_candidate_integrity import gate

SHA = "9" * 40
DATA = b"released package data"
HASH = "sha256-" + hashlib.sha256(DATA).hexdigest()
REPO = "https://github.com/Example/Extension"
INDEX = "https://raw.githubusercontent.com/Example/Extension/builds/plugins.json"
URL = "https://raw.githubusercontent.com/Example/Extension/builds/Demo.cs3"
PIN = "https://raw.githubusercontent.com/Example/Extension/" + SHA + "/Demo.cs3"


def package(version=2, url=URL, content=DATA, **overrides):
    d = {
        "name": "Demo", "internalName": "Demo", "version": version,
        "status": 1, "fileSize": len(content),
        "fileHash": "sha256-" + hashlib.sha256(content).hexdigest(),
        "url": url,
    }
    d.update(overrides)
    return d


def provenance(url=URL, **overrides):
    d = {"plugin": "Demo", "sourceId": "example",
         "sourceRepository": REPO, "sourceIndex": INDEX, "packageUrl": url}
    d.update(overrides)
    return d


SOURCES = {"sources": [{"id": "example", "repo": REPO, "index": INDEX}]}
POLICY = {"schemaVersion": 1, "rules": [{
    "sourceId": "example", "repository": REPO, "mode": "existing_upgrade_only"
}]}


def verify(item):
    # Separate calls made for unpinned and pinned URLs, as in production.
    return {"status": "hash_verified", "actualFileSize": len(DATA),
            "actualFileHash": HASH}


def stage(*, old=None, new=None, old_provenance=None, new_provenance=None,
          sources=None, policy=None, checker=verify, upstream=None):
    return promote(
        [new or package()], [old or package(version=1)],
        [new_provenance or provenance()], [old_provenance or provenance()],
        sources or SOURCES, policy or POLICY,
        resolve=lambda *args: SHA,
        fetch_index=lambda *args: [upstream or package()],
        checker=checker,
    )


class TrustedUpdateTests(unittest.TestCase):
    def test_valid_new_version_pinned_and_selected(self):
        plugins, origins, approvals, report = stage()
        self.assertEqual(report["promotedCount"], 1)
        self.assertEqual(plugins[0]["url"], PIN)
        self.assertEqual(origins[0]["packageUrl"], PIN)
        out = gate(plugins, [package(version=1)], origins, [provenance()],
                   approvals, checker=verify, workers=1)
        self.assertTrue(out["passed"], out["blocked"])
        self.assertEqual(out["trustedChanges"], 1)

    def test_no_automatic_trust_for_new_identity(self):
        plugins, origins, approvals, _ = promote(
            [package()], [], [provenance()], [], SOURCES, POLICY,
            resolve=lambda *args: SHA, fetch_index=lambda *args: [package()],
            checker=verify)
        self.assertFalse(approvals)

    def test_same_version_binary_replacement_is_blocked(self):
        plugins, origins, approvals, _ = stage(old=package(version=2, content=b"old"))
        self.assertFalse(approvals)
        self.assertEqual(plugins[0]["url"], URL)

    def test_publisher_mismatch_is_rejected(self):
        with self.assertRaises(ValueError):
            stage(policy={"schemaVersion": 1, "rules": [
                {"sourceId": "example", "repository": "https://github.com/evil/Extension",
                 "mode": "existing_upgrade_only"}]})

    def test_candidate_source_transfer_not_promoted(self):
        _, _, approvals, _ = stage(old_provenance=provenance(sourceId="other"))
        self.assertEqual(approvals, [])

    def test_publisher_index_conflict_not_promoted(self):
        _, _, approvals, report = stage(upstream=package(fileHash="sha256-"+"0"*64))
        self.assertFalse(approvals)
        self.assertEqual(len(report["skipped"]), 1)

    def test_bad_download_not_promoted(self):
        calls = []
        def check(item):
            calls.append(item["url"])
            if item["url"] == PIN:
                return {"status": "mismatch", "actualFileSize": len(DATA),
                        "actualFileHash": "sha256-" + "0"*64}
            return verify(item)
        _, _, approvals, _ = stage(checker=check)
        self.assertFalse(approvals)
        self.assertEqual(calls, [URL, PIN])

    def test_forged_policy_approval_not_accepted(self):
        plugins, origins, approvals, _ = stage()
        tampered = [dict(approvals[0], separateBytesVerified=False)]
        out = gate(plugins, [package(version=1)], origins, [provenance()],
                   tampered, checker=verify, workers=1)
        self.assertFalse(out["passed"])
        self.assertEqual(out["blockedCount"], 1)

    def test_unlisted_sources_do_not_promote(self):
        _, _, approvals, report = stage(policy={"schemaVersion":1, "rules":[]})
        self.assertFalse(approvals)
        self.assertEqual(report["policySources"], [])

    def test_downgrade_not_promoted(self):
        _, _, approvals, _ = stage(old=package(version=3))
        self.assertFalse(approvals)


if __name__ == "__main__":
    unittest.main()
