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
    from verify_owned_deprecations import verify
    return verify(log)
