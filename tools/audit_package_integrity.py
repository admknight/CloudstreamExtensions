#!/usr/bin/env python3
"""Read-only hourly integrity audit of MegaRepo's published CloudStream catalog.

Checks selected-source metadata for every published plugin and downloads one of six
rotating package partitions per run. This never writes production manifests.
"""
import argparse
import hashlib
import json
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

PREFIX = 'https://raw.githubusercontent.com/admknight/CloudstreamExtensions/refs/heads/'
PUBLISHED_URL = PREFIX + 'builds/plugins.json'
PROVENANCE_URL = PREFIX + 'builds/provenance.json'
SOURCES_URL = PREFIX + 'master/sources.json'
MAX_INDEX_BYTES = 20 * 1024 * 1024
MAX_PACKAGE_BYTES = 16 * 1024 * 1024
DIGEST_RE = re.compile(r'^sha256-[a-fA-F0-9]{64}$')
KEY_FIELDS = ('url', 'version', 'fileHash', 'fileSize', 'status')
USER_AGENT = 'AdamKnight-ReadOnly-IntegrityAudit/1.0'
# The currently published sources and packages use only these public hosts.
ALLOWED_HOSTS = {'raw.githubusercontent.com', 'gitlab.com'}


def _get_response(url, limit, *, timeout=22):
    parsed = urlparse(url)
    if parsed.scheme != 'https' or parsed.hostname not in ALLOWED_HOSTS or parsed.username or parsed.password:
        raise ValueError('Unsupported source host or non-HTTPS URL')
    error = None
    for attempt in range(2):
        try:
            request = Request(url, headers={'User-Agent': USER_AGENT, 'Accept': '*/*'})
            with urlopen(request, timeout=timeout) as response:
                if response.status != 200:
                    raise RuntimeError('Unexpected HTTP status ' + str(response.status))
                size = 0
                digest = hashlib.sha256()
                buffer = bytearray()
                while True:
                    chunk = response.read(65536)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > limit:
                        raise RuntimeError('Download exceeds safety limit')
                    digest.update(chunk)
                    if limit == MAX_INDEX_BYTES:
                        buffer.extend(chunk)
                return bytes(buffer), size, 'sha256-' + digest.hexdigest()
        except Exception as exc:
            error = exc
            if attempt == 0:
                time.sleep(0.8)
    raise RuntimeError(f'{type(error).__name__}: {error}') from error


def fetch_json(url):
    content, _, _ = _get_response(url, MAX_INDEX_BYTES)
    return json.loads(content)


def plugin_identity(plugin):
    if not isinstance(plugin, dict):
        return ''
    return str(plugin.get('internalName') or plugin.get('name') or '').strip().casefold()


def provenance_identity(row):
    if not isinstance(row, dict):
        return ''
    return str(row.get('plugin') or row.get('originalName') or '').strip().casefold()


def select_rotation(plugins, slot, buckets=6):
    if not 0 <= slot < buckets:
        raise ValueError('Invalid rotation slot')
    return [p for p in plugins if int.from_bytes(hashlib.sha256(plugin_identity(p).encode()).digest()[:8], 'big') % buckets == slot]


def compare_catalog(published, provenance, source_defs, upstream_catalogs):
    issues = []
    refs = {provenance_identity(p): p for p in provenance}
    defs = {s.get('id'): s for s in source_defs}
    for published_entry in published:
        key = plugin_identity(published_entry)
        provenance_entry = refs.get(key)
        if provenance_entry is None:
            issues.append({'plugin': key, 'problem': 'missing_provenance'})
            continue
        source_id = provenance_entry.get('sourceId')
        if source_id not in defs:
            issues.append({'plugin': key, 'problem': 'unknown_source', 'sourceId': source_id})
            continue
        upstream = upstream_catalogs.get(source_id)
        if upstream is None:
            issues.append({'plugin': key, 'problem': 'source_unavailable', 'sourceId': source_id})
            continue
        original = upstream.get(key)
        if original is None:
            issues.append({'plugin': key, 'problem': 'missing_upstream', 'sourceId': source_id})
            continue
        differences = {field: {'published': published_entry.get(field), 'upstream': original.get(field)}
                       for field in KEY_FIELDS if published_entry.get(field) != original.get(field)}
        if differences:
            issues.append({'plugin': key, 'problem': 'stale_metadata', 'sourceId': source_id,
                           'differences': differences})
    return issues


