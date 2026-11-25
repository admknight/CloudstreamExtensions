#!/usr/bin/env python3
"""One-time read-only inventory audit; no package execution or publication."""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
from pathlib import Path
import re
from urllib.parse import quote, unquote, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler
from zipfile import ZipFile

HOST = 'raw.githubusercontent.com'
REPO = re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z')
COMMIT = re.compile(r'[a-f0-9]{40}\Z')
DIGEST = re.compile(r'sha256-[a-fA-F0-9]{64}\Z')
MAX_INDEX = 20 * 1024 * 1024
MAX_PACKAGE = 16 * 1024 * 1024


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def segments(path):
    values = [unquote(p) for p in path.split('/')]
    if any(not p or p in ('.', '..') or '/' in p or '\\' in p or any(ord(c) < 32 for c in p) for p in values):
        raise ValueError('Unsafe path segment')
    return values


def raw_url(repo, commit, path):
    if not REPO.fullmatch(repo) or not COMMIT.fullmatch(commit):
        raise ValueError('Invalid repository or immutable revision')
    return f'https://{HOST}/{repo}/{commit}/' + '/'.join(quote(p, safe='') for p in segments(path))


def pin_package(source, url):
    u = urlsplit(url)
    if u.scheme != 'https' or u.netloc.lower() != HOST or u.fragment:
        raise ValueError('Unsupported package origin')
    parts = segments(u.path.removeprefix('/'))
    if len(parts) < 4 or '/'.join(parts[:2]).casefold() != source['repo'].casefold():
        raise ValueError('Cross-repository package URL')
    tail = parts[2:]
    if tail[:2] == ['refs', 'heads']:
        tail = tail[2:]
    if len(tail) < 2 or tail[0] not in (source['branch'], source['commit']):
        raise ValueError('Package revision differs from catalog revision')
    path = '/'.join(tail[1:])
    if not path.lower().endswith('.cs3'):
        raise ValueError('Invalid package extension')
    return raw_url(source['repo'], source['commit'], path)


def download(url, limit):
    u = urlsplit(url)
    if u.scheme != 'https' or u.netloc.lower() != HOST:
        raise ValueError('Unsupported download origin')
    request = Request(url, headers={'User-Agent': 'MegaRepo-ReadOnly-ExpansionAudit/1.0', 'Accept': '*/*'})
    with build_opener(NoRedirect()).open(request, timeout=25) as response:
        if response.status != 200:
            raise ValueError(f'Unexpected HTTP status {response.status}')
        data = response.read(limit + 1)
    if not data or len(data) > limit:
        raise ValueError('Empty or oversized response')
    return data


def identity(p):
    return str(p.get('internalName') or p.get('name') or '').casefold()


def catalog(source, fetch):
    url = raw_url(source['repo'], source['commit'], source['path'])
    data = fetch(url, MAX_INDEX)
    entries = json.loads(data)
    if not isinstance(entries, list) or any(not isinstance(p, dict) or not identity(p) for p in entries):
        raise ValueError('Malformed catalog or missing identity')
    keys = [identity(p) for p in entries]
    if len(keys) != len(set(keys)):
        raise ValueError('Repeated identity inside source catalog')
    return entries, 'sha256-' + hashlib.sha256(data).hexdigest()


def check_package(source, entry, fetch=download):
    result = {'source': source['name'], 'identity': identity(entry), 'version': entry.get('version'),
              'declaredHash': entry.get('fileHash'), 'declaredSize': entry.get('fileSize'),
              'upstreamStatus': entry.get('status'), 'apiVersion': entry.get('apiVersion'),
              'licenseMetadata': source.get('license_metadata'), 'sourceCommit': source['commit'],
              'result': 'error', 'approved': False}
    try:
        result['pinnedUrl'] = pin_package(source, entry.get('url') or '')
        data = fetch(result['pinnedUrl'], MAX_PACKAGE)
        result['actualHash'] = 'sha256-' + hashlib.sha256(data).hexdigest()
        result['actualSize'] = len(data)
        with ZipFile(io.BytesIO(data)) as z:
            if not z.infolist():
                raise ValueError('Empty ZIP directory')
        result['zipDirectoryReadable'] = True
        size = entry.get('fileSize')
        digest = entry.get('fileHash')
        if type(size) is not int or size <= 0 or size != len(data):
            result['result'] = 'size_mismatch_or_missing'
        elif not isinstance(digest, str) or not DIGEST.fullmatch(digest):
            result['result'] = 'declared_sha256_missing_or_invalid'
        elif digest.casefold() != result['actualHash']:
            result['result'] = 'sha256_mismatch'
        else:
            result['result'] = 'hash_verified'
    except Exception as exc:
        result['error'] = f'{type(exc).__name__}: {exc}'
    return result


def audit(snapshot, fetch=download, workers=12):
    base, base_hash = catalog(snapshot['baseline'], fetch)
    known = {identity(p) for p in base}
    if len(known) != snapshot['baseline']['identities']:
        raise ValueError('Published baseline does not reconcile')
    rows, sources, tasks = [], [], []
    for source in snapshot['sources']:
        record = {'source': source['name'], 'commit': source['commit']}
        try:
            entries, digest = catalog(source, fetch)
            candidates = [p for p in entries if identity(p) not in known]
            record.update(total=len(entries), overlap=len(entries)-len(candidates), candidates=len(candidates), catalogSHA256=digest)
            record['countsMatch'] = (len(entries) == source['total'] and record['overlap'] == source['overlap'])
            tasks.extend((source, p) for p in candidates)
        except Exception as exc:
            record.update(countsMatch=False, error=f'{type(exc).__name__}: {exc}')
        sources.append(record)
    with ThreadPoolExecutor(max_workers=workers) as pool:
        rows = list(pool.map(lambda t: check_package(t[0], t[1], fetch), tasks))
    identities = defaultdict(list)
    for row in rows:
        identities[row['identity']].append(row['source'])
    duplicates = {k: v for k, v in sorted(identities.items()) if len(v) > 1}
    counts = {'rows': sum(s.get('total', 0) for s in sources), 'overlap': sum(s.get('overlap', 0) for s in sources),
              'candidates': len(rows), 'distinct': len(identities)}
    for s in sources:
        s['packageResults'] = dict(Counter(r['result'] for r in rows if r['source'] == s['source']))
    complete = all(s['countsMatch'] for s in sources) and counts == snapshot['expected']
    return {'mode': 'READ_ONLY_EVIDENCE_NOT_RELEASE_APPROVAL', 'baselineSHA256': base_hash,
            'baselineCommit': snapshot['baseline']['commit'], 'counts': counts, 'coverageComplete': complete,
            'packageResults': dict(Counter(r['result'] for r in rows)), 'duplicateIdentities': duplicates,
            'nonActiveRows': sum(r['upstreamStatus'] != 1 for r in rows), 'approvedCount': 0,
            'sources': sources, 'excluded': snapshot['excluded_from_byte_audit'], 'packages': rows}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--snapshot', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    args = p.parse_args()
    report = audit(json.loads(args.snapshot.read_text(encoding='utf-8')))
    args.report.write_text(json.dumps(report, indent=2, ensure_ascii=True) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'packages'}, sort_keys=True, ensure_ascii=True))
    # A successful evidence-collection job is not an integrity or approval verdict.
    return 0 if report['coverageComplete'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
