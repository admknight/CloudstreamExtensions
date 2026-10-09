"""Offline regression suite for deterministic integrity review reconciliation."""
import copy
import hashlib
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(ROOT))
from plan_per_plugin_selection import build_preview
from reconcile_integrity_review import reconcile, write_bundle


def digest(payload):
    return "sha256-" + hashlib.sha256(payload).hexdigest()


def plugin(name, data=b"good", version=1, url=None, has_size=True):
    p = {
        "internalName": name,
        "name": "[Other] " + name,
        "status": 1,
        "version": version,
        "url": url or "https://raw.githubusercontent.com/demo/cs/builds/" + name + ".cs3",
        "fileHash": digest(data),
        "language": "en",
        "tvTypes": ["Movie", "TvSeries"],
    }
    if has_size:
        p["fileSize"] = len(data)
    return p


def source_row(p, source_id="test"):
    name = p["internalName"]
    return {
        "plugin": name, "originalName": name, "publishedName": p["name"],
        "category": "Other", "sourceId": source_id,
        "sourceName": source_id, "selectedVersion": p["version"],
        "sourceIndex": "https://raw.githubusercontent.com/demo/cs/builds/plugins.json",
        "sourceRepository": "https://github.com/demo/cs",
        "packageUrl": p["url"], "authors": ["person"],
    }


def verify_storage(stored):
    def check(p):
        if p["url"] not in stored:
            return {"status": "error", "error": "File not found"}
        data = stored[p["url"]]
        result = {
            "status": "size_only_no_checksum",
            "actualFileSize": len(data),
            "actualFileHash": digest(data),
        }
        if p.get("fileSize") is not None and len(data) != p["fileSize"]:
            result.update({"status": "mismatch", "reason": "fileSize"})
        elif p.get("fileHash"):
            if p["fileHash"].lower() == digest(data):
                result["status"] = "hash_verified"
            else:
                result.update({"status": "mismatch", "reason": "fileHash"})
        return result
    return check


def fixtures():
    previous = [plugin("P" + str(i)) for i in range(65)]
    previous[2]["url"] = (
        "https://raw.githubusercontent.com/demo/cs/" + "a" * 40 + "/P2.cs3"
    )
    current = copy.deepcopy(previous)
    current[0] = plugin("P0", data=b"changed-binary", version=2)
    current[2] = plugin("P2", data=b"new-binary", version=2)
    current.pop(3)
    # Find by identity, not by hard-coded list index after removing P3.
    current = [plugin("P4", version=2) if p["internalName"] == "P4"
               else p for p in current]
    current = [plugin("P5", has_size=False) if p["internalName"] == "P5"
               else p for p in current]
    current = [plugin("P6", version=2) if p["internalName"] == "P6"
               else p for p in current]
    current.append(plugin("P65", data=b"new-untrusted"))
    candidate_provenance = [
        source_row(p, "impostor" if p["internalName"] == "P4" else "test")
        for p in current
    ]
    previous_provenance = [source_row(p) for p in previous]
    stored = {p["url"]: b"good" for p in previous}
    stored[current[0]["url"]] = b"changed-binary"
    stored[current[1]["url"]] = b"good"
    stored[next(x for x in current if x["internalName"]=="P2")["url"]] = b"new-binary"
    stored[next(x for x in current if x["internalName"]=="P65")["url"]] = b"new-untrusted"
    preview, preview_src, selected, gate = build_preview(
        current, previous, candidate_provenance, previous_provenance, [],
        checker=verify_storage(stored), workers=1
    )
    repo = {
        "name": "MegaRepo",
        "manifestVersion": 1,
        "pluginLists": ["https://raw.githubusercontent.com/demo/cs/builds/plugins.json"],
    }
    src = [
        {"id": "test", "name":"test", "repo":"https://github.com/demo/cs",
         "ok":True, "includedCount":64, "rawCount":65},
        {"id": "impostor", "name":"impostor", "repo":"https://github.com/other/cs",
         "ok":True, "includedCount":1, "rawCount":1},
    ]
    report = {
        "candidateStatus":"READY", "uniquePlugins":len(current),
        "maintainer":"Maintainer", "shortcode":"demo",
        "sourceHealth":{"ok":2,"failed":0}, "sourceStatus":src,
        "sourceChanges":{"added":[],"removed":[],"healthChanged":[]},
        "duplicates":[], "changes":{"added":1,"updated":4,"unchanged":60,"removed":1},
    }
    old_report = {"sourceStatus": [src[0]], "removedPlugins":[]}
    return [
        current, previous, candidate_provenance, previous_provenance,
        preview, preview_src, selected, gate, report, old_report,
        repo, copy.deepcopy(repo)
    ]


