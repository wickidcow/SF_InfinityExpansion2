#!/usr/bin/env python3
"""Check the exact scope and remaining visible compiler deprecations of storage lookup migration."""
import hashlib
import re
from pathlib import Path

ROOT = Path('src/main/kotlin/net/guizhanss/infinityexpansion2')
NEW_IMPORT = 'import net.guizhanss.infinityexpansion2.utils.storage.StoredBlockLookup'
OLD_IMPORT = 'import me.mrCookieSlime.Slimefun.api.BlockStorage'
SITES = {
    'implementation/items/sfextension/NetherStarReactor.kt': (
        'StoredBlockLookup.getId(hit.location)', 'BlockStorage.getLocationInfo(hit.location, "id")',
        '32dd5230f4bb9638718d74954abec898661e86ed'),
    'implementation/listeners/MobDataCardRefreshListener.kt': (
        'StoredBlockLookup.hasBlockInfo(it)', 'BlockStorage.hasBlockInfo(it)',
        '789e7e89cadf2abb96f0c42e1bc28ead10081adf'),
}


def restore_lookup(path, text):
    for name, (new, old, _) in SITES.items():
        if str(path).endswith(name) or name.endswith(str(path)):
            if text.count(NEW_IMPORT) != 1 or text.count(new) != 1:
                raise AssertionError('Missing/duplicated approved lookup: ' + str(path))
            return text.replace(NEW_IMPORT, OLD_IMPORT).replace(new, old)
    return text


def verify_sources():
    for path, (_, _, expected) in SITES.items():
        raw = restore_lookup(path, (ROOT / path).read_text(encoding='utf-8')).encode('utf-8')
        actual = hashlib.sha1(b'blob ' + str(len(raw)).encode('ascii') + b'\0' + raw).hexdigest()
        if actual != expected:
            raise AssertionError(f'{path}: source drift outside the approved lookup: {actual} != {expected}')
        print(f'{path}: scheduling, identity decisions and failure handling preserved')


def verify_diagnostics(log):
    expected = {
        'EnergyGenerator.kt': 2, 'PoweredBedrock.kt': 1,
        'NetherStarReactor.kt': 1, 'StorageUnit.kt': 1,
    }
    seen = set()
    pattern = re.compile(r'^w: .*?/src/main/kotlin/(.+?\.kt):(\d+):\d+ (.+)$')
    for line in Path(log).read_text(encoding='utf-8').splitlines():
        if '/src/main/' not in line or 'deprecat' not in line.lower():
            continue
        match = pattern.match(line)
        if match is None:
            raise AssertionError('Unexpected/unparsed production deprecation: ' + line)
        path, row, reason = match.groups()
        lines = (Path('src/main/kotlin') / path).read_text(encoding='utf-8').splitlines()
        index = int(row) - 1
        bridge = (index > 0 and lines[index - 1].strip().startswith('@Deprecated(')
                  and lines[index].strip().startswith('override fun ')
                  and 'me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config' in lines[index])
        if (Path(path).name not in expected or not bridge
                or reason != "'class Config : Any' is deprecated. Deprecated in Java."):
            raise AssertionError('Unexpected production deprecation: ' + line)
        seen.add((Path(path).name, row))
    for name, maximum in expected.items():
        if sum(file == name for file, _ in seen) > maximum:
            raise AssertionError('Additional deprecated ABI bridges in ' + name)
    print(f'{len(seen)} retained Config ABI diagnostics; no other visible source deprecation diagnostics')
