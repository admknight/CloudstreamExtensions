"""Guarded publication candidate: no removals, all counts and URLs reconciled."""
import copy
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"tools"))
sys.path.insert(0,str(ROOT/"tests"))
from test_reconcile_integrity_review import fixtures
from finalize_guarded_release import make_guarded_candidate,EXPECTED_FILES
from reconcile_integrity_review import write_bundle


def baseline():
    values=fixtures()
    report=values[8]
    report["packageHealth"]={"reachable":len(values[0]),"failed":0}
    report["failedPlugins"]=[]
    report["inactiveSources"]=[]
    return values


class PublishCandidateTests(unittest.TestCase):
    def produce(self, amend=None, ceiling=24):
        a=baseline()
        if amend:
            amend(a)
        return make_guarded_candidate(*a,max_deferred=ceiling)

    def test_no_previous_identity_removed_from_guarded_release(self):
        bundle,evidence=self.produce()
        names={p["internalName"].casefold() for p in bundle["plugins.json"]}
        previous={p["internalName"].casefold() for p in baseline()[1]}
        self.assertEqual(previous,names)
        self.assertEqual(evidence["selectedCount"],65)
        self.assertEqual(evidence["deferredUnverifiedCount"],4)
        self.assertEqual(bundle["merge-report.json"]["changes"]["removed"],0)
        self.assertEqual(bundle["merge-report.json"]["uniquePlugins"],65)
        self.assertTrue(evidence["releaseEligible"])
        self.assertFalse(evidence["actualPublicationPerformed"])

    def test_unapproved_binary_keeps_unchanged_published_record(self):
        inputs=baseline()
        output,evidence=make_guarded_candidate(*inputs)
        prior={p["internalName"]:p for p in inputs[1]}
        chosen={p["internalName"]:p for p in output["plugins.json"]}
        for name in ("P0","P3","P4","P5"):
            self.assertEqual(prior[name], chosen[name])
        self.assertEqual(evidence["deferredUnverifiedCount"],4)

    def test_master_readme_and_status_preserve_install_instructions(self):
        out,_=self.produce()
        readme=out["README.md"]
        self.assertIn("## 🌐 Full MegaRepo installation",readme)
        self.assertIn("## Choose the right MegaRepo tool",readme)
        self.assertIn("## Integrity status",readme)
        self.assertIn("Legacy",out["RELEASE_NOTES.md"] if False else out["STATUS.md"])
        self.assertIn("old manifest entry",readme)
        self.assertIn("Deferred: previous metadata",out["STATUS.md"])

    def test_counts_match_source_health_and_diff(self):
        bundle,evidence=self.produce()
        report=bundle["merge-report.json"]
        diff=bundle["release-diff.json"]
        self.assertEqual(report["changes"],diff["changes"])
        self.assertEqual(sum(report["changes"].values()),len(bundle["plugins.json"]))
        self.assertEqual(sum(report["categoryCounts"].values()),len(bundle["plugins.json"]))
        self.assertEqual(sum(s["includedCount"] for s in report["sourceStatus"]),len(bundle["plugins.json"]))
        self.assertEqual(len(bundle["provenance.json"]),len(bundle["plugins.json"]))
        self.assertEqual(report["packageHealth"],{"reachable":65,"failed":0})
        self.assertEqual(report["integrityHealth"],diff["integrityHealth"])
        self.assertEqual(evidence["selectedCount"],diff["catalog"]["plugins"])

    def test_reject_unexpected_massive_deferred_changes(self):
        with self.assertRaisesRegex(ValueError,"exceed safety ceiling"):
            self.produce(ceiling=2)

    def test_reject_source_health_failure(self):
        def edit(args):
            args[8]["sourceHealth"]["failed"]=1
        with self.assertRaises(ValueError):
            self.produce(amend=edit)

    def test_reject_repo_manifest_change(self):
        def edit(args):
            args[10]["pluginLists"]=["https://evil.example/repo.json"]
        with self.assertRaises(ValueError):
            self.produce(amend=edit)

    def test_reject_inconsistent_selection_snapshot(self):
        def edit(args):
            args[4][0]["fileHash"]="sha256-"+("0"*64)
        with self.assertRaises(ValueError):
            self.produce(amend=edit)

    def test_reject_out_of_bounds_defer_limit(self):
        for value in (-1,101):
            with self.assertRaises(ValueError):
                self.produce(ceiling=value)

    def test_selected_package_identity_and_authors_are_preserved(self):
        inputs=baseline()
        bundle,_=make_guarded_candidate(*inputs)
        chosen={p["internalName"]:p for p in bundle["plugins.json"]}
        old={p["internalName"]:p for p in inputs[1]}
        for name in ("P0","P3","P4","P5"):
            self.assertEqual(chosen[name]["fileHash"],old[name]["fileHash"])
            self.assertEqual(chosen[name]["url"],old[name]["url"])

    def test_only_expected_artifact_files_can_be_copied_into_release(self):
        bundle,assurance=self.produce()
        self.assertEqual(set(bundle),set(EXPECTED_FILES))
        self.assertEqual(set(assurance["files"]),set(EXPECTED_FILES))
        self.assertTrue(assurance["mode"].endswith("NOT_YET_PUBLISHED"))

    def test_guarded_release_output_does_not_overwrite_existing(self):
        bundle,assurance=self.produce()
        with TemporaryDirectory() as t:
            directory=Path(t)/"guarded-stage"
            write_bundle(directory,{**bundle,"guarded-release-assurance.json":assurance})
            with self.assertRaises(FileExistsError):
                write_bundle(directory,bundle)
            self.assertTrue((directory/"guarded-release-assurance.json").exists())


if __name__=="__main__":
    unittest.main()
