"""Regression coverage for source-specific release transport; no network."""
import hashlib
import unittest
from urllib.request import Request
from unittest.mock import patch
from audit_package_integrity import (
    REPLAY_INDEX_URL, REPLAY_RELEASE_PREFIX, MAX_INDEX_BYTES, MAX_PACKAGE_BYTES,
    replay_release_module, ReplayReleaseRedirect, _get_response, verify_package,
)
from verify_candidate_integrity import evaluate

URL = REPLAY_RELEASE_PREFIX + 'T18-v2026.10.22.55/T18.cs3'
CONTENT = b'test bytes'
HASH = 'sha256-' + hashlib.sha256(CONTENT).hexdigest()


def entry(**changes):
    return {'internalName':'T18','url':URL,'fileSize':len(CONTENT),'fileHash':HASH,
            'version':55,'status':3,**changes}


def download(url, limit):
    if url != URL or limit != MAX_PACKAGE_BYTES:
        raise AssertionError('Unexpected download')
    return b'', len(CONTENT), HASH


class ReplayTransportTest(unittest.TestCase):
    def test_reviewed_publisher_asset_matches(self):
        self.assertEqual(replay_release_module(URL), 'T18')
        self.assertEqual(verify_package(entry(),download)['status'],'hash_verified')
        self.assertEqual(verify_package(entry(fileHash='sha256-'+'a'*64),download)['status'],'mismatch')

    def test_reject_wrong_identity_or_missing_metadata_without_download(self):
        for change in ({'internalName':'Other'}, {'fileHash':None}, {'fileHash':'invalid'},
                       {'fileSize':None}, {'fileSize':True}, {'fileSize':0}):
            with self.subTest(change=change):
                with patch('audit_package_integrity._get_response') as fetch:
                    self.assertEqual(verify_package(entry(**change))['status'],'error')
                    fetch.assert_not_called()

    def test_reject_unreviewed_urls(self):
        for url in [URL+'?x=1', URL+'#fragment',
                    URL.replace('aslafy-z','unreviewed'),
                    URL.replace('T18.cs3','FranceTV.cs3'),
                    URL.replace('T18-v2026.10.22.55','latest'),
                    URL.replace('https:','http:'),
                    URL.replace('github.com','github.com.evil.example'),
                    'https://release-assets.githubusercontent.com/a/T18.cs3',
                    REPLAY_INDEX_URL]:
            with self.subTest(url=url), patch('audit_package_integrity._get_response') as fetch:
                self.assertEqual(verify_package(entry(url=url))['status'],'error')
                fetch.assert_not_called()

    def test_release_only_allowed_in_intended_context(self):
        for url,limit in [(URL,MAX_INDEX_BYTES),(REPLAY_INDEX_URL,MAX_PACKAGE_BYTES),
                          (REPLAY_RELEASE_PREFIX+'catalog-dev/plugins.json',MAX_INDEX_BYTES),
                          ('https://release-assets.githubusercontent.com/a/T18.cs3',MAX_PACKAGE_BYTES)]:
            with self.subTest(url=url), patch('audit_package_integrity.urlopen') as old, patch('audit_package_integrity.build_opener') as new:
                with self.assertRaises(ValueError):
                    _get_response(url,limit)
                old.assert_not_called()
                new.assert_not_called()

    def test_strict_redirect(self):
        handler=ReplayReleaseRedirect()
        req=Request(URL)
        approved='https://release-assets.githubusercontent.com/path?sig=test'
        self.assertEqual(handler.redirect_request(req,None,302,'Found',{},approved).full_url,approved)
        for redirect in ['http://release-assets.githubusercontent.com/path',
                         'https://release-assets.githubusercontent.com.evil.example/path',
                         'https://user@release-assets.githubusercontent.com/path',
                         'https://release-assets.githubusercontent.com:443/path',
                         'https://example.com/path','https://github.com/other/repo']:
            with self.subTest(redirect=redirect), self.assertRaises(ValueError):
                handler.redirect_request(req,None,302,'Found',{},redirect)

    def test_verified_asset_is_not_an_approval(self):
        checked=verify_package(entry(),download)
        classification,_=evaluate(None,entry(),'','replay-preview',[],checked)
        self.assertEqual(classification,'blocked')
        self.assertEqual(entry()['status'],3)

if __name__=='__main__':
    unittest.main()
