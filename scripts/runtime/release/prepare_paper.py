#!/usr/bin/env python3
"""Checksum-verify an official stable Paper build and prepare only its disposable runtime cache."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
from urllib.parse import urlparse

USER_AGENT='InfinityExpansion2-CI/2.0.17 (https://github.com/wickidcow/SF_InfinityExpansion2)'


def fetch(url):
    error=None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':USER_AGENT}),timeout=90) as response:
                return response.read()
        except (OSError,TimeoutError) as failure:
            error=failure
            if attempt<3:time.sleep(2**attempt)
    raise RuntimeError('Unable to retrieve official Paper artifact') from error


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version',required=True,choices=['1.21.11','26.3'])
    parser.add_argument('--directory',required=True,type=Path)
    parser.add_argument('--java-home',required=True,type=Path)
    args=parser.parse_args()
    root=args.directory.resolve()
    if root.exists() and any(root.iterdir()):parser.error('Use a new empty cache directory, never an existing server')
    root.mkdir(parents=True,exist_ok=True)
    builds=json.loads(fetch(f'https://fill.papermc.io/v3/projects/paper/versions/{args.version}/builds'))
    if not isinstance(builds,list):raise RuntimeError('Unexpected official build metadata')
    stable=[build for build in builds if build.get('channel')=='STABLE']
    if not stable:raise RuntimeError('No official stable build for requested version; no fallback permitted')
    selected=stable[0];download=selected['downloads']['server:default']
    url=download['url'];sha=download['checksums']['sha256']
    if urlparse(url).scheme!='https' or urlparse(url).hostname!='fill-data.papermc.io':
        raise RuntimeError('Unexpected Paper download host')
    if not re.fullmatch('[0-9a-f]{64}',sha):raise RuntimeError('Invalid official checksum')
    data=fetch(url)
    if hashlib.sha256(data).hexdigest()!=sha:raise RuntimeError('Official Paper SHA-256 mismatch')
    (root/'server.jar').write_bytes(data)
    (root/'official-build.json').write_text(json.dumps({'version':args.version,'build':selected,'verified_sha256':sha},indent=2)+'\n')
    with (root/'bootstrap.log').open('w') as log:
        subprocess.run([str(args.java_home/'bin/java'),'-Dpaperclip.patchonly=true','-jar','server.jar'],
                       cwd=root,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=240)
    if not list((root/'libraries').rglob('*.jar')):raise RuntimeError('Paperclip did not prepare the runtime library cache')
    print(f'Prepared official stable Paper {args.version}, build {selected.get("id")}; SHA-256 {sha}')

if __name__=='__main__':main()
