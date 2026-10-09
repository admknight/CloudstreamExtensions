import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from validate_audit_origin import verify_origin

REPOSITORY = "admknight/CloudstreamExtensions"
EXAMPLE_RUN_ID = 37952669506


def original_run():
    # Real production audit run metadata. GitHub returns a repository-relative
    # path without a leading slash.
    return {
        "id": EXAMPLE_RUN_ID,
        "name": "Audit Published Plugin Integrity",
        "path": ".github/workflows/audit-package-integrity.yml",
        "head_branch": "master",
        "status": "completed",
        "conclusion": "failure",
        "event": "schedule",
        "head_repository": {"full_name": REPOSITORY},
        "repository": {"full_name": REPOSITORY},
        "run_attempt": 1,
    }


class OriginVerificationTests(unittest.TestCase):
    def test_accepts_real_scheduled_failed_audit(self):
        result = verify_origin(original_run(), REPOSITORY, str(EXAMPLE_RUN_ID))
        self.assertEqual(result["runId"], EXAMPLE_RUN_ID)

    def test_completed_failing_audit_is_processable(self):
        run = original_run()
        run["conclusion"] = "failure"
        self.assertEqual(verify_origin(run, REPOSITORY, run["id"])["event"], "schedule")

    def test_accepts_master_dispatch(self):
        run = original_run()
        run["event"] = "workflow_dispatch"
        self.assertEqual(verify_origin(run, REPOSITORY, run["id"])["event"], "workflow_dispatch")

    def test_rejects_leading_slash_inconsistent_with_github_contract(self):
        run = original_run()
        run["path"] = "/.github/workflows/audit-package-integrity.yml"
        with self.assertRaisesRegex(ValueError, "workflow_path"):
            verify_origin(run, REPOSITORY, EXAMPLE_RUN_ID)

    def test_rejects_wrong_workflow_path(self):
        run = original_run()
        run["path"] = ".github/workflows/build.yml"
        with self.assertRaisesRegex(ValueError, "workflow_path"):
            verify_origin(run, REPOSITORY, EXAMPLE_RUN_ID)

    def test_rejects_unauthorized_pr_origin(self):
        run = original_run()
        run["event"] = "pull_request"
        with self.assertRaisesRegex(ValueError, "event"):
            verify_origin(run, REPOSITORY, EXAMPLE_RUN_ID)

    def test_rejects_forked_run_even_if_head_branch_is_master(self):
        run = original_run()
        run["head_repository"] = {"full_name": "attacker/repo"}
        with self.assertRaisesRegex(ValueError, "head_repository"):
            verify_origin(run, REPOSITORY, EXAMPLE_RUN_ID)

    def test_rejects_wrong_repository(self):
        run = original_run()
        run["repository"] = {"full_name": "attacker/repo"}
        with self.assertRaisesRegex(ValueError, "repository"):
            verify_origin(run, REPOSITORY, EXAMPLE_RUN_ID)

    def test_rejects_mismatched_run_id(self):
        with self.assertRaisesRegex(ValueError, "run_id"):
            verify_origin(original_run(), REPOSITORY, 99)

    def test_rejects_running_audit(self):
        run = original_run()
        run["status"] = "in_progress"
        with self.assertRaisesRegex(ValueError, "status"):
            verify_origin(run, REPOSITORY, EXAMPLE_RUN_ID)


if __name__ == "__main__":
    unittest.main()
