#!/usr/bin/env python3
"""Reverse only the approved ticker adapters and verify exact historical source blobs.

This is a source-scope guard, not proof of runtime correctness. The JUnit suite
independently executes concrete, neutral-container and retained Config callbacks.
"""
from pathlib import Path
import hashlib
import re
import sys

ROOT = Path('src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items')
MODERN_IMPORT = 'import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData\n'
CONFIG = 'me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config'
ANNOTATION = '            @Deprecated("Compatibility bridge; use the block-data ticker overload")\n'
BASELINE = {
    'machines/PoweredBedrock.kt': '291143e75447d7ffc22f92fbbe7796a5005cf676',
    'sfextension/NetherStarReactor.kt': '782186939da6ce0f2df80cf94064b02a3c07149e',
    'storage/StorageUnit.kt': '57a674fd27514fb4f085fefa220d85d45a3ecea5',
}


def once(text, old, new):
    if text.count(old) != 1:
        raise AssertionError('Missing or duplicated approved boundary: ' + old[:100])
    return text.replace(old, new, 1)


def verify_sources():
    for path, expected in BASELINE.items():
        text = (ROOT / path).read_text(encoding='utf-8')
        text = once(text, MODERN_IMPORT, '')
        text = once(text, ANNOTATION, '')
        if path.startswith('machines/'):
            addition = '''            // The core's neutral-container overload dispatches here after its block-type check.
            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) {
                tickBlock(b)
            }

            override fun tick(b: Block, item: SlimefunItem, data: ''' + CONFIG + ''') {
                tickBlock(b)
            }

            private fun tickBlock(b: Block) {'''
            text = once(text, addition, '            override fun tick(b: Block, item: SlimefunItem, data: Config) {')
        elif path.startswith('storage/'):
            text = once(text, '''            override fun tick(b: Block, item: SlimefunItem, data: SlimefunBlockData) {
                tick(b)
            }

''', '')
            text = once(text, 'data: ' + CONFIG + ')', 'data: Config)')
        else:
            text = once(text, '''            override fun tick(b: Block, sfItem: SlimefunItem, data: SlimefunBlockData) {
                // No per-block work; retain the existing uniqueTick-only cadence.
            }

''', '')
            text = once(text, 'config: ' + CONFIG + ')', 'config: CSCoreLibConfig)')
            anchor = 'import io.github.thebusybiscuit.slimefun4.implementation.items.electric.reactors.NetherStarReactor as SlimefunNetherStarReactor\n'
            text = once(text, anchor, anchor + 'import ' + CONFIG + ' as CSCoreLibConfig\n')
        if not path.startswith('sfextension/'):
            anchor = 'import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker\n'
            text = once(text, anchor, 'import ' + CONFIG + '\n' + anchor)
        raw = text.encode('utf-8')
        actual = hashlib.sha1(b'blob ' + str(len(raw)).encode('ascii') + b'\0' + raw).hexdigest()
        if actual != expected:
            raise AssertionError(f'{path}: unrelated source drift: {actual} != {expected}')
        print(f'{path}: complete historical body/source preserved ({expected})')


def verify_diagnostics(log):
    diagnostics = set()
    pattern = re.compile(r'^w: .*?/src/main/kotlin/(.+?\.kt):(\d+):\d+ (.+)$')
    for line in Path(log).read_text(encoding='utf-8').splitlines():
        if not line.startswith('w:') or 'deprecat' not in line.lower():
            continue
        if not any('/' + path + ':' in line for path in BASELINE):
            continue
        match = pattern.match(line)
        if match is None:
            raise AssertionError('Unparsed ticker deprecation: ' + line)
        path, row, reason = match.groups()
        source = (Path('src/main/kotlin') / path).read_text(encoding='utf-8').splitlines()
        index = int(row) - 1
        bridge = (index > 0 and source[index - 1].strip().startswith('@Deprecated(')
                  and source[index].strip().startswith('override fun tick(')
                  and CONFIG in source[index])
        if reason == "'class Config : Any' is deprecated. Deprecated in Java." and bridge:
            diagnostics.add((path, row))
        elif path.endswith('/sfextension/NetherStarReactor.kt') and reason == "'class BlockStorage : Any' is deprecated. Deprecated in Java.":
            # Wither-proof block lookup and its import are separate, unchanged storage work.
            continue
        else:
            raise AssertionError('Unexpected ticker deprecation: ' + line)
    if len(diagnostics) > 3:
        raise AssertionError('Additional deprecated compatibility descriptors introduced')
    print(f'{len(diagnostics)} intentional ticker Config descriptor diagnostics remain visible.')


if __name__ == '__main__':
    verify_sources()
    if len(sys.argv) == 2:
        verify_diagnostics(sys.argv[1])
    elif len(sys.argv) != 1:
        raise SystemExit('Usage: verify_ticker_callback_parity.py [compiler-log]')
