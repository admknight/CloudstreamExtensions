import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest import TestCase, main
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import guarded_schedule_backstop as backstop

NOW = datetime(2026, 10, 10, 12, 0, tzinfo=timezone.utc)


def run(*, hours=1, status="completed", conclusion="success", branch="master", event="schedule"):
    return {"created_at": (NOW - timedelta(hours=hours)).isoformat(),
            "status": status, "conclusion": conclusion,
            "head_branch": branch, "event": event}


class BackstopTests(TestCase):
    def test_recent_success_no_dispatch(self):
        self.assertFalse(backstop.decision([run(hours=1)], NOW, timedelta(hours=7), timedelta(hours=12))[0])

    def test_missing_schedule_dispatches_only_after_tolerance(self):
        self.assertTrue(backstop.decision([run(hours=8)], NOW, timedelta(hours=7), timedelta(hours=12))[0])

    def test_failed_run_has_longer_cooldown(self):
        self.assertFalse(backstop.decision([run(hours=8, conclusion="failure")], NOW,
                                           timedelta(hours=7), timedelta(hours=12))[0])
        self.assertTrue(backstop.decision([run(hours=13, conclusion="failure")], NOW,
                                          timedelta(hours=7), timedelta(hours=12))[0])

    def test_pending_run_prevents_duplicate(self):
        self.assertFalse(backstop.decision([run(hours=9, status="in_progress")], NOW,
                                           timedelta(hours=7), timedelta(hours=12))[0])

    def test_ignores_pr_and_non_master_runs(self):
        self.assertFalse(backstop.decision([run(hours=8, branch="fix"), run(hours=8, event="pull_request")],
                                           NOW, timedelta(hours=7), timedelta(hours=12))[0])

    def test_missing_run_has_no_automatic_approval(self):
        self.assertFalse(backstop.decision([], NOW, timedelta(hours=7), timedelta(hours=12))[0])

    def test_future_timestamp_skips(self):
        self.assertFalse(backstop.decision([run(hours=-1)], NOW, timedelta(hours=7), timedelta(hours=12))[0])

    def test_no_token_fails_closed(self):
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": backstop.REPOSITORY, "GH_TOKEN": "", "GITHUB_EVENT_NAME": "schedule"}):
            with self.assertRaises(SystemExit):
                backstop.main([])

    def test_no_dangerous_repository(self):
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": "fork/not-production", "GH_TOKEN": "token", "GITHUB_EVENT_NAME": "schedule"}):
            with self.assertRaises(SystemExit):
                backstop.main(["--execute"])

    def test_cannot_dispatch_from_untrusted_pr(self):
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": backstop.REPOSITORY, "GH_TOKEN": "token", "GITHUB_EVENT_NAME": "pull_request"}):
            with self.assertRaises(SystemExit):
                backstop.main(["--execute"])

    def test_dry_run_does_not_dispatch(self):
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": backstop.REPOSITORY, "GH_TOKEN": "token", "GITHUB_EVENT_NAME": "schedule"}):
            with patch.object(backstop, "github_api", return_value={"workflow_runs": [run(hours=60)]}) as mocked:
                self.assertEqual(0, backstop.main([]))
                self.assertEqual(3, mocked.call_count)
                self.assertTrue(all(x.kwargs.get("method", "GET") == "GET" for x in mocked.call_args_list))

    def test_execution_only_allowlisted_dispatch_paths(self):
        with patch.dict(os.environ, {"GITHUB_REPOSITORY": backstop.REPOSITORY, "GH_TOKEN": "token", "GITHUB_EVENT_NAME": "schedule"}):
            with patch.object(backstop, "github_api", side_effect=lambda path, token, **kwargs: (
                {"workflow_runs": [run(hours=60)]} if kwargs.get("method", "GET") == "GET" else {}
            )) as mocked:
                self.assertEqual(0, backstop.main(["--execute"]))
                write_paths = [c.args[0] for c in mocked.call_args_list if c.kwargs.get("method") == "POST"]
                self.assertEqual(3, len(write_paths))
                self.assertTrue(all(p.endswith("/dispatches") for p in write_paths))


if __name__ == "__main__":
    main()
