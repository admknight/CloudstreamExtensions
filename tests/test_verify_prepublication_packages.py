import hashlib
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
from verify_prepublication_packages import check_release

DATA=b"known"
HASH="sha256-"+hashlib.sha256(DATA).hexdigest()
BAD="sha256-"+"0"*64


def fixture():
    plugins=[]
    origins=[]
    for i in range(50):
        key="provider"+str(i)
        url="https://raw.githubusercontent.com/owner/repo/"+"a"*40+"/"+key+".cs3"
        plugins.append({"internalName":key,"url":url,"fileSize":len(DATA),"fileHash":HASH})
        origins.append({"plugin":key,"sourceId":"source","packageUrl":url})
    return plugins, origins, {"releaseEligible":True,"uniquePlugins":50,
                             "deferredUnverified":[],
                             "integrityHealth":{"unverifiedPreviousCarried":0},
                             "trustedPolicyUpgrades":[],"quarantineIncidents":[]}


def good(row):
    return {"status":"hash_verified","actualFileSize":len(DATA),"actualFileHash":HASH}


def bad(row):
    if row["internalName"]=="provider1":
        return {"status":"mismatch","reason":"fileHash","actualFileSize":len(DATA),
                "actualFileHash":BAD}
    return good(row)


class PrepublicationTests(unittest.TestCase):
    def test_all_50_verified(self):
        result=check_release(*fixture(),checker=good,workers=2)
        self.assertEqual(result["hashVerified"],50)
        self.assertTrue(result["fullByteIntegrityPassed"])

    def test_one_legacy_failure_explicitly_reported(self):
        a,b,r=fixture()
        r["deferredUnverified"]=[{"plugin":"provider1"}]
        r["integrityHealth"]["unverifiedPreviousCarried"]=1
        result=check_release(a,b,r,checker=bad,workers=2)
        self.assertEqual(result["hashVerified"],49)
        self.assertEqual(result["knownLegacyFailures"][0]["plugin"],"provider1")
        self.assertFalse(result["fullByteIntegrityPassed"])

    def test_unlisted_failure_blocks_publication(self):
        with self.assertRaisesRegex(ValueError,"Unexpected published-byte mismatch"):
            check_release(*fixture(),checker=bad,workers=2)

    def test_unverified_legacy_ceiling_blocks(self):
        a,b,r=fixture()
        r["deferredUnverified"]=[{"plugin":"provider1"},{"plugin":"provider2"}]
        r["integrityHealth"]["unverifiedPreviousCarried"]=2
        with self.assertRaisesRegex(ValueError,"exceed release allowance"):
            check_release(a,b,r,checker=bad,max_deferred=1,workers=2)

    def test_cannot_hide_approved_upgrades_in_exception(self):
        a,b,r=fixture()
        r["deferredUnverified"]=[{"plugin":"provider1"}]
        r["integrityHealth"]["unverifiedPreviousCarried"]=1
        r["trustedPolicyUpgrades"]=[{"plugin":"provider1"}]
        with self.assertRaisesRegex(ValueError,"mask"):
            check_release(a,b,r,checker=bad,workers=2)

    def test_provenance_mismatch_blocks(self):
        a,b,r=fixture()
        b[2]["packageUrl"]+="?new=true"
        with self.assertRaisesRegex(ValueError,"provenance"):
            check_release(a,b,r,checker=good,workers=2)

    def test_unchanged_previous_provenance_url_inconsistency_is_reported(self):
        a,b,r=fixture()
        b[2]["packageUrl"]+="?legacy=1"
        from copy import deepcopy
        out=check_release(a,b,r,checker=good,workers=2,
                          previous_plugins=deepcopy(a),
                          previous_provenance=deepcopy(b))
        self.assertEqual(out["unchangedPreviousUrlProvenanceDiscrepancies"],["provider2"])
        self.assertTrue(out["fullByteIntegrityPassed"])

    def test_new_provenance_url_mismatch_cannot_be_grandfathered(self):
        a,b,r=fixture()
        from copy import deepcopy
        old_plugins,old_sources=deepcopy(a),deepcopy(b)
        b[2]["packageUrl"]+="?new=1"
        with self.assertRaisesRegex(ValueError,"New catalog/provenance"):
            check_release(a,b,r,checker=good,workers=2,
                          previous_plugins=old_plugins,previous_provenance=old_sources)

    def test_missing_catalog_member_blocks(self):
        a,b,r=fixture()
        a.pop()
        with self.assertRaisesRegex(ValueError,"[Ii]ncomplete or inconsistent"):
            check_release(a,b,r,checker=good,workers=2)


if __name__=="__main__":
    unittest.main()
