#!/usr/bin/env python3
"""Reverse only the approved reactor migration and compare exact historical source blobs."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path('src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/generators')
BASELINE = {
    'InfinityReactor': ('575f1ef5d0e001109eee8231e7a878ce71b382a9', 4),
    'InfinitySingularityReactor': ('61ccffe32242832f27f0530adf040496c5f1dd54', 2),
}
CONFIG = 'me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config'


def once(text, old, new):
    if text.count(old) != 1:
        raise AssertionError('Missing or duplicate reactor boundary: ' + old)
    return text.replace(old, new, 1)


def restore_reactor_source(name, text):
    if name not in BASELINE:
        return text
    if 'Suppress(' in text or 'BlockStorage' in text or 'ChatColor' in text:
        raise AssertionError(name + ': suppressed or old normal-path API returned')
    text = '@file:Suppress("deprecation")\n\n' + text
    text = once(text,
        'import net.guizhanss.infinityexpansion2.utils.storage.StoredBlockLookup\n',
        'import ' + CONFIG + '\nimport me.mrCookieSlime.Slimefun.api.BlockStorage\n')
    text = once(text, 'import net.guizhanss.infinityexpansion2.utils.items.ReactorText\n',
        'import net.guizhanss.infinityexpansion2.utils.items.MachineLore\n')
    text = once(text, 'import org.bukkit.Location\n', 'import org.bukkit.ChatColor\nimport org.bukkit.Location\n')
    text = once(text, 'data: ' + CONFIG + '): Int = generateAt(l)', 'data: Config): Int = generateAt(l)')
    text = once(text, 'StoredBlockLookup.getInventory(l)', 'BlockStorage.getInventory(l)')
    text, count = re.subn(r'ReactorText\.grayNumber\((\w+)\)',
        lambda m: '"${ChatColor.GRAY}${MachineLore.format(' + m.group(1) + ')}"', text)
    expected, color_count = BASELINE[name]
    if count != color_count:
        raise AssertionError(name + ': number or shape of presentation calls changed')
    raw = text.encode('utf-8')
    actual = hashlib.sha1(b'blob ' + str(len(raw)).encode('ascii') + b'\0' + raw).hexdigest()
    if actual != expected:
        raise AssertionError(f'{name}: unrelated source drift: {actual} != {expected}')
    return text


def verify_reactor_sources(candidate=Path('.')):
    for name in BASELINE:
        restore_reactor_source(name, (candidate / ROOT / (name + '.kt')).read_text(encoding='utf-8'))
        print(name + ': blanket suppression removed; exact baseline reconstructed with approved changes only')
    remaining = []
    for path in sorted((candidate / 'src/main').rglob('*')):
        if path.suffix not in ('.kt', '.java'):
            continue
        for row, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
            if re.search(r'@(?:file:)?Suppress(?:Warnings)?\(', line) and 'deprecat' in line.lower():
                remaining.append({'path': str(path.relative_to(candidate)), 'line': row, 'annotation': line.strip()})
    report = {'remaining_annotation_sites': remaining,
        'note': 'Lexical inventory, not a compiler count; bundled-library code and indirect API calls remain separate.'}
    (candidate / 'reactor-suppression-inventory.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f'{len(remaining)} other deprecation-suppression annotation sites inventoried; not claimed cleaned up.')


if __name__ == '__main__':
    verify_reactor_sources()
