"""Regression checks for passive redirects, hostname drift and error types."""
import io
import socket
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError, URLError

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import custom_provider_health as health


class FakeResponse:
    status = 200

    def __init__(self, final):
        self.final = final

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return None

    def geturl(self):
        return self.final

    def read(self, size):
        return b"<html>ok</html>"


class DriftTests(unittest.TestCase):
    def test_hostname_change_is_not_a_hard_failure(self):
        url = "https://cinemalux.baby/"
        with patch.object(health, "urlopen", return_value=FakeResponse("https://cinemalux.beauty/")):
            row = health.check({"module": "Cinemaluxe", "url": url}, timeout=1)
        self.assertEqual(row["state"], "ok")
        self.assertTrue(row["redirected"])
        self.assertTrue(row["hostChanged"])
        self.assertEqual(row["configuredHost"], "cinemalux.baby")
        self.assertEqual(row["finalHost"], "cinemalux.beauty")
        self.assertEqual(row["outcome"], "ok")
        self.assertEqual(row["url"], url)

    def test_same_hostname_path_redirect_is_not_host_drift(self):
        row = health.navigation_details("https://example.com/", "https://example.com/welcome")
        self.assertTrue(row["redirected"])
        self.assertFalse(row["hostChanged"])
        self.assertEqual(row["finalHost"], "example.com")

    def test_no_redirect_and_invalid_url_fail_closed(self):
        result = health.navigation_details("https://example.com", "https://example.com/")
        self.assertFalse(result["redirected"])
        self.assertFalse(result["hostChanged"])
        self.assertFalse(health.navigation_details("invalid", "alsoinvalid")["hostChanged"])

    def test_dns_and_timeout_are_distinguished(self):
        checks = [
            (URLError(socket.gaierror(-2, "Name or service not known")), "dns_error"),
            (URLError(TimeoutError("timed out")), "timeout"),
            (URLError("connection reset by peer"), "network_error"),
        ]
        for exc, reason in checks:
            with self.subTest(reason=reason), patch.object(health, "urlopen", side_effect=exc), \
                 patch.object(health.time, "sleep"):
                row = health.check({"module": "M", "url": "https://example.com"}, timeout=1, attempts=2)
                self.assertEqual(row["state"], "fail")
                self.assertEqual(row["outcome"], reason)
                self.assertEqual(row["attempts"], 2)

    def test_http_restriction_not_domain_failure(self):
        exc = HTTPError("https://example.com/", 403, "Forbidden", {}, None)
        with patch.object(health, "urlopen", side_effect=exc):
            row = health.check({"module": "M", "url": "https://example.com/"}, timeout=1)
        self.assertEqual(row["state"], "restricted")
        self.assertEqual(row["outcome"], "http_restricted")
        self.assertFalse(row["hostChanged"])
        self.assertEqual(row["httpStatus"], 403)

    def test_history_includes_host_drift_without_retroactive_mutation(self):
        old = {"generatedAt": "2026-10-09 01:30:00 UTC",
               "summary": {"ok": 1, "restricted": 0, "failed": 0},
               "providers": [{
                   "module": "M", "state": "ok", "configuredUrl": "https://old.example",
                   "finalUrl": "https://new.example", "httpStatus": 200, "error": "",
               }]}
        now = datetime(2026, 10, 10, tzinfo=timezone.utc)
        trend = health.build_window([old], now, 7)
        self.assertEqual(trend["hostChanged"], 1)
        self.assertEqual(trend["redirected"], 1)
        self.assertEqual(trend["providers"][0]["hostChanged"], 1)
        self.assertNotIn("hostChanged", old["providers"][0])

    def test_compact_snapshot_preserves_new_fields(self):
        row = {"module": "Test", "state": "ok", "url": "https://old.com",
               "finalUrl": "https://new.com", "outcome": "ok",
               "hostChanged": True, "redirected": True,
               "configuredHost": "old.com", "finalHost": "new.com"}
        payload = health.compact_provider(row)
        self.assertTrue(payload["hostChanged"])
        self.assertEqual(payload["finalHost"], "new.com")
        content = health.render_markdown([row | {"httpStatus": 200}], "2026-10-10 00:00:00 UTC")
        self.assertIn("Hostname changes: **1**", content)
        self.assertIn("old.com", content)


if __name__ == "__main__":
    unittest.main()
