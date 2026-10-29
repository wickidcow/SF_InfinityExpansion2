#!/usr/bin/env python3
"""Guard the unchanged generation bodies against an exact, independently checked-out baseline."""
from pathlib import Path
import re
import sys

ROOT = Path('src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/generators')
TARGETS = ('EnergyGenerator', 'InfinityReactor', 'InfinitySingularityReactor')


def body(text: str, declaration: str) -> str:
    match = re.search(r'^    ' + re.escape(declaration) + r' \{\n(.*?)^    \}', text, re.M | re.S)
    if match is None:
        raise AssertionError('Missing or reformatted boundary: ' + declaration)
    return match.group(1)


def verify(baseline: Path, candidate: Path = Path('.')) -> None:
    for name in TARGETS:
        old = (baseline / ROOT / (name + '.kt')).read_text(encoding='utf-8')
        new = (candidate / ROOT / (name + '.kt')).read_text(encoding='utf-8')
        before = body(old, 'override fun getGeneratedOutput(l: Location, data: Config): Int')
        after = body(new, 'private fun generateAt(l: Location): Int')
        if before != after:
            raise AssertionError(name + ': historical generation/fuel/progress body changed')
        if new.count('@file:Suppress("deprecation")') != old.count('@file:Suppress("deprecation")'):
            raise AssertionError(name + ': file-wide deprecation suppression changed')
        for signature in (
            'override fun getGeneratedOutput(l: Location, data: ASlimefunDataContainer): Int',
            'override fun getGeneratedOutputLong(l: Location, data: ASlimefunDataContainer): Long',
        ):
            if new.count(signature) != 1:
                raise AssertionError(name + ': missing or duplicate modern callback')
        legacy = re.search(r'@Deprecated\([^\n]+\)\n    override fun getGeneratedOutput\(l: Location, data: (?:me\.mrCookieSlime\.CSCoreLibPlugin\.Configuration\.)?Config\): Int =\s*generateAt\(l\)', new)
        if legacy is None:
            raise AssertionError(name + ': legacy binary bridge no longer directly forwards')
        suffix = '    private fun onBlockClick()' if name == 'EnergyGenerator' else '    private fun ItemStack.'
        if old[old.index(suffix):] != new[new.index(suffix):]:
            raise AssertionError(name + ': unrelated click, fuel identity or presentation code changed')
    for name in ('GeneratorType.kt', 'AbstractReactor.kt'):
        if (baseline / ROOT / name).read_bytes() != (candidate / ROOT / name).read_bytes():
            raise AssertionError(name + ': environmental generation or persistence policy changed')
    print('Exact-baseline parity: all three generation bodies, external behavior suffixes and storage/type policies preserved.')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: verify_generator_callback_parity.py <exact-baseline-checkout>')
    verify(Path(sys.argv[1]))
