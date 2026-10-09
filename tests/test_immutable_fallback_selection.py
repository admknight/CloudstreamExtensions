"""Verify immutable recovery locks cannot bypass prior-published trust."""
import copy
import hashlib
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from plan_per_plugin_selection import build_preview, canonical_sha256
from reconcile_integrity_review import validate_snapshots
from verified_immutable_fallback import validated_recovery_locks, restore_previous_from_lock

PREFIX = "https://raw.githubusercontent.com/owner/repo/builds/"
COMMIT = "a" * 40
PIN_PREFIX = "https://raw.githubusercontent.com/owner/repo/" + COMMIT + "/"


def sha(data):
    return "sha256-" + hashlib.sha256(data).hexdigest()


def item(name, payload=b"old", version=1):
    return {
        "internalName": name, "name": name, "url": PREFIX+name+".cs3",
        "version": version, "fileSize": len(payload), "fileHash": sha(payload), "status": 1,
    }


def origin(item):
    return {
        "plugin": item["internalName"], "sourceId": "upstream",
        "sourceIndex": "https://raw.githubusercontent.com/owner/repo/builds/plugins.json",
        "sourceRepository": "https://github.com/owner/repo",
        "packageUrl": item["url"], "originalName": item["internalName"],
    }


def index_for(previous, origins, name):
    p=next(x for x in previous if x["internalName"]==name)
    o=next(x for x in origins if x["plugin"]==name)
    return {
        "mode": "READ_ONLY_PINNED_RECOVERY_EVIDENCE",
        "autoPublicationAuthorized": False,
        "sourceCatalogDigest": canonical_sha256(previous),
        "sourceProvenanceDigest": canonical_sha256(origins),
        "publishedCount": len(previous),
        "immutableMatches": 1,
        "locks": [{
            "plugin": name, "sourceId": "upstream", "originalUrl": p["url"],
            "pinnedUrl": PIN_PREFIX+name+".cs3",
            "commitSha": COMMIT, "version": p["version"],
            "fileSize": p["fileSize"], "fileHash": p["fileHash"],
            "originalSourceIndex": o["sourceIndex"],
            "originalSourceRepository": o["sourceRepository"],
            "trustBasis": "matches_previously_published_sha256_and_size",
            "releaseAuthorVerified": False,
        }],
    }


def checker(stored):
    def verify(p):
        content = stored.get(p["url"])
        if content is None:
            return {"status": "error", "error":"404"}
        actual = "sha256-" + hashlib.sha256(content).hexdigest()
        length = len(content)
        if length != p.get("fileSize"):
            status="mismatch"
        elif p.get("fileHash") and actual != p["fileHash"]:
            status="mismatch"
        elif p.get("fileHash"):
            status="hash_verified"
        else:
            status="size_only_no_checksum"
        return {"status":status,"actualFileSize":length,"actualFileHash":actual}
    return verify


def catalog():
    previous = [item("P"+str(i)) for i in range(51)]
    candidate = copy.deepcopy(previous)
    candidate[0]=item("P0", b"new-not-trusted",version=2)
    old_provenance=[origin(p) for p in previous]
    new_provenance=[origin(p) for p in candidate]
    pin=PIN_PREFIX+"P0.cs3"
    storage={p["url"]:b"old" for p in previous}
    storage[previous[0]["url"]]=b"new-not-trusted"
    storage[pin]=b"old"
    return candidate,previous,new_provenance,old_provenance,storage


