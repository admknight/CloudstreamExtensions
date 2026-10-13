"""Regression gates for green-with-documented-holds, never green-for-new-drift."""
import copy
import hashlib
import json
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import audit_package_integrity as audit
from guarded_audit_classification import classify_drift

SHA = "a" * 40
OLDER = b"old signed package"
NEWER = b"new independently scanned candidate"
DIGEST = lambda b: "sha256-" + hashlib.sha256(b).hexdigest()
PIN = f"https://raw.githubusercontent.com/demo/repo/{SHA}/Demo.cs3"
UNPIN = "https://raw.githubusercontent.com/demo/repo/builds/Demo.cs3"
INDEX = "https://raw.githubusercontent.com/demo/repo/builds/plugins.json"


def fixture():
    earlier = {
        "internalName": "Demo", "name": "Demo", "url": PIN,
        "version": 1, "status": 1, "fileSize": len(OLDER),
        "fileHash": DIGEST(OLDER),
    }
    changed = {
        **earlier, "url": UNPIN, "version": 2,
        "fileSize": len(NEWER), "fileHash": DIGEST(NEWER),
    }
    origin = {
        "plugin": "Demo", "sourceId": "demo", "packageUrl": PIN,
        "sourceRepository": "https://github.com/demo/repo",
        "sourceIndex": INDEX,
    }
    release = {
        "uniquePlugins": 1, "releaseEligible": True,
        "publicationMethod": "guarded_per_plugin_compatibility",
        "sourceHealth": {"ok": 1, "failed": 0}, "changes": {"removed": 0},
        "integrityHealth": {
            "unverifiedPreviousCarried": 0, "pinnedPreviousRecovered": 1,
        },
        "deferredUnverified": [], "trustedPolicyUpgrades": [],
        "quarantineIncidents": [{
            "plugin": "demo", "disposition": "retained_immutable_previous",
            "previousSourceId": "demo", "candidateSourceId": "demo",
            "candidateUrl": UNPIN, "previousUrl": PIN, "fallbackPin": PIN,
            "fallbackVerified": True, "fallbackThroughRecoveryLock": True,
            "observedActualSize": len(NEWER),
            "observedActualSHA256": DIGEST(NEWER),
        }],
    }
    return earlier, changed, origin, release


def differences(current, upstream, origin):
    return audit.compare_catalog(
        [current], [origin], [{"id": "demo", "index": INDEX}],
        {"demo": {"demo": upstream}}
    )


def offline_audit(*, mutate=None, hash_error=False, guarded=True, full=False):
    old, newer, origin, release = fixture()
    if mutate:
        mutate(old, newer, origin, release)
    source = {"sources": [{"id": "demo", "index": INDEX}]}
    def fetcher(url):
        if url == audit.SOURCES_URL:
            return source
        if url == INDEX:
            return [newer]
        raise AssertionError("Unexpected read: " + url)
    def downloader(url, limit):
        byte_data = b"tampered" if hash_error else OLDER
        return b"", len(byte_data), DIGEST(byte_data)
    with TemporaryDirectory() as root:
        root = Path(root)
        for path, value in (
            ("plugins.json", [old]),
            ("provenance.json", [origin]),
            ("merge-report.json", release),
        ):
            (root/path).write_text(json.dumps(value), encoding="utf-8")
        return audit.audit(
            full=full, buckets=1, slot=0, fetcher=fetcher,
            downloader=downloader, published_file=root/"plugins.json",
            provenance_file=root/"provenance.json",
            guarded_release_report=(root/"merge-report.json" if guarded else None),
        )


