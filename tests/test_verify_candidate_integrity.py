import importlib.util
import hashlib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "tools"
spec = importlib.util.spec_from_file_location("verify_candidate_integrity", ROOT / "verify_candidate_integrity.py")
import sys
sys.path.insert(0, str(ROOT))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

def plugin(data=b"good", **changes):
    row = {"internalName": "Demo", "name": "Demo", "url": "https://raw.githubusercontent.com/demo/Demo.cs3",
           "version": 1, "status": 1, "fileSize": len(data)}
    row.update(changes)
    return row

def outcome(row, data=b"good"):
    return {"status": "hash_verified" if row.get("fileHash") else "size_only_no_checksum",
            "actualFileSize": len(data), "actualFileHash": "sha256-" + hashlib.sha256(data).hexdigest()}

def run(old, new, *, old_source="demo", new_source="demo", approvals=None, actual=b"good"):
    return module.gate(
        [new], [old] if old else [], [{"plugin": "Demo", "sourceId": new_source}],
        [{"plugin": "Demo", "sourceId": old_source}] if old else [], approvals or [],
        checker=lambda row: outcome(row, actual), workers=1
    )

class GateTests(unittest.TestCase):
    def test_unchanged_legacy_allowed(self):
        p = plugin()
        self.assertTrue(run(p, p)["passed"])

    def test_metadata_change_without_hash_blocked(self):
        self.assertFalse(run(plugin(), plugin(version=2))["passed"])

    def test_gdindex_size_mismatch_blocked(self):
        original = plugin(fileSize=15937)
        changed = plugin(fileSize=17206)
        self.assertFalse(run(original, changed, actual=b"new")["passed"])

    def test_binary_size_mismatch_blocked(self):
        p = plugin()
        self.assertFalse(module.gate([p], [p], [{"plugin":"Demo","sourceId":"demo"}],
            [{"plugin":"Demo","sourceId":"demo"}], [], checker=lambda p: {"status":"mismatch","reason":"fileSize"})["passed"])

    def test_unchanged_digest_verified(self):
        digest = outcome(plugin())["actualFileHash"]
        p = plugin(fileHash=digest)
        self.assertTrue(run(p, p)["passed"])

    def test_approved_change_allowed(self):
        old = plugin()
        new = plugin(version=2)
        approval = {"plugin": "Demo", "sourceId": "demo", "url":new["url"],
                    "version":2, "fileSize":4, "fileHash":outcome(new)["actualFileHash"]}
        self.assertTrue(run(old, new, approvals=[approval])["passed"])

    def test_approval_wrong_source_blocked(self):
        old = plugin()
        new = plugin(version=2)
        approval = {"plugin": "Demo", "sourceId": "attacker", "url":new["url"],
                    "version":2, "fileSize":4, "fileHash":outcome(new)["actualFileHash"]}
        self.assertFalse(run(old, new, approvals=[approval])["passed"])

    def test_existing_same_hash_changed_version(self):
        digest = outcome(plugin())["actualFileHash"]
        old = plugin(fileHash=digest)
        new = plugin(fileHash=digest, version=2)
        self.assertTrue(run(old, new)["passed"])

    def test_missing_provenance_fails(self):
        with self.assertRaises(ValueError):
            module.gate([], [plugin()], [], [], [], checker=lambda r: outcome(r))

if __name__ == "__main__":
    unittest.main()