def verify_package(plugin, downloader=None):
    """Return one package check. URL/size/hash checks are against *published* metadata."""
    downloader = downloader or _get_response
    name = plugin_identity(plugin)
    url = plugin.get('url', '')
    result = {'plugin': name, 'url': url, 'status': 'error'}
    try:
        parsed = urlparse(url)
        if parsed.scheme != 'https' or parsed.hostname not in ALLOWED_HOSTS or not parsed.path.lower().endswith('.cs3'):
            raise ValueError('Invalid HTTPS .cs3 package URL')
        _, actual_size, actual_hash = downloader(url, MAX_PACKAGE_BYTES)
        if actual_size == 0:
            raise ValueError('Empty downloaded package')
        result.update({'actualFileSize': actual_size, 'actualFileHash': actual_hash})
        expected_size = plugin.get('fileSize')
        if expected_size is not None and actual_size != expected_size:
            result.update({'status': 'mismatch', 'reason': 'fileSize', 'expectedFileSize': expected_size})
            return result
        expected_hash = plugin.get('fileHash')
        if expected_hash:
            if not DIGEST_RE.fullmatch(expected_hash) or actual_hash.lower() != expected_hash.lower():
                result.update({'status': 'mismatch', 'reason': 'fileHash', 'expectedFileHash': expected_hash})
                return result
            result['status'] = 'hash_verified'
        else:
            result['status'] = 'size_only_no_checksum'
    except Exception as exc:
        result['error'] = f'{type(exc).__name__}: {exc}'
    return result


def _source_index(entries):
    if not isinstance(entries, list):
        raise ValueError('Source index is not a plugin list')
    result = {}
    for item in entries:
        key = plugin_identity(item)
        if not key:
            continue
        if key in result:
            raise ValueError('Duplicate identity in source catalog: ' + key)
        result[key] = item
    return result


def audit(*, only='', full=False, buckets=6, slot=None, fetcher=None, downloader=None):
    fetcher = fetcher or fetch_json
    ts = datetime.now(timezone.utc)
    chosen_slot = ts.hour % buckets if slot is None else slot
    published = fetcher(PUBLISHED_URL)
    provenance = fetcher(PROVENANCE_URL)
    config = fetcher(SOURCES_URL)
    if not isinstance(published, list) or not isinstance(provenance, list) or not isinstance(config, dict):
        raise ValueError('Unexpected production JSON structure')
    sources = config.get('sources')
    if not isinstance(sources, list) or not published or not provenance:
        raise ValueError('Missing catalog, provenance, or sources')
    if only:
        published = [p for p in published if plugin_identity(p) == only.strip().casefold()]
        if not published:
            raise ValueError('Requested plugin is not in the published catalog')
    identities = [plugin_identity(p) for p in published]
    if not all(identities) or len(set(identities)) != len(identities):
        raise ValueError('Invalid or duplicated published plugin identities')
    provenance_by_id = {provenance_identity(p): p for p in provenance}
    ids = {provenance_by_id.get(key, {}).get('sourceId') for key in identities}
    source_urls = {s.get('id'): s.get('index') for s in sources if s.get('id') in ids}
    source_errors = []
    upstream_catalogs = {}

    def pull_source(pair):
        source_id, url = pair
        return source_id, _source_index(fetcher(url))

    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = {pool.submit(pull_source, pair): pair[0] for pair in source_urls.items()}
        for future in as_completed(futures):
            source_id = futures[future]
            try:
                k, entries = future.result()
                upstream_catalogs[k] = entries
            except Exception as exc:
                source_errors.append({'sourceId': source_id, 'error': f'{type(exc).__name__}: {exc}'})
    diffs = compare_catalog(published, provenance, sources, upstream_catalogs)
    to_check = published if full or only else select_rotation(published, chosen_slot, buckets)
    packages = []
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(verify_package, p, downloader): plugin_identity(p) for p in to_check}
        for future in as_completed(futures):
            packages.append(future.result())
    packages.sort(key=lambda p: p['plugin'])
    return {
        'checkedAtUTC': ts.isoformat(), 'readOnly': True, 'publishedCount': len(published),
        'upstreamSourceIndexesChecked': len(upstream_catalogs),
        'sourceErrors': source_errors, 'metadataDrift': diffs,
        'scan': {'mode': 'all' if full or only else 'rotation', 'slot': chosen_slot,
                 'buckets': buckets, 'checked': len(packages),
                 'hashVerified': sum(p['status'] == 'hash_verified' for p in packages),
                 'withoutHashes': sum(p['status'] == 'size_only_no_checksum' for p in packages),
                 'failed': sum(p['status'] in ('error', 'mismatch') for p in packages)},
        'packageProblems': [p for p in packages if p['status'] in ('error', 'mismatch')],
        'pass': not (source_errors or diffs or any(p['status'] in ('error', 'mismatch') for p in packages)),
    }


