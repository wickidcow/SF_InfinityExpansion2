#!/usr/bin/env python3
"""Run native item, restart and deliberately SIGKILLed Forge conservation probes."""
import argparse
import base64
import contextlib
import hashlib
import json
import os
import shutil
from pathlib import Path
import sqlite3
import subprocess
import sys
import time
import zipfile


def run_process(command, directory, log, timeout):
    with log.open('w') as output:
        process=subprocess.Popen(command,cwd=directory,stdout=output,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL)
        try:
            code=process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:process.wait(timeout=20)
            except subprocess.TimeoutExpired:process.kill();process.wait(timeout=20)
            raise RuntimeError('Disposable runtime timed out: '+str(log))
    if code:
        print('\n'.join(log.read_text(errors='replace').splitlines()[-100:]))
        raise RuntimeError('Disposable runtime exited '+str(code))



def kill_exact_disposable_process_after_result(command,directory,log,result,timeout):
    """SIGKILL only the locally spawned test server after its craft receipt."""
    with log.open('w') as output:
        process=subprocess.Popen(command,cwd=directory,stdout=output,
                                 stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL)
        try:
            deadline=time.monotonic()+timeout
            while time.monotonic()<deadline:
                if result.is_file():
                    report=result.read_text(errors='replace')
                    if 'PASS crash:' not in report or 'failures: 0' not in report:
                        raise AssertionError('Forge crash probe did not finish validly: '+report)
                    process.kill()
                    process.wait(timeout=20)
                    print('SIGKILL applied to disposable Forge test JVM after craft receipt.')
                    return
                if process.poll() is not None:
                    raise RuntimeError('Forge crash JVM exited before receipt; log='+str(log))
                time.sleep(0.025)
            raise TimeoutError('Forge crash JVM timed out before receipt: '+str(log))
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=20)

def configured_block_data_base64(runtime):
    """Use the live Slimefun fixture's top-level encoding mode; never guess either representation."""
    config=runtime/'plugins/Slimefun/block-storage.yml'
    if config.is_symlink() or not config.is_file():
        raise AssertionError('Native Slimefun block-storage.yml is missing or linked')
    settings=[]
    for line in config.read_text(encoding='utf-8').splitlines():
        # The checked-in Slimefun configuration defines this single top-level scalar.
        # Reject malformed/duplicate settings instead of accepting both database encodings.
        if not line.startswith((' ', '\t')) and line.split('#',1)[0].strip().startswith('base64EncodeVal:'):
            value=line.split('#',1)[0].partition(':')[2].strip().lower()
            if value not in ('true','false'):
                raise AssertionError('Native Slimefun base64EncodeVal must be an explicit boolean')
            settings.append(value=='true')
    if len(settings)!=1:
        raise AssertionError('Native Slimefun requires exactly one top-level base64EncodeVal setting')
    return settings[0]


def stored_unknown_snapshot(runtime):
    """Read the stopped SQLite database verbatim and enforce its configured field encoding."""
    encoded=configured_block_data_base64(runtime)
    database=runtime/'data-storage/Slimefun/block-storage.db'
    if database.is_symlink() or not database.is_file():
        raise AssertionError('Native Slimefun SQLite database is missing or linked')
    with contextlib.closing(sqlite3.connect(database.as_uri()+'?mode=ro',uri=True)) as connection:
        connection.execute('PRAGMA query_only=ON')
        records=connection.execute('SELECT loc, chunk, sf_id FROM block_record WHERE sf_id=?',
            ('IE2_NATIVE_UNKNOWN_ID',)).fetchall()
        if len(records)!=1:raise AssertionError('Unknown record not preserved exactly once in native database')
        payload=connection.execute('SELECT data_key, data_val FROM block_data WHERE loc=? ORDER BY data_key',
            (records[0][0],)).fetchall()
        expected='preserve-unknown-record'
        if encoded:
            expected=base64.b64encode(expected.encode('utf-8')).decode('ascii')
        markers=[value for key,value in payload if key=='probe.marker']
        if markers!=[expected]:
            raise AssertionError('Unknown record payload did not survive native database save '
                                 'in the configured encoding; marker rows='+str(len(markers)))
    # Keep the raw rows for exact byte-for-byte comparison across the second clean JVM boot.
    return {'record':records[0],'payload':payload,'base64_encoded':encoded}



# Immutable published artifact, used only to seed a disposable native world.
# The published 2.0.17 JAR is never modified or republished.
HISTORICAL_IE2_URL = ('https://github.com/wickidcow/SF_InfinityExpansion2/releases/'
                      'download/v2.0.17/SF_InfinityExpansion2.0.17.jar')
HISTORICAL_IE2_SIZE = 1734055
HISTORICAL_IE2_SHA256 = '02aa79bc84444a28aa292cc216450a9a44ae62fa7377b86787a70d2c5758f33a'


