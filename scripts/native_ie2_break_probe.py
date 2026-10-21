#!/usr/bin/env python3
"""Native storage break receipts, actual client placement, and a second JVM reload.

Accepts only a fresh disposable directory and hash-pinned Purpur. Uses explicit
synthetic boundary balances, real Player.breakBlock and ItemSpawnEvent callbacks,
an actual loopback client placement, and the real stored-shell PDC reader.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PURPUR_SHA = 'ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34'

def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def main() -> None:
    if len(sys.argv) != 7:
        raise SystemExit('Usage: native_ie2_break_probe.py <core.jar> <networks.jar> <ie2.jar> <purpur.jar> <new-directory> <client-deps>')
    core, networks, ie2, server, work, modules = map(lambda arg: Path(arg).resolve(), sys.argv[1:])
    for jar in (core, networks, ie2, server):
        if not jar.is_file() or not zipfile.is_zipfile(jar):
            raise SystemExit(f'Not a valid JAR: {jar}')
    if digest(server) != PURPUR_SHA:
        raise SystemExit('Only pinned Purpur1.21.11 build2568 is accepted')
    revisions = {key: os.environ.get(key, '') for key in ('CORE_HEAD', 'NETWORKS_HEAD', 'IE2_HEAD')}
    if not all(re.fullmatch('[0-9a-f]{40}', sha) for sha in revisions.values()):
        raise SystemExit('Exact source revisions required for all three plugin JARs')
    work.mkdir(parents=True, exist_ok=False)
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin'
    java, javac = str(java_bin / 'java'), str(java_bin / 'javac')
    with zipfile.ZipFile(server) as archive:
        expected, url, filename = archive.read('META-INF/download-context').decode().strip().split('\t')
    if Path(filename).name != filename or not url.startswith('https://piston-data.mojang.com/'):
        raise SystemExit('Unexpected server bootstrap source')
    (work / 'cache').mkdir()
    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '2',
                    '--max-time', '240', url, '--output', str(work / 'cache' / filename)], check=True)
    if digest(work / 'cache' / filename) != expected:
        raise SystemExit('Mojang input hash mismatch')
    with (work / 'patch.log').open('w') as log:
        subprocess.run([java, '-Dpaperclip.patchonly=true', '-jar', str(server)], cwd=work,
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=120)
    plugins = work / 'plugins'
    (plugins / 'bStats').mkdir(parents=True)
    (plugins / 'bStats/config.yml').write_text('enabled: false\n')
    for source, name in ((core, 'Slimefun.jar'), (networks, 'Networks.jar'), (ie2, 'InfinityExpansion2.jar')):
        shutil.copyfile(source, plugins / name)
    classpath = os.pathsep.join(map(str, [core, networks, *sorted((work / 'libraries').rglob('*.jar'))]))
    classes = work / 'break-probe-classes'
    subprocess.run([javac, '--release', '21', '-cp', classpath, '-d', str(classes),
                    str(ROOT / 'tests/runtime/NativeIE2BreakProbe.java')], check=True)
    with zipfile.ZipFile(plugins / 'NativeIE2BreakProbe.jar', 'w') as archive:
        for compiled in sorted(classes.rglob('*.class')):
            archive.write(compiled, compiled.relative_to(classes))
        archive.writestr('plugin.yml', "name: NativeIE2BreakProbe\nversion: 1.0\n"
                         "main: ie2breakprobe.NativeIE2BreakProbe\napi-version: '1.21'\n"
                         "depend: [Slimefun, Networks, InfinityExpansion2]\n")
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    (work / 'eula.txt').write_text('eula=true\n')
    (work / 'server.properties').write_text(
        f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-name=fixture\n'
        'level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n')
    manifest = {**revisions, 'core_sha256': digest(core), 'networks_sha256': digest(networks),
                'ie2_sha256': digest(ie2), 'server_sha256': digest(server), 'result': 'PENDING',
                'native_break_source_sha256': digest(ROOT / 'tests/runtime/NativeIE2BreakProbe.java'),
                'fixture_limits': 'Synthetic boundary balances; native Player.breakBlock/entity events; actual-client placement; clean restart, not forced-crash proof'}
    manifest_path = work / 'inputs.json'
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    for phase in ('seed', 'verify'):
        result = plugins / 'native-break-result.txt'
        ready = plugins / 'native-break-ready.txt'
        result.unlink(missing_ok=True)
        ready.unlink(missing_ok=True)
        (plugins / 'native-break-phase.txt').write_text(phase + '\n')
        with (work / f'{phase}-console.log').open('w') as log, (work / f'{phase}-client.log').open('w') as client_log:
            process = subprocess.Popen([java, '-Die2.break.probe=disposable', '-Dterminal.jline=false',
                                        '-Dterminal.ansi=false', '-Xms256M', '-Xmx1800M', '-jar', str(server), '--nogui'],
                                       cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
            client = None
            try:
                if phase == 'seed':
                    deadline = time.monotonic() + 180
                    while not ready.is_file() and process.poll() is None and time.monotonic() < deadline:
                        time.sleep(0.25)
                    if not ready.is_file() or process.poll() is not None:
                        raise RuntimeError('Native break server did not become ready')
                    env = {**os.environ, 'IE2_BREAK_PROBE': 'disposable', 'NATIVE_PLAYER_PORT': str(port),
                           'NATIVE_PLAYER_MODULES': str(modules)}
                    client = subprocess.Popen(['node', str(ROOT / 'tests/runtime/native_ie2_break_client.cjs')],
                                              env=env, stdout=client_log, stderr=subprocess.STDOUT)
                    client.wait(timeout=160)
                    if client.returncode != 0: raise RuntimeError('Native break client failed')
                process.wait(timeout=150)
                report = result.read_text().strip() if result.is_file() else 'No native result'
                if process.returncode != 0 or not report.startswith(f'PASS: {phase} '):
                    raise RuntimeError(report)
                manifest[f'{phase}_pid'] = process.pid
                manifest[f'{phase}_result'] = report
                (work / f'{phase}-result.txt').write_text(report + '\n')
                # Preserve the exact stopped-server database and any journal files
                # before the next JVM can open them. This is evidence only: never
                # replace the live database or checkpoint it from the harness.
                stopped_storage = work / f'{phase}-stopped-storage'
                shutil.copytree(work / 'data-storage', stopped_storage)
                manifest[f'{phase}_stopped_storage_sha256'] = {
                    str(path.relative_to(stopped_storage)): digest(path)
                    for path in sorted(stopped_storage.rglob('*')) if path.is_file()
                }
                manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
                print(report, flush=True)
            except BaseException:
                manifest['result'] = 'FAIL'
                manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
                raise
            finally:
                if client is not None and client.poll() is None:
                    client.kill(); client.wait()
                if process.poll() is None:
                    try: process.communicate(input=b'stop\n', timeout=30)
                    except subprocess.TimeoutExpired: process.kill(); process.wait()
                print('\n'.join((work / f'{phase}-console.log').read_text(errors='replace').splitlines()[-45:]), flush=True)
                print((work / f'{phase}-client.log').read_text(errors='replace'), flush=True)
    if manifest['seed_pid'] == manifest['verify_pid']:
        raise SystemExit('Restart phase must run in a different JVM')
    manifest['result'] = 'PASS'
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    print('PASS: native IE2 break/rollback/placement and independent-JVM persistence', flush=True)

if __name__ == '__main__':
    main()
