#!/usr/bin/env python3
"""Run the existing native card suite, then real database/menu seed and restart verification."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
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
    with sqlite3.connect(database.as_uri()+'?mode=ro',uri=True) as connection:
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
    (runtime/'release-runtime-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
    print('Native card suite and both live database/menu persistence phases passed; three clean JVM boots.')
    return 0

if __name__=='__main__':raise SystemExit(main())
