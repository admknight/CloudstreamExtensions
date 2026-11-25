import hashlib
import io
import json
import unittest
from zipfile import ZipFile
from audit_screenshot_expansion import audit, check_package, pin_package, raw_url

S = {'name':'demo','repo':'Owner/Repo','branch':'builds','commit':'a'*40,'path':'plugins.json','license_metadata':'MIT','total':2,'overlap':1}
B = {'repo':'Owner/Base','commit':'b'*40,'path':'plugins.json','identities':1}


def zip_bytes():
    buf = io.BytesIO()
    with ZipFile(buf,'w') as f:
        f.writestr('classes.dex',b'dummy test only')
    return buf.getvalue()


class AuditTest(unittest.TestCase):
    def setUp(self):
        self.data = zip_bytes()
        self.entry = {'internalName':'New','url':'https://raw.githubusercontent.com/owner/repo/refs/heads/builds/New%20Plugin.cs3?version=2',
                      'fileSize':len(self.data),'fileHash':'sha256-'+hashlib.sha256(self.data).hexdigest(),'status':1,'version':2}

    def test_pin(self):
        self.assertEqual(pin_package(S,self.entry['url']), 'https://raw.githubusercontent.com/Owner/Repo/'+'a'*40+'/New%20Plugin.cs3')

    def test_reject_other_origins_and_traversal(self):
        for url in ['https://evil.example/Owner/Repo/builds/x.cs3','https://raw.githubusercontent.com/Other/Repo/builds/x.cs3',
                    'https://raw.githubusercontent.com/Owner/Repo/builds/%2e%2e/x.cs3','https://raw.githubusercontent.com/Owner/Repo/main/x.cs3',
                    'https://raw.githubusercontent.com/Owner/Repo/builds/a%2fb.cs3']:
            with self.subTest(url=url), self.assertRaises(ValueError):
                pin_package(S,url)

    def test_valid_and_missing_checksum_and_mismatch(self):
        fetch = lambda *_: self.data
        self.assertEqual(check_package(S,self.entry,fetch)['result'],'hash_verified')
        self.assertFalse(check_package(S,self.entry,fetch)['approved'])
        self.assertEqual(check_package(S,{**self.entry,'fileHash':None},fetch)['result'],'declared_sha256_missing_or_invalid')
        self.assertEqual(check_package(S,{**self.entry,'fileHash':'sha256-'+'f'*64},fetch)['result'],'sha256_mismatch')
        self.assertEqual(check_package(S,{**self.entry,'fileSize':999},fetch)['result'],'size_mismatch_or_missing')
        self.assertEqual(check_package(S,self.entry,lambda *_:b'not a zip')['result'],'error')

    def test_count_reconciliation_and_fail_closed(self):
        snapshot={'baseline':B,'sources':[S],'expected':{'rows':2,'overlap':1,'candidates':1,'distinct':1},'excluded_from_byte_audit':[]}
        files={raw_url(B['repo'],B['commit'],B['path']):json.dumps([{'internalName':'Known'}]).encode(),
               raw_url(S['repo'],S['commit'],S['path']):json.dumps([{'internalName':'known'},self.entry]).encode(),
               pin_package(S,self.entry['url']):self.data}
        report=audit(snapshot,lambda url,limit: files[url],1)
        self.assertTrue(report['coverageComplete'])
        self.assertEqual(report['packageResults'],{'hash_verified':1})
        self.assertEqual(report['approvedCount'],0)
        del files[raw_url(S['repo'],S['commit'],S['path'])]
        self.assertFalse(audit(snapshot,lambda url,limit:files[url],1)['coverageComplete'])

if __name__ == '__main__':
    unittest.main()