def verify_historical_ie2_jar(path, expected_size=HISTORICAL_IE2_SIZE,
                               expected_sha256=HISTORICAL_IE2_SHA256):
    """Fail closed unless the historical artifact has the pinned bytes and plugin ZIP structure."""
    path=Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size!=expected_size:
        raise AssertionError('Historical IE2 JAR is missing, linked, or has the wrong byte size')
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    if digest!=expected_sha256:
        raise AssertionError('Historical IE2 JAR SHA-256 does not match published asset')
    try:
        with zipfile.ZipFile(path) as jar:
            if jar.testzip() is not None:
                raise AssertionError('Historical IE2 JAR failed ZIP CRC verification')
            if not ({'plugin.yml','paper-plugin.yml'} & set(jar.namelist())):
                raise AssertionError('Historical IE2 JAR has no plugin descriptor')
    except (zipfile.BadZipFile,OSError) as error:
        raise AssertionError('Historical IE2 JAR is not a readable plugin ZIP') from error
    return digest


def download_historical_ie2_jar(destination):
    """Download exact publicly released legacy bytes; never silently use the candidate as old data."""
    if destination.exists() or destination.is_symlink():
        raise AssertionError('Refusing preexisting historical native test artifact')
    subprocess.run(['curl','--fail','--show-error','--silent','--location',
                    '--retry','2','--proto','=https','--proto-redir','=https',
                    '--tlsv1.2','--connect-timeout','20','--max-time','240',
                    '-o',str(destination),HISTORICAL_IE2_URL],check=True)
    return verify_historical_ie2_jar(destination)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('runtime','paper-source','core','ie2','java-home','kotlin-stdlib','kotlin-reflect'):
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--timeout',type=int,default=300)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[3]
    forwarded=[]
    for name in ('runtime','paper-source','core','ie2','java-home','kotlin-stdlib','kotlin-reflect'):
        forwarded.extend(['--'+name,str(getattr(args,name.replace('-','_')).resolve())])
    subprocess.run([sys.executable,str(root/'scripts/runtime/mob_card_refresh/run.py'),*forwarded,'--timeout',str(args.timeout)],check=True)
    runtime=args.runtime.resolve()
    initial=runtime/'plugins/NativeMobCardRefreshProbe/results.txt'
    if not initial.exists() or 'failures: 0' not in initial.read_text():
        raise AssertionError('Existing native card suite did not pass')
    (runtime/'card-probe-results.txt').write_bytes(initial.read_bytes())
    (runtime/'console.log').rename(runtime/'card-probe-console.log')
    # The card suite shuts down on completion. Keep it out of the following dedicated phases.
    (runtime/'plugins/NativeMobCardRefreshProbe.jar').rename(runtime/'NativeMobCardRefreshProbe.test-artifact')
    source=Path(__file__).with_name('NativeModernizationProbe.java')
    classes=runtime/'modernization-classes';classes.mkdir()
    jars=list((runtime/'libraries').rglob('*.jar'))+[args.core.resolve(),args.ie2.resolve(),args.kotlin_stdlib.resolve()]
    javac_args=['--release','21','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),str(source)]
    argfile=runtime/'modernization-javac.args'
    argfile.write_text('\n'.join(json.dumps(arg) for arg in javac_args))
    subprocess.run([str(args.java_home/'bin/javac'),'@'+str(argfile)],check=True)
    with zipfile.ZipFile(runtime/'plugins/NativeModernizationProbe.jar','w',zipfile.ZIP_DEFLATED) as jar:
        for compiled in classes.rglob('*.class'):jar.write(compiled,compiled.relative_to(classes))
        jar.writestr('plugin.yml','name: NativeModernizationProbe\nversion: 1.0\nmain: verification.NativeModernizationProbe\napi-version: "1.21"\ndepend: [Slimefun, InfinityExpansion2]\n')
    evidence={'core_sha256':hashlib.sha256(args.core.read_bytes()).hexdigest(),
              'ie2_sha256':hashlib.sha256(args.ie2.read_bytes()).hexdigest(),
              'probe_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'phases':{}}
    for phase in ('seed','verify'):
        result=runtime/f'plugins/NativeModernizationProbe/results-{phase}.txt'
        if result.exists():raise AssertionError('Refusing stale phase result '+str(result))
        command=[str(args.java_home/'bin/java'),'-Xms256M','-Xmx1G','-XX:ActiveProcessorCount=2',
                 '-Dterminal.jline=false','-Dterminal.ansi=false','-Die2.release.probe=disposable',
                 '-Die2.release.phase='+phase,'-jar','server.jar','--nogui']
        log=runtime/f'modernization-{phase}.log'
        run_process(command,runtime,log,args.timeout)
        if not result.exists():
            print('\n'.join(log.read_text(errors='replace').splitlines()[-100:]))
            raise AssertionError('Native phase produced no result '+phase)
        text=result.read_text();print(text)
        if 'failures: 0' not in text or 'PASS '+phase+':' not in text:
            raise AssertionError('Native phase failed: '+phase)
        evidence['phases'][phase]=text
        stored=stored_unknown_snapshot(runtime)
        if phase=='seed':evidence['unknown_record_database_snapshot']=stored
        elif stored!=evidence['unknown_record_database_snapshot']:
            raise AssertionError('Unknown-ID database record or payload changed across restart')
        print('Read-only SQLite evidence: unknown-ID record and payload preserved after '+phase)
    # A separate isolated three-phase Forge fixture preserves a populated
    # old-unit recipe across an intentionally SIGKILLed full craft process.
    crash_source=Path(__file__).with_name('NativeForgeCrashProbe.java')
    crash_classes=runtime/'forge-crash-classes';crash_classes.mkdir()
    crash_javac=['--release','21','-cp',os.pathsep.join(map(str,jars)),
                 '-d',str(crash_classes),str(crash_source)]
    crash_argfile=runtime/'forge-crash-javac.args'
    crash_argfile.write_text('\n'.join(json.dumps(arg) for arg in crash_javac))
    subprocess.run([str(args.java_home/'bin/javac'),'@'+str(crash_argfile)],check=True)
    with zipfile.ZipFile(runtime/'plugins/NativeForgeCrashProbe.jar','w',zipfile.ZIP_DEFLATED) as jar:
        for compiled in crash_classes.rglob('*.class'):
            jar.write(compiled,compiled.relative_to(crash_classes))
        jar.writestr('plugin.yml',
                     'name: NativeForgeCrashProbe\nversion: 1.0\n'
                     'main: verification.NativeForgeCrashProbe\n'
                     'api-version: "1.21"\ndepend: [Slimefun, InfinityExpansion2]\n')
    evidence['forge_crash_probe_sha256']=hashlib.sha256(crash_source.read_bytes()).hexdigest()
    evidence['forge_crash_phases']={}
    # Preserve an actual v2.0.17-created saved inventory across an addon JAR
    # upgrade. Only this disposable test JVM ever loads the historical release.
    historical_jar=runtime/'historical-ie2-v2.0.17.jar'
    historical_digest=download_historical_ie2_jar(historical_jar)
    active_addon=runtime/'plugins/InfinityExpansion2.jar'
    candidate_digest=hashlib.sha256(args.ie2.read_bytes()).hexdigest()
    if hashlib.sha256(active_addon.read_bytes()).hexdigest()!=candidate_digest:
        raise AssertionError('Current native candidate JAR changed unexpectedly')
    evidence['historical_forge_seed']={
        'published_tag':'v2.0.17',
        'published_asset_sha256':historical_digest,
        'candidate_sha256':candidate_digest,
        'seeded_with_published_jar':True,
    }
    for phase in ('seed','crash','verify'):
        result=runtime/f'plugins/NativeForgeCrashProbe/results-{phase}.txt'
        if result.exists():raise AssertionError('Refusing stale crash fixture '+str(result))
        command=[str(args.java_home/'bin/java'),'-Xms256M','-Xmx1G',
                 '-XX:ActiveProcessorCount=2','-Dterminal.jline=false',
                 '-Dterminal.ansi=false','-Die2.forge.crash=disposable',
                 '-Die2.forge.phase='+phase,'-jar','server.jar','--nogui']
        log=runtime/f'forge-crash-{phase}.log'
        if phase=='seed':
            shutil.copyfile(historical_jar,active_addon)
            expected_digest=historical_digest
        else:
            expected_digest=candidate_digest
        if hashlib.sha256(active_addon.read_bytes()).hexdigest()!=expected_digest:
            raise AssertionError('Forge phase loaded an unexpected addon binary: '+phase)
        try:
            if phase=='crash':
                kill_exact_disposable_process_after_result(command,runtime,log,result,args.timeout)
            else:
                run_process(command,runtime,log,args.timeout)
        finally:
            # Even if the historical seed fails, restore the candidate in the
            # disposable fixture. Never leave a legacy binary in later phases.
            if phase=='seed':
                shutil.copyfile(args.ie2,active_addon)
        if not result.is_file():
            print('\n'.join(log.read_text(errors='replace').splitlines()[-100:]))
            raise AssertionError('Native Forge crash phase wrote no result: '+phase)
        report=result.read_text();print(report)
        if 'PASS '+phase+':' not in report or 'failures: 0' not in report:
            raise AssertionError('Native Forge crash phase failed: '+phase)
        evidence['forge_crash_phases'][phase]=report
    # The crash probe must not damage an unrelated existing SQLite record.
    post_crash_unknown=stored_unknown_snapshot(runtime)
    if post_crash_unknown!=evidence['unknown_record_database_snapshot']:
        raise AssertionError('Unrelated stored Slimefun record changed across crash recovery')
    evidence['unknown_record_after_forge_sigkill']=post_crash_unknown
    (runtime/'release-runtime-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
    print('Native card suite, clean restart and post-craft SIGKILL Forge conservation checks passed.')
    return 0

if __name__=='__main__':raise SystemExit(main())
