#!/usr/bin/env python3
"""Run the existing native card suite, then real database/menu seed and restart verification."""
import argparse
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


def stored_unknown_snapshot(runtime):
    """Read the actual stopped disposable database; never register, rewrite or repair a record."""
    database=runtime/'data-storage/Slimefun/block-storage.db'
    if not database.is_file():raise AssertionError('Native Slimefun SQLite database is missing')
    with sqlite3.connect(database.as_uri()+'?mode=ro',uri=True) as connection:
        connection.execute('PRAGMA query_only=ON')
        records=connection.execute('SELECT loc, chunk, sf_id FROM block_record WHERE sf_id=?',
            ('IE2_NATIVE_UNKNOWN_ID',)).fetchall()
        if len(records)!=1:raise AssertionError('Unknown record not preserved exactly once in native database')
        payload=connection.execute('SELECT data_key, data_val FROM block_data WHERE loc=? ORDER BY data_key',
            (records[0][0],)).fetchall()
        if ('probe.marker','preserve-unknown-record') not in payload:
            raise AssertionError('Unknown record payload did not survive native database save')
    return {'record':records[0],'payload':payload}


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