def summary(report):
    scan = report['scan']
    lines = ['# MegaRepo package-integrity audit', '',
             f"- Checked: {report['checkedAtUTC']}",
             f"- Result: {'PASS' if report['pass'] else 'FAIL - investigation needed'}",
             f"- Published plugins evaluated for metadata: {report['publishedCount']}",
             f"- Upstream source indexes fetched: {report['upstreamSourceIndexesChecked']}",
             f"- Rotation: {scan['slot'] + 1} / {scan['buckets']} ({scan['mode']})",
             f"- Packages downloaded: {scan['checked']}",
             f"- Matching SHA-256 digests: {scan['hashVerified']}",
             f"- Size-only checks (no upstream checksum): {scan['withoutHashes']}",
             f"- Package failures: {scan['failed']}",
             f"- Upstream metadata drift: {len(report['metadataDrift'])}",
             f"- Source fetch failures: {len(report['sourceErrors'])}",
             '', 'This audit is read-only. Source availability and checksum checks do not prove playback.', '']
    for title, data in [('Upstream metadata drift', report['metadataDrift']),
                        ('Package failures', report['packageProblems']),
                        ('Upstream index fetch failures', report['sourceErrors'])]:
        if data:
            lines.extend([f'## {title}', '', '```json', json.dumps(data[:30], indent=2, sort_keys=True), '```', ''])
            if len(data) > 30:
                lines.append(f'...and {len(data) - 30} more; see the uploaded audit JSON.\n')
    return '\n'.join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--full', action='store_true', help='Download all packages instead of rotating 1/6')
    parser.add_argument('--only', default='', help='Only check one internal plugin name')
    parser.add_argument('--slot', type=int, help='Rotation index 0-5; default is current UTC hour modulo 6')
    parser.add_argument('--json-report', default='audit-report.json')
    parser.add_argument('--markdown-report', default='audit-summary.md')
    args = parser.parse_args(argv)
    try:
        data = audit(only=args.only, full=args.full, slot=args.slot)
    except Exception as exc:
        data = {'checkedAtUTC': datetime.now(timezone.utc).isoformat(), 'pass': False,
                'fatalError': f'{type(exc).__name__}: {exc}'}
    Path(args.json_report).write_text(json.dumps(data, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    if 'scan' in data:
        report = summary(data)
    else:
        report = f"# MegaRepo package-integrity audit\n\nFAIL: {data['fatalError']}\n"
    Path(args.markdown_report).write_text(report + '\n', encoding='utf-8')
    print(report)
    return 0 if data['pass'] else 1


if __name__ == '__main__':
    sys.exit(main())
