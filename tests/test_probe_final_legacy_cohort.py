"""Final missing SHA-256 evidence must be original-source and byte-identical."""
import io
import sys
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import probe_final_legacy_cohort as m


def archive():
    b=io.BytesIO()
    with zipfile.ZipFile(b,"w") as z:
        z.writestr("payload",b"extension")
    return b.getvalue()


class Tests(unittest.TestCase):
    def test_original_commit_matches_current_published_byte(self):
        data=archive()
        p={"internalName":"Sample","version":2,"status":1,"fileSize":len(data),"url":"https://raw.githubusercontent.com/owner/old/builds/Sample.cs3"}
        source={"internalName":"Sample","version":2,"status":1,"fileSize":len(data)}
        def read(url):
            return data
        out=m.verify_one(p,source,"owner/new","a"*40,
                         "https://raw.githubusercontent.com/owner/new/"+"a"*40+"/Sample.cs3",
                         p["url"],read)
        self.assertFalse(out["approvedForPublication"])
        self.assertTrue(out["identicalToPublishedBytes"])
        self.assertEqual(len(out["sha256"]),71)

    def test_changed_original_bytes_are_held(self):
        data=archive()
        p={"internalName":"Sample","version":2,"status":1,"fileSize":len(data),"url":"https://raw.githubusercontent.com/owner/old/builds/Sample.cs3"}
        source={"internalName":"Sample","version":2,"status":1,"fileSize":len(data)}
        def read(url):
            return data[:-1]+b"x" if "/old/" in url else data
        with self.assertRaisesRegex(ValueError,"do not match"):
            m.verify_one(p,source,"owner/new","a"*40,
                         "https://raw.githubusercontent.com/owner/new/"+"a"*40+"/Sample.cs3",
                         p["url"],read)

    def test_inaccurate_manifest_size_is_held(self):
        data=archive()
        p={"internalName":"Sample","version":2,"status":1,"fileSize":len(data),"url":"https://raw.githubusercontent.com/owner/old/builds/Sample.cs3"}
        source={"internalName":"Sample","version":2,"status":1,"fileSize":len(data)+100}
        with self.assertRaisesRegex(ValueError,"manifest fileSize"):
            m.verify_one(p,source,"owner/new","a"*40,
                         "https://raw.githubusercontent.com/owner/new/"+"a"*40+"/Sample.cs3",
                         p["url"],lambda url:data)

    def test_wrong_original_status_rejected(self):
        data=archive()
        p={"internalName":"Sample","version":2,"status":1,"fileSize":len(data),"url":"https://raw.githubusercontent.com/owner/old/builds/Sample.cs3"}
        source={"internalName":"Sample","version":2,"status":2,"fileSize":len(data)}
        with self.assertRaisesRegex(ValueError,"identity/status/version"):
            m.verify_one(p,source,"owner/new","a"*40,
                         "https://raw.githubusercontent.com/owner/new/"+"a"*40+"/Sample.cs3",
                         p["url"],lambda url:data)

    def test_duplicate_original_manifest_identity_rejected(self):
        with self.assertRaisesRegex(ValueError,"duplicate"):
            m.index_entries([{"internalName":"X"},{"internalName":"X"}])

    def test_original_repo_source_is_not_a_release_approval(self):
        self.assertEqual(m.GITLAB_COMMIT,"05b8e0b8c7b3aa43665fc57c477862cc0888f917")
        self.assertEqual(sum(x[-1] for x in m.GITHUB_ORIGINAL)+len(m.GITLAB_NAMES),16)


if __name__=="__main__":
    unittest.main()
