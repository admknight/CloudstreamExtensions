import hashlib
import importlib.util
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
import json

SCRIPT = Path(__file__).resolve().parents[1] / 'tools' / 'audit_package_integrity.py'
spec = importlib.util.spec_from_file_location('audit_package_integrity', SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def plugin(name='Anichi', content=b'new package'):
    return {'internalName': name, 'name': name, 'url': f'https://raw.githubusercontent.com/demo/{name}.cs3',
            'version': 28, 'status': 1, 'fileSize': len(content),
            'fileHash': 'sha256-' + hashlib.sha256(content).hexdigest()}


class AuditTests(unittest.TestCase):
    def test_snapshot_files_are_consistent_without_raw_branch_cache(self):
        entry = plugin()
        upstream_url = 'https://raw.githubusercontent.com/demo/plugins.json'
        def fetcher(url):
            if url == audit.SOURCES_URL:
                return {'sources': [{'id': 'demo', 'index': upstream_url}]}
            if url == upstream_url:
                return [entry]
            raise AssertionError('Snapshot must not fetch independent mutable raw URLs: ' + url)
        def downloader(url, limit):
            content = b'new package'
            return b'', len(content), 'sha256-' + hashlib.sha256(content).hexdigest()
        with TemporaryDirectory() as folder:
            published = Path(folder) / 'plugins.json'
            provenance = Path(folder) / 'provenance.json'
            published.write_text(json.dumps([entry]), encoding='utf-8')
            provenance.write_text(json.dumps([{'plugin': 'Anichi', 'sourceId': 'demo'}]), encoding='utf-8')
            report = audit.audit(only='Anichi', published_file=published,
                                 provenance_file=provenance, fetcher=fetcher, downloader=downloader)
        self.assertTrue(report['pass'])
        self.assertEqual(report['publishedCount'], 1)

    def test_snapshot_files_must_be_supplied_together(self):
        with self.assertRaisesRegex(ValueError, 'supplied together'):
            audit.audit(published_file=Path('plugins.json'), fetcher=lambda _: None)

    def test_live_hash_match(self):
        entry = plugin()
        fake = lambda url, limit: (b'', len(b'new package'), 'sha256-' + hashlib.sha256(b'new package').hexdigest())
        self.assertEqual(audit.verify_package(entry, fake)['status'], 'hash_verified')

    def test_detects_stale_hash_same_version(self):
        old = plugin(content=b'old package')
        new = plugin(content=b'new package')
        issues = audit.compare_catalog([old], [{'plugin': 'Anichi', 'sourceId': 'phisher'}],
                                       [{'id': 'phisher', 'index': 'https://example.com/plugins.json'}],
                                       {'phisher': {'anichi': new}})
        self.assertEqual(issues[0]['problem'], 'stale_metadata')
        self.assertEqual(issues[0]['differences']['fileHash']['upstream'], new['fileHash'])
        self.assertNotIn('version', issues[0]['differences'])

    def test_url_spaces_normalized_like_aggregator(self):
        upstream = plugin()
        upstream['url'] = 'https://raw.githubusercontent.com/demo/Ani Chi.cs3'
        published = dict(upstream)
        published['url'] = upstream['url'].replace(' ', '%20')
        issues = audit.compare_catalog([published], [{'plugin': 'Anichi', 'sourceId': 'phisher'}],
                                       [{'id': 'phisher', 'index': 'https://raw.githubusercontent.com/demo/plugins.json'}],
                                       {'phisher': {'anichi': upstream}})
        self.assertEqual(issues, [])

    def test_binary_mismatch(self):
        entry = plugin(content=b'old package')
        fake = lambda url, limit: (b'', len(b'old package'), 'sha256-' + hashlib.sha256(b'new package').hexdigest())
        self.assertEqual(audit.verify_package(entry, fake)['status'], 'mismatch')
        self.assertEqual(audit.verify_package(entry, fake)['reason'], 'fileHash')

    def test_size_mismatch(self):
        entry = plugin()
        fake = lambda url, limit: (b'', 123, entry['fileHash'])
        self.assertEqual(audit.verify_package(entry, fake)['reason'], 'fileSize')

    def test_missing_hash_is_size_only(self):
        entry = plugin()
        del entry['fileHash']
        fake = lambda url, limit: (b'', entry['fileSize'], 'sha256-' + 'a' * 64)
        self.assertEqual(audit.verify_package(entry, fake)['status'], 'size_only_no_checksum')

    def test_missing_source_detected(self):
        entry = plugin()
        issues = audit.compare_catalog([entry], [{'plugin': 'Anichi', 'sourceId': 'phisher'}],
                                       [{'id': 'phisher', 'index': 'https://example.com'}], {})
        self.assertEqual(issues[0]['problem'], 'source_unavailable')

    def test_package_network_failure(self):
        def broken(url, limit):
            raise TimeoutError('request timed out')
        checked = audit.verify_package(plugin(), broken)
        self.assertEqual(checked['status'], 'error')
        self.assertIn('TimeoutError', checked['error'])

    def test_rotation_covers_all_once(self):
        entries = [plugin(name='P' + str(i)) for i in range(120)]
        seen = [p['internalName'] for slot in range(6) for p in audit.select_rotation(entries, slot, 6)]
        self.assertEqual(set(seen), {x['internalName'] for x in entries})
        self.assertEqual(len(seen), len(entries))

    def test_metadata_current_pass(self):
        entry = plugin()
        issues = audit.compare_catalog([entry], [{'plugin': 'Anichi', 'sourceId': 'phisher'}],
                                       [{'id': 'phisher', 'index': 'https://example.com'}],
                                       {'phisher': {'anichi': entry}})
        self.assertEqual(issues, [])

    def test_rejects_unapproved_https_host(self):
        entry = plugin()
        entry['url'] = 'https://example.com/Anichi.cs3'
        self.assertEqual(audit.verify_package(entry)['status'], 'error')

    def test_rejects_bad_url(self):
        entry = plugin()
        entry['url'] = 'http://example.com/a.cs3'
        self.assertEqual(audit.verify_package(entry)['status'], 'error')


if __name__ == '__main__':
    unittest.main()
