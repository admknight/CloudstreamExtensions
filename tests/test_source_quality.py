"""Read-only source quality scoring and report reconciliation tests."""
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import source_quality as score


def sample():
    report = {
        "generatedAt": "2026-10-09 21:47:09 UTC", "uniquePlugins": 3,
        "sourceHealth": {"ok": 2, "failed": 1},
        "sourceStatus": [
            {"id": "good", "ok": True, "rawCount": 4,
             "includedCount": 2, "packageFailed": 0, "duplicateSkipped": 2},
            {"id": "duplicate", "ok": True, "rawCount": 1,
             "includedCount": 0, "packageFailed": 0, "duplicateSkipped": 1},
            {"id": "bad", "ok": False, "rawCount": 2,
             "includedCount": 1, "packageFailed": 2, "duplicateSkipped": 0},
        ]
    }
    provenance = [{"sourceId": "good"}, {"sourceId": "good"}, {"sourceId": "bad"}]
    return report, provenance


class SourceQualityTests(unittest.TestCase):
    def test_reconciles_unique_sources_and_explains_score(self):
        report, provenance = sample()
        original = copy.deepcopy(report)
        result = score.analyze(report, provenance)
        sources = {x["id"]: x for x in result["report"]["sources"]}
        self.assertEqual(sources["good"]["score"], 100)
        self.assertEqual(sources["duplicate"]["score"], 60)
        self.assertEqual(sources["bad"]["score"], 23.3)
        self.assertEqual(sources["duplicate"]["duplicateOnly"], True)
        self.assertEqual(sources["bad"]["currentPackageContributionRatioPct"], 33.3)
        self.assertEqual(result["report"]["publishedPluginCount"], 3)
        self.assertFalse(result["report"]["scoring"]["sourceSelectionAffected"])
        self.assertEqual(report, original)

    def test_recent_failures_affect_score_without_changing_prior_evidence(self):
        report, provenance = sample()
        past = {"version": 1, "snapshots": [{
            "generatedAt": "2026-10-08 21:47:09 UTC",
            "sources": [{"id": "good", "ok": False}, {"id": "bad", "ok": False}]
        }]}
        before = copy.deepcopy(past)
        out = score.analyze(report, provenance, past)
        sources = {x["id"]: x for x in out["report"]["sources"]}
        self.assertEqual(sources["good"]["indexAvailability30dPct"], 50)
        self.assertEqual(sources["good"]["score"], 70)
        self.assertEqual(sources["bad"]["failedIndexObservations30d"], 2)
        self.assertEqual(out["history"]["snapshots"], past["snapshots"] + [
            {"generatedAt": report["generatedAt"],
             "sources": [
                 {"id": "good", "ok": True},
                 {"id": "duplicate", "ok": True},
                 {"id": "bad", "ok": False},
             ]}
        ])
        self.assertEqual(past, before)

    def test_same_snapshot_is_idempotent(self):
        report, prov = sample()
        one = score.analyze(report, prov)
        two = score.analyze(report, prov, one["history"])
        self.assertEqual(len(two["history"]["snapshots"]), 1)
        self.assertEqual(two["report"], one["report"])

    def test_missing_or_duplicate_provenance_rejected(self):
        report, prov = sample()
        with self.assertRaisesRegex(ValueError, "count mismatch"):
            score.analyze(report, prov[:-1])
        mismatch = copy.deepcopy(prov)
        mismatch[0]["sourceId"] = "unknown"
        with self.assertRaisesRegex(ValueError, "do not reconcile"):
            score.analyze(report, mismatch)
        duplicate = copy.deepcopy(report)
        duplicate["sourceStatus"][1]["id"] = "good"
        with self.assertRaisesRegex(ValueError, "duplicate"):
            score.analyze(duplicate, prov)

    def test_invalid_history_rejected_without_replacing_it(self):
        report, prov = sample()
        with self.assertRaisesRegex(ValueError, "history"):
            score.analyze(report, prov, {"snapshots": "bad"})
        with self.assertRaises(ValueError):
            score.analyze(report, prov, {"snapshots": [{"generatedAt": "broken", "sources": []}]})

    def test_markdown_is_complete_and_advisory(self):
        report, prov = sample()
        body = score.render_markdown(score.analyze(report, prov)["report"])
        self.assertIn("Scor", body)
        self.assertIn("never modify source priorities", body)
        self.assertIn("| good | 100", body)
        self.assertIn("| duplicate | 60", body)

    def test_command_creates_report_without_modifying_inputs(self):
        report, prov = sample()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name, obj in (("report", report), ("provenance", prov)):
                (root / (name + ".json")).write_text(json.dumps(obj))
            command = [sys.executable, str(ROOT / "tools/source_quality.py"),
                       "--report", str(root / "report.json"),
                       "--provenance", str(root / "provenance.json"),
                       "--history-in", str(root / "missing.json"),
                       "--history-out", str(root / "history.json"),
                       "--json", str(root / "quality.json"),
                       "--markdown", str(root / "quality.md")]
            proc = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(proc.returncode, 0, proc.stderr)
            out = json.loads((root / "quality.json").read_text())
            self.assertEqual(out["sourceCount"], 3)
            self.assertEqual(len(json.loads((root / "history.json").read_text())["snapshots"]), 1)
            self.assertEqual(json.loads((root / "report.json").read_text()), report)
            self.assertEqual(json.loads((root / "provenance.json").read_text()), prov)


if __name__ == "__main__":
    unittest.main()
