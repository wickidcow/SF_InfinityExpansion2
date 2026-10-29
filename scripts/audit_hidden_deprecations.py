#!/usr/bin/env python3
"""Expose suppressed Kotlin warnings in a disposable worktree, never the packaged source.

This is a diagnostic inventory, not a production migration or a claim of runtime bugs.
The tested source and canonical JAR hashes must remain unchanged after the audit.
"""
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ANNOTATION = re.compile(r'^@file:Suppress\(([^\n]*)\)', re.MULTILINE)
DIAGNOSTIC = re.compile(r'^w: .*?/src/main/(.+?\.(?:kt|java)):(\d+):(\d+) (.*deprecat.*)$', re.IGNORECASE)


def expose(text):
    removed = 0
    def replace(match):
        nonlocal removed
        args = match.group(1)
        if not re.fullmatch(r'\s*"[^"]+"(?:\s*,\s*"[^"]+")*\s*', args):
            raise AssertionError('Unsupported suppression syntax in diagnostic copy: ' + match.group(0))
        names = re.findall(r'"([^"]+)"', args)
        kept = [name for name in names if name.upper() != 'DEPRECATION']
        if len(kept) == len(names):
            return match.group(0)
        removed += 1
        return '@file:Suppress(' + ', '.join('"' + name + '"' for name in kept) + ')' if kept else ''
    result = ANNOTATION.sub(replace, text)
    return result, removed


def diagnostics(text):
    result = set()
    unlocated = []
    for line in text.splitlines():
        match = DIAGNOSTIC.match(line)
        if match:
            path, row, col, reason = match.groups()
            result.add((path, int(row), int(col), reason))
        elif '/src/main/' in line and 'deprecat' in line.lower():
            unlocated.append(line)
    return sorted(result), unlocated


def source_hashes(root):
    return {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (root / 'src/main').rglob('*') if path.is_file()}


def main():
    assert expose('@file:Suppress("deprecation")\npackage test\n') == ('\npackage test\n', 1)
    assert expose('@file:Suppress("deprecation", "UnstableApiUsage")') == ('@file:Suppress("UnstableApiUsage")', 1)
    assert expose('@file:Suppress("unused")') == ('@file:Suppress("unused")', 0)
    root = Path.cwd().resolve()
    before_source = source_hashes(root)
    before_jars = {str(path): hashlib.sha256(path.read_bytes()).hexdigest()
                   for path in (root / 'build/libs').glob('*.jar')}
    if not before_jars:
        raise AssertionError('Run only after the tested canonical JAR has been built')
    baseline, baseline_unlocated = diagnostics((root / 'candidate-compile.log').read_text(encoding='utf-8'))
    log_path = root / 'unsuppressed-compile.log'
    source_sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    with tempfile.TemporaryDirectory(prefix='ie2-deprecation-audit-', dir=os.environ.get('RUNNER_TEMP')) as temporary:
        copy = Path(temporary) / 'source'
        subprocess.run(['git', 'worktree', 'add', '--detach', str(copy), 'HEAD'], check=True)
        try:
            removed = []
            for path in sorted((copy / 'src/main').rglob('*.kt')):
                original = path.read_text(encoding='utf-8')
                changed, count = expose(original)
                if count:
                    path.write_text(changed, encoding='utf-8')
                    removed.append({'path': str(path.relative_to(copy)), 'annotation_sites': count})
            wrapper = copy / 'gradlew'
            wrapper.chmod(wrapper.stat().st_mode | 0o111)
            with log_path.open('w', encoding='utf-8') as log:
                completed = subprocess.run([
                    str(wrapper), 'clean', 'compileJava', 'compileKotlin', '--no-daemon', '--console=plain',
                    '-PpaperApiVersion=1.21.11-R0.1-SNAPSHOT', '-PtargetJvm=21',
                    '-PslimefunApiCoordinate=com.github.slimefun:Slimefun:IE2-CANDIDATE-SNAPSHOT',
                ], cwd=copy, stdout=log, stderr=subprocess.STDOUT, timeout=480)
            full, unlocated = diagnostics(log_path.read_text(encoding='utf-8'))
            ordinary_counts = Counter((path, reason) for path, _, _, reason in baseline)
            exposed_counts = Counter((path, reason) for path, _, _, reason in full)
            added = exposed_counts - ordinary_counts
            report = {
                'source_sha': source_sha,
                'paper_api': '1.21.11-R0.1-SNAPSHOT',
                'core': 'f5656f43d220de1fac59e1492eb1f5248af129ab',
                'compile_exit_code': completed.returncode,
                'modified_only_in_disposable_worktree': removed,
                'normal_visible_diagnostics': len(baseline),
                'unsuppressed_diagnostics': len(full),
                'newly_exposed_diagnostics': sum(added.values()),
                'newly_exposed_by_file_and_reason': [
                    {'path': path, 'reason': reason, 'count': count}
                    for (path, reason), count in sorted(added.items())
                ],
                'all_diagnostics': [{'path': p, 'line': r, 'column': c, 'reason': d} for p, r, c, d in full],
                'unlocated_diagnostic_lines': unlocated,
                'normal_unlocated_diagnostic_lines': baseline_unlocated,
                'limits': 'Own-source compiler evidence only. Bundled code, indirect library calls and runtime behavior are not certified.',
            }
            (root / 'unsuppressed-deprecation-audit.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
            print(f'Isolated audit: {len(removed)} files exposed; {len(baseline)} visible -> {len(full)} unsuppressed diagnostics; {sum(added.values())} newly exposed.')
            if completed.returncode:
                print('\n'.join(log_path.read_text(encoding='utf-8').splitlines()[-80:]))
                raise RuntimeError('Isolated diagnostic compilation failed; inspect retained log')
        finally:
            subprocess.run(['git', 'worktree', 'remove', '--force', str(copy)], check=True)
            if source_hashes(root) != before_source:
                raise AssertionError('Diagnostic audit changed production source/resources')
            after_jars = {str(path): hashlib.sha256(path.read_bytes()).hexdigest()
                          for path in (root / 'build/libs').glob('*.jar')}
            if after_jars != before_jars:
                raise AssertionError('Diagnostic audit changed the tested canonical JAR')
            print('Production source/resources and tested JAR hashes unchanged after isolated audit.')


if __name__ == '__main__':
    main()
