#!/usr/bin/env python3
"""Reverse only the approved ticker adapters and verify exact historical source blobs.

This is a source-scope guard, not proof of runtime correctness. The JUnit suite
independently executes concrete, neutral-container and retained Config callbacks.
"""
from pathlib import Path
import hashlib
import re
import sys
from verify_storage_lookup import restore_lookup, verify_sources as verify_storage_sources, verify_diagnostics as verify_storage_diagnostics

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
        # Undo exactly the newly reviewed opt-in cosmetic presentation repair
        # before asserting the original historical storage ticker body hash.
        # An extra or altered ticker mutation is still rejected byte-for-byte.
        if path == 'storage/StorageUnit.kt':
            text = once(text, '''        // Cosmetic reconciliation changes only the private identity template.
        // The separately persisted amount/void ledger is never rewritten here.
        // Both pre- and post-crash templates are proven semantically equivalent;
        // the public physical output slot must already be empty.
        if (cache.amount > 0 && menu.getItemInSlot(outputSlots[0]).isAir()) {
            val canonical = StorageItemPresentation.canonicalIfCosmetic(cache.itemStack)
            if (canonical != null) {
                val original = StorageTemplatePersistence.snapshot(cache.itemStack!!)
                val originalAmount = cache.amount
                try {
                    check(cache.itemStack?.isSimilar(original) == true && cache.amount == originalAmount) {
                        "Storage presentation repair began with an unexpected balance or identity"
                    }
                    menu.repairCosmeticTemplate(original, canonical)
                    check(cache.itemStack?.isSimilar(original) == true && cache.amount == originalAmount) {
                        "Storage presentation repair changed its source identity or balance"
                    }
                    // Commit the in-memory representation only after a verified,
                    // rollback-protected private template write.
                    cache.itemStack = canonical
                } catch (failure: RuntimeException) {
                    cache.itemStack = original
                    cache.blockUnverifiedTransfer()
                    InfinityExpansion2.instance.logger.log(java.util.logging.Level.SEVERE,
                        "Storage cosmetic repair could not be verified at ${menu.location}; " +
                            "the saved balance was not rewritten. Transfers are blocked " +
                            "pending administrator inspection.", failure)
                    return
                }
            }
        }

''', '')
        if path == 'storage/StorageUnit.kt':
            text = once(text, '''        /**
         * Rewrite only the protected ITEM_SLOT after checking that it still
         * contains the exact historical identity. In case of a setter/dirty
         * callback exception, try restoring the old private template. A hard
         * stop may leave either semantically equivalent template, but this
         * method never touches stored_amount or void_excess.
         */
        private fun BlockMenu.repairCosmeticTemplate(original: ItemStack, canonical: ItemStack) {
            check(StorageItemPresentation.matchesRegistered(original, canonical)) {
                "Storage canonical template changed functional identity"
            }
            val existing = getItemInSlot(ITEM_SLOT)
            check(!existing.isAir() && existing.amount == 1 && existing.isDisplayItem() &&
                existing.removeDisplayItem().isSimilar(original)) {
                "Storage private template differs from the historical cached identity"
            }
            val previous = StorageTemplatePersistence.snapshot(original).toDisplayItem().also { it.amount = 1 }
            val replacement = StorageTemplatePersistence.snapshot(canonical).toDisplayItem().also { it.amount = 1 }
            check(previous.removeDisplayItem().isSimilar(original) &&
                replacement.removeDisplayItem().isSimilar(canonical)) {
                "Cosmetic template display encoding changed functional identity"
            }
            StorageTemplatePersistence.updateCosmeticWithRollback(
                previous, replacement,
                { getItemInSlot(ITEM_SLOT) },
                { replaceExistingItem(ITEM_SLOT, it) },
                { markDirty() }
            )
        }

''', '')
        text = restore_lookup(path, text)
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
        else:
            raise AssertionError('Unexpected ticker deprecation: ' + line)
    if len(diagnostics) > 3:
        raise AssertionError('Additional deprecated compatibility descriptors introduced')
    print(f'{len(diagnostics)} intentional ticker Config descriptor diagnostics remain visible.')


if __name__ == '__main__':
    verify_storage_sources()
    verify_sources()
    if len(sys.argv) == 2:
        verify_storage_diagnostics(sys.argv[1])
        verify_diagnostics(sys.argv[1])
    elif len(sys.argv) != 1:
        raise SystemExit('Usage: verify_ticker_callback_parity.py [compiler-log]')
