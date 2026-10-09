"""Offline regression tests for review-only per-plugin catalog selection."""
import copy
import hashlib
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from plan_per_plugin_selection import (
    build_preview, immutable_package_url, verified_immutable_previous
)

ROOT = "https://raw.githubusercontent.com/demo/example/"
PIN = ROOT + "a" * 40 + "/"
MUTABLE = ROOT + "builds/"


def sha(data):
    return "sha256-" + hashlib.sha256(data).hexdigest()


def item(name="Alpha", version=1, contents=b"original", url=None,
         source_hash=True):
    result = {
        "internalName": name, "name": name, "version": version,
        "status": 1,
        "url": url or MUTABLE + name + ".cs3",
        "fileSize": len(contents),
    }
    if source_hash:
        result["fileHash"] = sha(contents)
    return result


def provenance(plugins, source="test"):
    return [{"plugin": p["internalName"], "sourceId": source,
             "origin": "preserve-this-metadata"} for p in plugins]


def verifier(storage, calls=None):
    """Behave like verify_package: length is checked before declared SHA."""
    def check(plugin):
        if calls is not None:
            calls.append(plugin["url"])
        url = plugin["url"]
        if url not in storage:
            return {"status": "error", "error": "offline-missing"}
        content = storage[url]
        outcome = {
            "status": "size_only_no_checksum",
            "actualFileSize": len(content),
            "actualFileHash": sha(content),
        }
        if plugin.get("fileSize") != len(content):
            outcome.update(status="mismatch", reason="fileSize")
        elif plugin.get("fileHash"):
            if plugin["fileHash"].lower() == sha(content):
                outcome["status"] = "hash_verified"
            else:
                outcome.update(status="mismatch", reason="fileHash")
        return outcome
    return check


def select(candidate, previous=None, storage=None,
           candidate_source="test", previous_source="test", calls=None):
    old = previous or []
    existing = storage if storage is not None else {
        p["url"]: b"original" for p in candidate
    }
    return build_preview(
        candidate, old, provenance(candidate, candidate_source),
        provenance(old, previous_source),
        [], checker=verifier(existing, calls), workers=1
    )


