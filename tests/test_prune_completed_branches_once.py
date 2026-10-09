"""Offline safety regression checks for one-time completed-branch cleanup."""
import importlib.util
import pathlib
import unittest
from types import SimpleNamespace
from unittest.mock import patch

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "tools" / "prune_completed_branches_once.py"
spec = importlib.util.spec_from_file_location("cleanup", SCRIPT)
cleanup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cleanup)


def data(name, sha="a" * 40):
    branch = {"name": name, "protected": False, "commit": {"sha": sha}}
    pr = {
        "state": "closed", "merged_at": "2026-10-09T20:00:00Z",
        "head": {"ref": name, "sha": sha, "repo": {"full_name": cleanup.REPO}},
        "base": {"ref": "master", "repo": {"full_name": cleanup.REPO}},
    }
    return branch, pr


class CleanupTests(unittest.TestCase):
    def test_exact_allowlist_and_preserved_branches_do_not_overlap(self):
        values = cleanup.targets()
        self.assertEqual(len(cleanup.COMPLETED), 23)
        self.assertEqual(len(values), 24)
        self.assertEqual(len({x[1] for x in values}), len(values))
        self.assertFalse({x[1] for x in values}.intersection(cleanup.PRESERVE))
        self.assertEqual(values[-1][0], cleanup.SELF_PR)
        for number, name, sha in values[:-1]:
            self.assertGreater(number, 0)
            self.assertEqual(len(sha), 40)
            self.assertTrue(all(c in "0123456789abcdef" for c in sha))

    def test_only_original_merged_pr_head_is_eligible(self):
        name, sha = cleanup.COMPLETED[60]
        b, p = data(name, sha)
        self.assertTrue(cleanup.is_eligible(name, sha, b, p, set()))
        self.assertFalse(cleanup.is_eligible(name, sha, b, p, {name}))
        self.assertFalse(cleanup.is_eligible(name, "b" * 40, b, p, set()))
        b["commit"]["sha"] = "b" * 40
        self.assertFalse(cleanup.is_eligible(name, sha, b, p, set()))

    def test_never_delete_protected_active_or_unmerged_work(self):
        for name in cleanup.PRESERVE:
            b, p = data(name)
            with self.subTest(name=name):
                self.assertFalse(cleanup.is_eligible(name, None, b, p, set()))
        name, sha = cleanup.COMPLETED[59]
        b, p = data(name, sha)
        for alteration in ("merged_at", "state", "head", "base", "protected", "name"):
            with self.subTest(alteration=alteration):
                b1, p1 = data(name, sha)
                if alteration == "merged_at":
                    p1["merged_at"] = None
                elif alteration == "state":
                    p1["state"] = "open"
                elif alteration == "head":
                    p1["head"]["repo"]["full_name"] = "some/other"
                elif alteration == "base":
                    p1["base"]["ref"] = "builds"
                elif alteration == "protected":
                    b1["protected"] = True
                elif alteration == "name":
                    b1["name"] = "replacement"
                self.assertFalse(cleanup.is_eligible(name, sha, b1, p1, set()))

    def test_dry_run_does_not_delete_even_if_valid(self):
        name, sha = cleanup.COMPLETED[60]
        b, p = data(name, sha)
        def fake_api(path):
            if "pulls?state=open" in path:
                return []
            if "/branches/" in path:
                return b
            if path.endswith("/pulls/60"):
                return p
            raise AssertionError(path)
        with patch.object(cleanup, "targets", lambda: [(60, name, sha)]), \
             patch.object(cleanup, "gh_json", side_effect=fake_api), \
             patch.object(cleanup.subprocess, "run") as execute:
            report = cleanup.audit_and_prune(apply=False)
            self.assertEqual(report["results"][0]["result"], "eligible_dry_run")
            execute.assert_not_called()

    def test_apply_requires_master_context_and_rechecks_head(self):
        name, sha = cleanup.COMPLETED[60]
        b, p = data(name, sha)
        seen = {"branches": 0}
        def fake_api(path):
            if "pulls?state=open" in path:
                return []
            if "/branches/" in path:
                seen["branches"] += 1
                return b
            if path.endswith("/pulls/60"):
                return p
            raise AssertionError(path)
        with patch.object(cleanup, "targets", lambda: [(60, name, sha)]), \
             patch.object(cleanup, "confirm_context") as context, \
             patch.object(cleanup, "gh_json", side_effect=fake_api), \
             patch.object(cleanup.subprocess, "run", return_value=SimpleNamespace(returncode=0)) as run:
            result = cleanup.audit_and_prune(apply=True)
            context.assert_called_once()
            self.assertEqual(seen["branches"], 2)
            self.assertEqual(result["results"][0]["result"], "deleted")
            self.assertIn("DELETE", run.call_args.args[0])

    def test_refuses_apply_outside_verified_master_push(self):
        with patch.dict(cleanup.os.environ, {
            "GITHUB_REPOSITORY": cleanup.REPO,
            "GITHUB_EVENT_NAME": "pull_request",
            "GITHUB_REF": "refs/heads/master",
            "GITHUB_SHA": "a" * 40,
        }):
            with self.assertRaisesRegex(RuntimeError, "outside a master-branch push"):
                cleanup.confirm_context()

    def test_master_changed_since_trigger_blocks_cleanup(self):
        with patch.dict(cleanup.os.environ, {
            "GITHUB_REPOSITORY": cleanup.REPO,
            "GITHUB_EVENT_NAME": "push",
            "GITHUB_REF": "refs/heads/master",
            "GITHUB_SHA": "a" * 40,
        }):
            with patch.object(cleanup, "gh_json", return_value={"object": {"sha": "b" * 40}}):
                with self.assertRaisesRegex(RuntimeError, "advanced"):
                    cleanup.confirm_context()


if __name__ == "__main__":
    unittest.main()
