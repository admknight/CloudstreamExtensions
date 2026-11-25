#!/usr/bin/env python3
"""Read-only check of one public release catalog and its declared packages."""
import hashlib
import io
import json
from pathlib import Path
import re
from urllib.parse import urlsplit, unquote, quote
from urllib.request import Request, build_opener, HTTPRedirectHandler
from zipfile import ZipFile
from concurrent.futures import ThreadPoolExecutor

PREFIX = 'https://github.com/aslafy-z/cloudstream-plugins/releases/download/'
STABLE = PREFIX + 'catalog-stable/'
BASE = 'https://raw.githubusercontent.com/admknight/CloudstreamExtensions/b71583bb72c6506a977e476add6e9db727fd7af6/plugins.json'
EXPECTED = {'repo.json': (247, '36ac6b7a8ee451b05068129b5254565b668787fbf8c92bb0a97a935532911e50'),
            'plugins.json': (4869, 'e899ecdd1be9b878a1c848f2611f384ea8768febe81039d25140512325beed0b')}
REDIRECT_HOSTS = {'release-assets.githubusercontent.com', 'objects.githubusercontent.com'}
MAX_SIZE = 16 * 1024 * 1024


class ReleaseRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        target = urlsplit(newurl)
        if target.scheme != 'https' or target.netloc not in REDIRECT_HOSTS:
            raise ValueError('Unapproved release download redirect')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def package_url(url):
    if not isinstance(url, str) or not url.startswith(PREFIX):
        raise ValueError('Package URL outside expected publisher release path')
    u = urlsplit(url)
    if u.netloc != 'github.com' or u.query or u.fragment:
        raise ValueError('Unexpected release URL components')
    parts = [unquote(p) for p in url[len(PREFIX):].split('/')]
    if len(parts) != 2 or any(not re.fullmatch(r'[A-Za-z0-9_.+-]+', p) or p in ('.', '..') for p in parts):
        raise ValueError('Unexpected tag or asset name')
    if not parts[1].endswith('.cs3') or parts[0].startswith('catalog-'):
        raise ValueError('Not a versioned package asset')
    return PREFIX + '/'.join(quote(p, safe='') for p in parts)


def fetch(url, limit):
    if url != BASE and not url.startswith(PREFIX):
        raise ValueError('Unapproved original URL')
    req = Request(url, headers={'User-Agent':'MegaRepo-Release-Catalog-Audit/1.0','Accept':'*/*'})
    with build_opener(ReleaseRedirect()).open(req, timeout=25) as res:
        if res.status != 200:
            raise ValueError(f'Unexpected HTTP {res.status}')
        data = res.read(limit+1)
    if not data or len(data) > limit:
        raise ValueError('Empty or oversized download')
    return data


def key(p):
    return str(p.get('internalName') or p.get('name') or '').casefold()


def verify(entry, fetcher=fetch):
    row = {'identity':key(entry),'version':entry.get('version'),'upstreamStatus':entry.get('status'),
           'apiVersion':entry.get('apiVersion'),'declaredSize':entry.get('fileSize'),'declaredHash':entry.get('fileHash'),
           'approved':False,'result':'error'}
    try:
        row['assetUrl'] = package_url(entry.get('url'))
        data = fetcher(row['assetUrl'], MAX_SIZE)
        row.update(actualSize=len(data), actualHash='sha256-'+hashlib.sha256(data).hexdigest())
        with ZipFile(io.BytesIO(data)) as z:
            if not z.infolist():
                raise ValueError('Empty ZIP directory')
        row['result'] = 'hash_verified' if (type(row['declaredSize']) is int and row['declaredSize']==len(data) and
                      isinstance(row['declaredHash'],str) and row['declaredHash'].lower()==row['actualHash']) else 'metadata_mismatch'
    except Exception as exc:
        row['error'] = f'{type(exc).__name__}: {exc}'
    return row


def run(fetcher=fetch):
    manifests = {}
    for name,(size,digest) in EXPECTED.items():
        data = fetcher(STABLE+name, 1024*1024)
        if len(data)!=size or hashlib.sha256(data).hexdigest()!=digest:
            raise ValueError(f'{name} no longer matches observed public release asset metadata')
        manifests[name] = json.loads(data)
    if manifests['repo.json'].get('pluginLists') != [STABLE+'plugins.json']:
        raise ValueError('Unexpected catalog pointer')
    published = json.loads(fetcher(BASE, 20*1024*1024))
    known = {key(p) for p in published}
    if len(published)!=547 or len(known)!=547:
        raise ValueError('Baseline mismatch')
    entries = manifests['plugins.json']
    if not isinstance(entries,list) or any(not isinstance(p,dict) or not key(p) for p in entries) or len({key(p) for p in entries})!=len(entries):
        raise ValueError('Malformed or duplicated catalog identity')
    new = [p for p in entries if key(p) not in known]
    with ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(lambda p: verify(p, fetcher),new))
    return {'mode':'READ_ONLY_RELEASE_ASSET_EVIDENCE_NOT_APPROVAL','catalogMetadataMatched':True,'catalogRows':len(entries),
            'overlap':len(entries)-len(new),'candidateRows':len(new),'hashVerified':sum(r['result']=='hash_verified' for r in rows),
            'approvedCount':0,'packages':rows,
            'limitation':'Versioned release assets are not immutable commit-pinned raw packages; production policy and provenance review unchanged.'}


if __name__=='__main__':
    try:
        report=run()
    except Exception as exc:
        report={'mode':'READ_ONLY_RELEASE_ASSET_EVIDENCE_NOT_APPROVAL','fatalError':f'{type(exc).__name__}: {exc}','approvedCount':0}
    Path('replay-release-catalog-report.json').write_text(json.dumps(report,indent=2,ensure_ascii=True)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=True,sort_keys=True))
    raise SystemExit(1 if 'fatalError' in report else 0)