class PerPluginSelectionTests(unittest.TestCase):
    def test_recognizes_only_immutable_commit_address(self):
        self.assertTrue(immutable_package_url(PIN + "Alpha.cs3"))
        self.assertFalse(immutable_package_url(MUTABLE + "Alpha.cs3"))
        self.assertFalse(immutable_package_url(PIN + "Alpha.cs3?nocache=1"))
        self.assertFalse(immutable_package_url(PIN + "Alpha.cs3#fragment"))
        self.assertFalse(immutable_package_url("http://raw.githubusercontent.com/demo/repo/" + "a"*40 + "/A.cs3"))
        self.assertFalse(immutable_package_url("https://evil.com/demo/repo/" + "a"*40 + "/A.cs3"))
        self.assertFalse(immutable_package_url("https://gitlab.com/d/r/-/raw/" + "a"*40 + "/A.cs3"))

    def test_verified_candidate_passes_without_changes(self):
        records = [item(name=f"P{i}") for i in range(50)]
        storage = {p["url"]: b"original" for p in records}
        proposed, origin, report, gate = select(records, records, storage)
        self.assertEqual(len(proposed), 50)
        self.assertEqual(len(origin), 50)
        self.assertEqual(report["selection"]["acceptedCandidates"], 50)
        self.assertEqual(report["blockedCandidateCount"], 0)
        self.assertFalse(report["automaticPublicationAuthorized"])

    def test_untrusted_changed_mutable_binary_is_quarantined(self):
        previous = [item(name=f"P{i}") for i in range(51)]
        candidate = copy.deepcopy(previous)
        candidate[0]["version"] = 2
        candidate[0]["fileSize"] = len(b"replaced")
        candidate[0]["fileHash"] = sha(b"replaced")
        storage = {p["url"]: b"original" for p in previous}
        storage[candidate[0]["url"]] = b"replaced"
        planned, provenance_rows, summary, verification = select(candidate, previous, storage)
        self.assertEqual(len(planned), 50)
        self.assertEqual(len(provenance_rows), 50)
        self.assertEqual(summary["selection"]["quarantinedExisting"], 1)
        self.assertEqual(summary["selection"]["acceptedCandidates"], 50)
        self.assertEqual(summary["incidents"][0]["disposition"], "quarantined_existing")
        self.assertTrue(summary["requiresExplicitReleaseReview"])
        self.assertEqual(verification["blockedCount"], 1)
        self.assertNotIn("P0", [x["internalName"] for x in planned])
        self.assertEqual(previous[0]["version"], 1)
        self.assertEqual(candidate[0]["version"], 2)

    def test_pinned_previous_is_independently_verified_and_retained(self):
        before = [item(name=f"P{i}") for i in range(51)]
        before[0] = item(name="P0", url=PIN+"P0.cs3")
        now = copy.deepcopy(before)
        now[0] = item(name="P0", version=2, contents=b"untrusted", url=MUTABLE+"P0.cs3")
        storage = {p["url"]: b"original" for p in before}
        storage[now[0]["url"]] = b"untrusted"
        checks = []
        result, origin, summary, _ = select(now, before, storage, calls=checks)
        self.assertEqual(summary["selection"]["retainedImmutablePrevious"], 1)
        self.assertEqual(summary["selection"]["quarantinedExisting"], 0)
        self.assertEqual(len(result), 51)
        self.assertEqual(result[0], before[0])
        self.assertEqual(origin[0]["origin"], "preserve-this-metadata")
        self.assertIn(before[0]["url"], checks)
        self.assertTrue(summary["requiresExplicitReleaseReview"])

    def test_pinned_previous_with_changed_content_is_not_held(self):
        before = [item(name=f"P{i}") for i in range(51)]
        before[0] = item(name="P0", url=PIN+"P0.cs3")
        new = copy.deepcopy(before)
        new[0] = item(name="P0", version=2, contents=b"new", url=MUTABLE+"P0.cs3")
        storage = {p["url"]: b"original" for p in before}
        storage[before[0]["url"]] = b"tampered"
        storage[new[0]["url"]] = b"new"
        proposed, _, plan, _ = select(new, before, storage)
        self.assertEqual(plan["selection"]["quarantinedExisting"], 1)
        self.assertEqual(plan["selection"]["retainedImmutablePrevious"], 0)
        self.assertEqual(len(proposed), 50)

    def test_pinned_previous_with_no_digest_is_not_held(self):
        before = [item(name=f"P{i}") for i in range(51)]
        before[0] = item(name="P0", url=PIN+"P0.cs3", source_hash=False)
        changed = copy.deepcopy(before)
        changed[0] = item(name="P0", version=2, contents=b"new", url=MUTABLE+"P0.cs3")
        storage = {p["url"]: b"original" for p in before}
        storage[changed[0]["url"]] = b"new"
        _, _, result, _ = select(changed, before, storage)
        self.assertEqual(result["selection"]["quarantinedExisting"], 1)

    def test_new_untrusted_provider_is_held_without_deleting_existing(self):
        before = [item(name=f"P{i}") for i in range(50)]
        candidate = before + [item(name="Untrusted", version=1, contents=b"new")]
        storage = {p["url"]: b"original" for p in before}
        storage[candidate[-1]["url"]] = b"new"
        proposed, _, result, _ = select(candidate, before, storage)
        self.assertEqual(result["selection"]["withheldNew"], 1)
        self.assertEqual(result["selection"]["quarantinedExisting"], 0)
        self.assertEqual(len(proposed), 50)

    def test_legacy_unchanged_no_hash_is_size_only_not_dropped(self):
        old = [item(name=f"P{i}", source_hash=False) for i in range(50)]
        selected, _, report, gate = select(old, old)
        self.assertEqual(len(selected), 50)
        self.assertEqual(report["selection"]["legacySizeOnlyCandidates"], 50)
        self.assertEqual(gate["legacyUnpinned"], 50)
        self.assertIn("legacy size-only", report["importantLimitation"])

    def test_size_mismatch_is_not_approved(self):
        before = [item(name=f"P{i}") for i in range(51)]
        now = copy.deepcopy(before)
        now[0]["fileSize"] = 10000
        _, _, report, integrity = select(now, before)
        self.assertEqual(report["selection"]["quarantinedExisting"], 1)
        self.assertEqual(integrity["blocked"][0]["verification"], "mismatch")

    def test_replaced_source_without_review_is_quarantined(self):
        before = [item(name=f"P{i}") for i in range(51)]
        changed = copy.deepcopy(before)
        _, _, report, _ = select(
            changed, before, candidate_source="impostor", previous_source="test"
        )
        self.assertEqual(report["selection"]["quarantinedExisting"], 51)

    def test_candidate_disappearance_is_reconciled_as_review_required(self):
        before = [item(name=f"P{i}") for i in range(51)]
        current = before[:-1]
        proposed, _, result, _ = select(current, before)
        self.assertEqual(len(proposed), 50)
        self.assertEqual(result["selection"]["previousRemovedByCandidate"], 1)
        self.assertTrue(result["requiresExplicitReleaseReview"])

    def test_below_minimum_preview_does_not_silently_pass(self):
        old = [item(name=f"P{i}") for i in range(50)]
        now = copy.deepcopy(old)
        now[0]["fileSize"] = 999999
        with self.assertRaisesRegex(ValueError, "fewer than 50"):
            select(now, old)

    def test_duplicate_candidate_identity_fails(self):
        x = item("P0")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            select([x, dict(x)])

    def test_empty_candidate_fails(self):
        with self.assertRaises(ValueError):
            select([])

    def test_verification_exceptions_quarantine_one_entry(self):
        before = [item(name=f"P{i}") for i in range(51)]
        def fail_one(p):
            if p["internalName"] == "P0":
                raise RuntimeError("timeout")
            return verifier({p["url"]: b"original"})(p)
        selected, _, report, _ = build_preview(
            before, before, provenance(before), provenance(before), [],
            checker=fail_one, workers=1
        )
        self.assertEqual(len(selected), 50)
        self.assertEqual(report["selection"]["quarantinedExisting"], 1)

    def test_previous_pinned_wrong_provenance_cannot_fallback(self):
        prior = item(name="P0", url=PIN+"P0.cs3")
        checked, reason = verified_immutable_previous(
            prior, "", verifier({prior["url"]: b"original"})
        )
        self.assertFalse(checked)
        self.assertIn("provenance", reason)

    def test_no_input_mutation_and_report_consistent(self):
        original = [item(name=f"P{i}") for i in range(51)]
        new = copy.deepcopy(original)
        new[0]["version"] = 2
        new_copy = copy.deepcopy(new)
        old_copy = copy.deepcopy(original)
        _, _, report, _ = select(new, original)
        self.assertEqual(new, new_copy)
        self.assertEqual(original, old_copy)
        counts = report["selection"]
        self.assertEqual(report["candidateCount"], (
            counts["acceptedCandidates"] + counts["retainedImmutablePrevious"]
            + counts["quarantinedExisting"] + counts["withheldNew"]
        ))
        self.assertFalse(report["automaticPublicationAuthorized"])


if __name__ == "__main__":
    unittest.main()
