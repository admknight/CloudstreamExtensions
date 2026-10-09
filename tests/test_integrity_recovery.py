import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(ROOT))
from plan_integrity_recovery import issue_key, plan
from sync_integrity_issues import MARKER, issue_body, sync


def failed():
    return {
        "pass": False, "publishedCount": 543,
        "scan": {"mode": "rotation", "checked": 91},
        "metadataDrift": [{"plugin": "GDIndex", "sourceId": "shakzz-recovery",
                           "differences": {"fileSize": {"published":15937, "upstream":17206}}}],
        "packageProblems": [{"plugin": "GDIndex", "sourceId": "shakzz-recovery",
                             "expectedFileSize": 15937, "actualFileSize": 17206,
                             "status": "mismatch", "reason": "fileSize"}],
        "sourceErrors": [],
    }


class IncidentTests(unittest.TestCase):
    def test_same_plugin_metadata_and_binary_one_issue(self):
        incidents = plan(failed())["incidents"]
        self.assertEqual(len(incidents), 1)
        self.assertEqual(set(incidents[0]["categories"]), {"package", "metadata"})

    def test_gdindex_existing_issue_identity_is_preserved(self):
        self.assertEqual(issue_key("GDIndex"), "megarepo-integrity:gdindex-57c0162511")

    def test_spaces_case_unicode_stable_without_collision(self):
        self.assertEqual(issue_key("Shakzz TV"), issue_key("shakzz tv"))
        self.assertNotEqual(issue_key("Shakzz TV"), issue_key("Shakzz-TV"))
        self.assertNotEqual(issue_key("Shakzz TV"), issue_key("Shakzz 测试"))

    def test_fatal_audit_creates_incident(self):
        report = {"pass": False, "fatalError": "Invalid source index"}
        incidents = plan(report)["incidents"]
        self.assertEqual(incidents[0]["plugin"], "audit-system")

    def test_empty_failed_audit_creates_incident(self):
        self.assertEqual(len(plan({"pass": False})["incidents"]), 1)

    def test_full_pass_must_cover_all_published(self):
        report = {"pass": True, "publishedCount": 543,
                  "scan": {"mode": "all", "checked": 543}}
        self.assertTrue(plan(report)["fullVerifiedPass"])
        report["scan"]["checked"] = 1
        self.assertFalse(plan(report)["fullVerifiedPass"])
        report["scan"]["checked"] = 543
        report["scan"]["mode"] = "rotation"
        self.assertFalse(plan(report)["fullVerifiedPass"])

    def test_creates_once_despite_multiple_findings(self):
        calls = []
        def runner(args):
            calls.append(args)
            if args[2] == "list":
                return "[]"
            if args[2] == "create":
                return "https://github.com/demo/issues/1"
            return ""
        result = sync(failed(), runner)
        self.assertEqual(len(result), 1)
        self.assertEqual(len([x for x in calls if x[2] == "create"]), 1)

    def test_unchanged_issue_does_not_write(self):
        incident = plan(failed())["incidents"][0]
        existing = {"number": 18, "title": "[Integrity] " + incident["key"],
                    "body": issue_body(incident), "state": "OPEN"}
        calls = []
        def runner(args):
            calls.append(args)
            return json.dumps([existing]) if args[2] == "list" else ""
        result = sync(failed(), runner)
        self.assertEqual(len(calls), 1)
        self.assertEqual(result[0]["action"], "unchanged")

    def test_closed_issue_reopens_on_repeat(self):
        item = plan(failed())["incidents"][0]
        existing = {"number":18, "title":"[Integrity] "+item["key"],
                    "body":issue_body(item), "state":"CLOSED"}
        calls = []
        def runner(args):
            calls.append(args)
            return json.dumps([existing]) if args[2] == "list" else ""
        sync(failed(), runner)
        self.assertTrue(any(x[2] == "reopen" for x in calls))

    def test_rotation_pass_does_not_clear_incidents(self):
        existing = {"number": 18, "title": "[Integrity] generic",
                    "body": MARKER, "state":"OPEN"}
        def check(mode, checked):
            calls = []
            def runner(args):
                calls.append(args)
                return json.dumps([existing]) if args[2] == "list" else ""
            sync({"pass":True, "publishedCount":543,
                  "scan":{"mode":mode,"checked":checked}},runner)
            return any(x[2]=="close" for x in calls)
        self.assertFalse(check("rotation", 91))
        self.assertFalse(check("all", 1))
        self.assertFalse(check("all", 543))  # Closure requires separate per-plugin verification

    def test_full_pass_preserves_candidate_only_issue(self):
        candidate = {
            "number": 49,
            "title": "[Integrity] megarepo-integrity:unpublished-provider-123",
            "body": MARKER + "\nUnpublished candidate still requires release verification",
            "state": "OPEN",
        }
        calls = []
        def runner(args):
            calls.append(args)
            return json.dumps([candidate]) if args[2] == "list" else ""
        report = {
            "pass": True, "publishedCount": 543,
            "scan": {"mode": "all", "checked": 543},
            "metadataDrift": [], "packageProblems": [], "sourceErrors": []
        }
        changes = sync(report, runner)
        self.assertEqual(changes, [])
        self.assertEqual(len(calls), 1)
        self.assertFalse(any("close" in args for args in calls))

    def test_issue_data_never_becomes_shell_script(self):
        report = failed()
        report["packageProblems"][0]["plugin"] = "GDIndex; $(touch /tmp/bad)"
        calls = []
        def runner(args):
            calls.append(args)
            return "[]" if args[2] == "list" else "https://github.com/test/issues/1"
        sync(report, runner)
        self.assertTrue(all(isinstance(args,list) for args in calls))
        self.assertTrue(any(args[2] == "create" for args in calls))


if __name__ == "__main__":
    unittest.main()