class ImmutableFallbackSelectionTests(unittest.TestCase):
    def test_previous_pinned_hash_survives_new_untrusted_binary(self):
        cand,prev,prov,oldprov,stored=catalog()
        locked=index_for(prev,oldprov,"P0")
        selected,selected_src,report,verified=build_preview(
            cand,prev,prov,oldprov,[],checker=checker(stored),workers=1,
            recovery_index=locked)
        self.assertEqual(report["selection"]["retainedImmutablePrevious"],1)
        self.assertEqual(report["selection"]["retainedThroughRecoveryLock"],1)
        self.assertEqual(report["selection"]["quarantinedExisting"],0)
        self.assertEqual(len(selected),51)
        self.assertEqual(selected[0]["url"], PIN_PREFIX+"P0.cs3")
        self.assertEqual(selected_src[0]["packageUrl"], PIN_PREFIX+"P0.cs3")
        self.assertEqual(selected[0]["fileHash"],prev[0]["fileHash"])
        self.assertEqual(report["incidents"][0]["disposition"],"retained_immutable_previous")
        self.assertTrue(report["incidents"][0]["fallbackThroughRecoveryLock"])
        self.assertFalse(report["automaticPublicationAuthorized"])
        validate_snapshots(cand,prev,prov,oldprov,selected,selected_src,report,verified)

    def test_tampered_recovery_lock_rejected(self):
        cand,prev,prov,oldprov,stored=catalog()
        for property,value in (
            ("fileHash",sha(b"malicious")), ("sourceId","other"),
            ("version",999),("commitSha","b"*40),
            ("trustBasis","trust-anything"),("releaseAuthorVerified",True)
        ):
            lock=index_for(prev,oldprov,"P0")
            lock["locks"][0][property]=value
            with self.assertRaisesRegex(ValueError,"does not match previous publication"):
                build_preview(cand,prev,prov,oldprov,[],
                              checker=checker(stored),workers=1,recovery_index=lock)

    def test_stale_published_manifest_snapshot_is_rejected(self):
        cand,prev,prov,oldprov,stored=catalog()
        lock=index_for(prev,oldprov,"P0")
        lock["sourceCatalogDigest"]="0"*64
        with self.assertRaisesRegex(ValueError,"stale"):
            build_preview(cand,prev,prov,oldprov,[],
                          checker=checker(stored),workers=1,recovery_index=lock)

    def test_pinned_content_changed_no_fallback_or_approval(self):
        cand,prev,prov,oldprov,stored=catalog()
        lock=index_for(prev,oldprov,"P0")
        stored[PIN_PREFIX+"P0.cs3"]=b"new-not-trusted"
        selected,_,report,_=build_preview(
            cand,prev,prov,oldprov,[],checker=checker(stored),workers=1,
            recovery_index=lock)
        self.assertEqual(report["selection"]["retainedImmutablePrevious"],0)
        self.assertEqual(report["selection"]["quarantinedExisting"],1)
        self.assertEqual(len(selected),50)
        self.assertNotIn("P0",[x["internalName"] for x in selected])

    def test_wrong_previous_provenance_snapshot_is_rejected(self):
        cand,prev,prov,oldprov,stored=catalog()
        lock=index_for(prev,oldprov,"P0")
        oldprov[0]["sourceId"]="other"
        with self.assertRaisesRegex(ValueError,"stale"):
            build_preview(cand,prev,prov,oldprov,[],checker=checker(stored),workers=1,
                          recovery_index=lock)

    def test_no_lock_remains_quarantined_and_does_not_approve_new_release(self):
        cand,prev,prov,oldprov,stored=catalog()
        result,_,summary,_=build_preview(
            cand,prev,prov,oldprov,[],checker=checker(stored),workers=1
        )
        self.assertEqual(len(result),50)
        self.assertEqual(summary["selection"]["quarantinedExisting"],1)

    def test_pinned_url_cannot_be_redirected_to_another_repository_in_metadata(self):
        cand,prev,prov,oldprov,stored=catalog()
        lock=index_for(prev,oldprov,"P0")
        lock["locks"][0]["pinnedUrl"]="https://raw.githubusercontent.com/attacker/repo/"+COMMIT+"/P0.cs3"
        with self.assertRaises(ValueError):
            validated_recovery_locks(lock,prev,oldprov)

    def test_reconciler_rejects_unreviewed_pin_mutation(self):
        cand,prev,prov,oldprov,stored=catalog()
        lock=index_for(prev,oldprov,"P0")
        selected,selected_src,summary,verified=build_preview(
            cand,prev,prov,oldprov,[],checker=checker(stored),workers=1,recovery_index=lock
        )
        selected[0]["version"]=999
        with self.assertRaises(ValueError):
            validate_snapshots(cand,prev,prov,oldprov,selected,selected_src,summary,verified)


if __name__=="__main__":
    unittest.main()
