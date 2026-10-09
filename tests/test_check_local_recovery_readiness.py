"""Fail-closed tests for read-only MegaRepo local recovery readiness."""
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from check_local_recovery_readiness import analyze

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "check_local_recovery_readiness.py"
HASH = "sha256-" + "a" * 64
URL = "https://raw.githubusercontent.com/org/repo/builds/Sample.cs3"


def blocked(**changes):
    row = {"plugin": "Sample", "sourceId": "upstream", "url": URL,
           "version": 3, "expectedFileSize": 19, "actualFileSize": 20,
           "actualFileHash": HASH, "verification": "mismatch"}
    row.update(changes)
    return row


def selection(row=None):
    row = blocked() if row is None else row
    return {"blockedCandidateCount": 1, "incidents": [{
        "plugin": "sample", "disposition": "quarantined_existing",
        "candidateSourceId": row["sourceId"], "candidateUrl": row["url"],
        "observedActualSize": row["actualFileSize"],
        "observedActualSHA256": row["actualFileHash"]}]}


def approval():
    return {"plugin": "sample", "sourceId": "upstream", "url": URL,
            "version": 3, "fileSize": 20, "fileHash": HASH,
            "reviewedBy": "maintainer", "reviewedAt": "2026-01-01",
            "reason": "Independently reviewed signed release",
            "evidenceUrl": "https://github.com/org/repo/releases/tag/v3"}


class ReadinessTests(unittest.TestCase):
    def test_actual_gate_mismatch_does_not_become_approval(self):
        b = blocked()
        before = copy.deepcopy(b)
        result = analyze([b], [approval()], selection(b))
        self.assertEqual(result["incidents"][0]["category"],
                         "metadata_correction_requires_release_review")
        self.assertEqual(result["withoutMatchingReviewCount"], 1)
        self.assertFalse(result["incidents"][0]["safeToPublishCandidate"])
        self.assertFalse(result["releaseAuthorized"])
        self.assertEqual(b, before)

    def test_genuine_gate_shape_reports_no_review_record(self):
        b = blocked(expectedFileSize=20, verification="hash_verified")
        result = analyze([b], [], selection(b))
        self.assertEqual(result["incidents"][0]["category"], "changed_binary_requires_review")
        self.assertEqual(result["blockedCandidateCount"], 1)
        self.assertEqual(result["incidents"][0]["selectionDisposition"], "quarantined_existing")

    def test_matching_review_is_not_publication_authority(self):
        b = blocked(expectedFileSize=20, verification="hash_verified")
        result = analyze([b], [approval()], selection(b))
        self.assertEqual(result["incidents"][0]["matchingReviewRecordCount"], 1)
        self.assertFalse(result["incidents"][0]["safeToPublishCandidate"])
        self.assertFalse(result["releaseAuthorized"])

    def test_untrusted_review_metadata_is_rejected(self):
        b = blocked(expectedFileSize=20, verification="hash_verified")
        for field, wrong in (("reviewedAt", "2099-01-01"),
                             ("evidenceUrl", "http://not-secure.example/binary"),
                             ("fileHash", "sha256-" + "b" * 64),
                             ("version", 5), ("reviewedBy", "")):
            a = approval(); a[field] = wrong
            with self.subTest(field=field):
                self.assertEqual(analyze([b], [a])["withoutMatchingReviewCount"], 1)

    def test_source_and_size_only_blockers(self):
        b = blocked(expectedFileSize=20, actualFileHash=HASH, verification="size_only_no_checksum")
        self.assertEqual(analyze([b], [approval()])["incidents"][0]["category"],
                         "digest_and_release_review_required")

    def test_malformed_or_mismatched_selection_rejected(self):
        b = blocked()
        selection_record = selection(b)
        selection_record["incidents"][0]["candidateUrl"] = "https://bad.example/file.cs3"
        with self.assertRaisesRegex(ValueError, "does not match"):
            analyze([b], [], selection_record)
        with self.assertRaisesRegex(ValueError, "blocked counts differ"):
            analyze([b], [], {"blockedCandidateCount": 2, "incidents": []})

    def test_duplicate_and_invalid_records_rejected(self):
        b = blocked()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            analyze([b, copy.deepcopy(b)], [])
        with self.assertRaisesRegex(ValueError, "identity"):
            analyze([blocked(url="http://example.org/Sample.cs3")], [])
        with self.assertRaisesRegex(ValueError, "size"):
            analyze([blocked(actualFileSize=True)], [])
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            analyze([blocked(actualFileHash="not-hash")], [])

    def test_cli_reads_verifier_and_writes_advisory_only(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            b = blocked()
            paths = {k: root / (k + ".json") for k in ("verification", "selection", "approvals", "output")}
            paths["verification"].write_text(json.dumps({"checked": 543, "blockedCount": 1, "blocked": [b]}))
            paths["selection"].write_text(json.dumps(selection(b)))
            paths["approvals"].write_text("[]")
            p = subprocess.run([sys.executable, str(SCRIPT),
                                "--verification", str(paths["verification"]),
                                "--selection", str(paths["selection"]),
                                "--approvals", str(paths["approvals"]),
                                "--output", str(paths["output"])],
                               capture_output=True, text=True)
            self.assertEqual(p.returncode, 0, p.stderr)
            result = json.loads(paths["output"].read_text())
            self.assertEqual(result["blockedCandidateCount"], 1)
            self.assertEqual(paths["approvals"].read_text(), "[]")
            p = subprocess.run([sys.executable, str(SCRIPT),
                                "--verification", str(paths["verification"]),
                                "--approvals", str(paths["approvals"]),
                                "--output", str(paths["approvals"])],
                               capture_output=True, text=True)
            self.assertNotEqual(p.returncode, 0)
            self.assertEqual(paths["approvals"].read_text(), "[]")


if __name__ == "__main__":
    unittest.main()