class ReconcileTests(unittest.TestCase):
    def run_reconcile(self, *, mode="compatibility", modify=None):
        inputs = fixtures()
        if modify:
            modify(inputs)
        return reconcile(*inputs, mode=mode)

    def test_compatibility_preserves_all_previous_records(self):
        bundle = self.run_reconcile()
        report = bundle["merge-report.json"]
        self.assertEqual(report["uniquePlugins"], 65)
        self.assertEqual(report["changes"]["removed"], 0)
        self.assertEqual(report["integrityHealth"]["unverifiedPreviousCarried"], 4)
        self.assertEqual(report["integrityHealth"]["changedOrFailedCandidates"], 5)
        self.assertEqual(report["integrityHealth"]["immutableOldRetained"], 1)
        self.assertFalse(report["releaseAuthorized"])
        self.assertEqual(len(bundle["plugins.json"]),len(bundle["provenance.json"]))
        self.assertEqual(report["changes"]["updated"], 1)
        self.assertEqual(sum(report["changes"].values()), 65)

    def test_strict_quarantine_reports_real_removal_count(self):
        bundle = self.run_reconcile(mode="quarantine")
        rep = bundle["merge-report.json"]
        self.assertEqual(rep["uniquePlugins"], 61)
        self.assertEqual(rep["changes"]["removed"], 4)
        self.assertEqual(rep["integrityHealth"]["unverifiedPreviousCarried"], 0)
        self.assertEqual(rep["integrityHealth"]["quarantinedNotInProposal"], 4)
        self.assertEqual(rep["changes"]["updated"], 1)
        self.assertFalse(bundle["reconciliation.json"]["releaseAuthorized"])

    def test_compatibility_old_entries_are_not_mutated(self):
        inputs = fixtures()
        before = copy.deepcopy(inputs[1])
        original_prov = copy.deepcopy(inputs[3])
        result = reconcile(*inputs, mode="compatibility")
        old = {x["internalName"]:x for x in before}
        updated = {x["internalName"]:x for x in result["plugins.json"]}
        for key in ("P0","P3","P4","P5","P2"):
            self.assertEqual(old[key], updated[key])
        self.assertEqual(inputs[1], before)
        self.assertEqual(inputs[3], original_prov)
        pids = {x["plugin"]:x for x in result["provenance.json"]}
        original = {x["plugin"]:x for x in original_prov}
        for key in ("P0","P3","P4","P5","P2"):
            self.assertEqual(pids[key], original[key])

    def test_status_and_diff_counts_derive_from_same_catalog(self):
        d = self.run_reconcile()
        n = len(d["plugins.json"])
        report = d["merge-report.json"]
        delta = d["release-diff.json"]
        self.assertEqual(n,report["uniquePlugins"])
        self.assertEqual(n,delta["catalog"]["plugins"])
        self.assertEqual(report["changes"],delta["changes"])
        self.assertEqual(report["sourceHealth"],{
            "ok":2, "failed":0
        })
        self.assertEqual(sum(report["categoryCounts"].values()), n)
        self.assertEqual(sum(x["includedCount"] for x in report["sourceStatus"]), n)
        for phrase in ("NOT PUBLISHED","Unverified","Blocked"):
            self.assertIn(phrase.lower(), d["STATUS.md"].lower())
        self.assertIn("NOT PUBLISHED", d["README.md"])
        self.assertIn("Not production release notes",d["RELEASE_NOTES.md"])
        self.assertIn("releaseAuthorized",d["reconciliation.json"])

    def test_live_duplicate_decisions_not_misrepresented_as_final(self):
        d = self.run_reconcile()
        r = d["merge-report.json"]
        self.assertEqual(r["duplicates"],[])
        self.assertIn("candidateDuplicateDecisions",r)

    def test_preview_digest_mismatch_fails_closed(self):
        def change(args):
            args[0][0]["version"]=199
        with self.assertRaisesRegex(ValueError,"does not bind original input"):
            self.run_reconcile(modify=change)

    def test_tampered_selected_record_fails_closed(self):
        def change(args):
            args[4][0]["name"]="altered without revalidation"
        with self.assertRaisesRegex(ValueError,"does not bind preview output"):
            self.run_reconcile(modify=change)

    def test_tampered_selection_or_provenance_is_rejected(self):
        def change(args):
            args[5][0]["sourceId"]="other"
        with self.assertRaises(ValueError):
            self.run_reconcile(modify=change)

    def test_report_count_tampering_is_rejected(self):
        def change(args):
            args[8]["uniquePlugins"]=1
        with self.assertRaisesRegex(ValueError,"merger report doesn't match"):
            self.run_reconcile(modify=change)

    def test_empty_approvals_cannot_override_manifest(self):
        def change(args):
            args[10]["pluginLists"]=["https://evil.example/plugins.json"]
        with self.assertRaisesRegex(ValueError,"Repo manifest changed"):
            self.run_reconcile(modify=change)

    def test_source_health_blocked_prevents_reconciliation(self):
        def change(args):
            args[8]["sourceHealth"]["failed"]=1
        with self.assertRaisesRegex(ValueError,"Unhealthy source"):
            self.run_reconcile(modify=change)

    def test_invalid_source_counts_fail_evidence(self):
        def change(args):
            args[6]["selection"]["quarantinedExisting"]=300
        with self.assertRaisesRegex(ValueError,"Selection counts disagree"):
            self.run_reconcile(modify=change)

    def test_unknown_mode_fails(self):
        with self.assertRaisesRegex(ValueError,"Unrecognized reconciliation mode"):
            self.run_reconcile(mode="delete-all")

    def test_atomic_nonoverwrite_output(self):
        from tempfile import TemporaryDirectory
        with TemporaryDirectory() as tmp:
            dest=Path(tmp)/"preview"
            bundle=self.run_reconcile()
            write_bundle(dest,bundle)
            self.assertEqual(
                len(list(dest.glob("*"))),len(bundle)
            )
            with self.assertRaises(FileExistsError):
                write_bundle(dest,bundle)

    def test_manifest_json_shape(self):
        d=self.run_reconcile()
        self.assertIn("pluginLists",d["repo.json"])
        self.assertTrue(all("internalName" in p for p in d["plugins.json"]))
        self.assertTrue(all("sourceId" in r for r in d["provenance.json"]))
        self.assertFalse(d["reconciliation.json"]["releaseAuthorized"])
        self.assertTrue(d["reconciliation.json"]["changesReconciled"])


if __name__ == "__main__":
    unittest.main()