class GuardedClassificationTests(unittest.TestCase):
    def test_known_pinned_old_binary_is_warning_not_hourly_failure(self):
        report = offline_audit()
        self.assertTrue(report["pass"])
        self.assertFalse(report["strictUnclassifiedAuditPass"])
        self.assertEqual(report["guardedHourlyClassification"]["knownCount"], 1)
        self.assertEqual(report["guardedHourlyClassification"]["unexpectedCount"], 0)
        self.assertEqual(report["scan"]["hashVerified"], 1)
        self.assertEqual(len(report["metadataDrift"]), 1)
        self.assertIn("DOCUMENTED", report["verdict"])

    def test_independent_strict_audit_preserves_old_behavior(self):
        report = offline_audit(guarded=False, full=True)
        self.assertFalse(report["pass"])
        self.assertIsNone(report["guardedHourlyClassification"])

    def test_full_scan_cannot_use_hourly_exception(self):
        with self.assertRaisesRegex(ValueError, "only for rotating hourly"):
            offline_audit(full=True)

    def test_changed_candidate_digest_since_release_is_new_regression(self):
        result = offline_audit(mutate=lambda old, new, src, release:
                               new.update(fileHash=DIGEST(b"unknown")))
        self.assertFalse(result["pass"])
        self.assertEqual(result["guardedHourlyClassification"]["unexpectedCount"], 1)

    def test_forged_fallback_verified_flag_is_not_accepted(self):
        result = offline_audit(mutate=lambda old, new, src, r:
                               r["quarantineIncidents"][0].update(fallbackVerified=False))
        self.assertFalse(result["pass"])
        self.assertEqual(result["guardedHourlyClassification"]["unexpectedCount"], 1)

    def test_pinned_package_download_mismatch_always_fails(self):
        result = offline_audit(hash_error=True)
        self.assertFalse(result["pass"])
        self.assertEqual(result["guardedHourlyClassification"]["knownCount"], 1)
        self.assertEqual(result["scan"]["failed"], 1)

    def test_source_status_change_never_masked(self):
        result = offline_audit(mutate=lambda old, new, src, r:
                               new.update(status=0))
        self.assertFalse(result["pass"])
        self.assertEqual(result["guardedHourlyClassification"]["unexpectedCount"], 1)

    def test_source_identity_change_never_masked(self):
        result = offline_audit(mutate=lambda old, new, src, r:
                               src.update(sourceId="other"))
        self.assertFalse(result["pass"])
        self.assertEqual(result["guardedHourlyClassification"]["unexpectedCount"], 1)

    def test_unverified_mutable_fallback_prevents_green(self):
        def mutate(old, new, src, r):
            r["deferredUnverified"] = [{"plugin": "Demo"}]
            r["integrityHealth"]["unverifiedPreviousCarried"] = 1
        result = offline_audit(mutate=mutate)
        self.assertFalse(result["pass"])
        self.assertEqual(result["guardedHourlyClassification"]["unverifiedMutableHeldCount"], 1)

    def test_exact_policy_url_only_difference_is_documented(self):
        old, changed, origin, r = fixture()
        r["quarantineIncidents"] = []
        r["integrityHealth"]["pinnedPreviousRecovered"] = 0
        current = copy.deepcopy(old)
        current["version"] = 3
        original = {**current, "url": UNPIN}
        origin["packageUrl"] = PIN
        r["trustedPolicyUpgrades"] = [{
            "authorizationKind": "trusted_upstream_policy_v1",
            "plugin": "demo", "sourceId": "demo", "url": PIN,
            "originalUrl": UNPIN, "version": 3,
            "fileSize": len(OLDER), "fileHash": DIGEST(OLDER),
            "pinnedIndexVerified": True, "separateBytesVerified": True,
            "immutableCommit": SHA,
            "pinnedIndexUrl": f"https://raw.githubusercontent.com/demo/repo/{SHA}/plugins.json",
        }]
        diff = differences(current, original, origin)
        verdict = classify_drift([current], [origin], r, diff)
        self.assertEqual(verdict["knownCount"], 1)
        self.assertEqual(verdict["unexpectedCount"], 0)

    def test_policy_version_change_is_not_url_only(self):
        old, changed, origin, r = fixture()
        r["quarantineIncidents"] = []
        r["integrityHealth"]["pinnedPreviousRecovered"] = 0
        r["trustedPolicyUpgrades"] = [{
            "authorizationKind": "trusted_upstream_policy_v1",
            "plugin": "demo", "sourceId": "demo",
            "url": PIN, "originalUrl": UNPIN,
            "version": 1, "fileSize": len(OLDER), "fileHash": DIGEST(OLDER),
            "pinnedIndexVerified": True, "separateBytesVerified": True,
            "immutableCommit": SHA,
            "pinnedIndexUrl": f"https://raw.githubusercontent.com/demo/repo/{SHA}/plugins.json",
        }]
        changed.update(fileHash=old["fileHash"], fileSize=old["fileSize"])
        verdict = classify_drift([old], [origin], r, differences(old, changed, origin))
        self.assertEqual(verdict["unexpectedCount"], 1)

    def test_stale_or_fabricated_report_fails_closed(self):
        old, changed, origin, r = fixture()
        r["uniquePlugins"] = 547
        with self.assertRaisesRegex(ValueError, "guarded publication assurance"):
            classify_drift([old], [origin], r, differences(old, changed, origin))


if __name__ == "__main__":
    unittest.main()
