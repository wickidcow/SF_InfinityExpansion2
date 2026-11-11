package net.guizhanss.infinityexpansion2.core.commands.subcommands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.inventory.ItemStack

/**
 * Read-only diagnostic for the *actual* held stacks. An item can share a
 * registered Slimefun ID with another stack and still refuse vanilla stacking
 * because its full Bukkit/Paper metadata differs.
 *
 * No item templates are canonicalized and no player inventories are changed.
 * Never include serialized values here: skull textures and addon owner data
 * may be private, and those fields can be very large.
 */
internal object ItemMetadataDifference {

    internal data class Report(
        val isSimilar: Boolean,
        val mainMaxStackSize: Int,
        val offhandMaxStackSize: Int,
        val materialSame: Boolean,
        val nameSame: Boolean,
        val loreSame: Boolean,
        val itemMetaSame: Boolean,
        val pdcSame: Boolean,
        val onlyMainPdcKeys: List<String>,
        val onlyOffhandPdcKeys: List<String>,
        val changedMetaFields: List<String>,
        val changedStackFields: List<String>,
        val componentStructureDifferences: List<String>
    )

    fun inspect(main: ItemStack, offhand: ItemStack): Report {
        val firstMeta = main.itemMeta
        val secondMeta = offhand.itemMeta
        val firstPdc = firstMeta?.persistentDataContainer
        val secondPdc = secondMeta?.persistentDataContainer
        val firstKeys = firstPdc?.keys?.map { it.toString() }?.toSet() ?: emptySet()
        val secondKeys = secondPdc?.keys?.map { it.toString() }?.toSet() ?: emptySet()

        val firstSerialized = firstMeta?.serialize() ?: emptyMap()
        val secondSerialized = secondMeta?.serialize() ?: emptyMap()
        val metaFields = (firstSerialized.keys + secondSerialized.keys).toSortedSet()
            .filter { firstSerialized[it] != secondSerialized[it] }

        // Compare the component serialization of independent ONE-item clones.
        // Bukkit/MockBukkit serializers may expose count under different keys;
        // filtering "amount" alone can report false differences. Never edit
        // either real stack or assume its quantity is part of its identity.
        val firstClone = main.clone().apply { amount = 1 }
        val secondClone = offhand.clone().apply { amount = 1 }
        val firstStack = firstClone.serialize().filterKeys { it != "amount" }
        val secondStack = secondClone.serialize().filterKeys { it != "amount" }
        val stackFields = (firstStack.keys + secondStack.keys).toSortedSet()
            .filter { it != "meta" && firstStack[it] != secondStack[it] }

        return Report(
            isSimilar = main.isSimilar(offhand),
            mainMaxStackSize = main.maxStackSize,
            offhandMaxStackSize = offhand.maxStackSize,
            materialSame = main.type == offhand.type,
            nameSame = firstMeta?.displayName() == secondMeta?.displayName(),
            loreSame = firstMeta?.lore() == secondMeta?.lore(),
            itemMetaSame = firstMeta == secondMeta,
            pdcSame = firstPdc == secondPdc,
            onlyMainPdcKeys = (firstKeys - secondKeys).sorted(),
            onlyOffhandPdcKeys = (secondKeys - firstKeys).sorted(),
            changedMetaFields = metaFields,
            changedStackFields = stackFields,
            componentStructureDifferences = componentDifferences(
                firstMeta?.displayName(), secondMeta?.displayName(), firstMeta?.lore(), secondMeta?.lore()
            )
        )
    }
    /**
     * Report only Adventure component shape/style and the *existence* of changed payload.
     * Never print text, hover/click values, insertion, font values, or profile data.
     * A flat bold name and a nested explicitly unbolded name may look similar in
     * a UI, yet remain distinct Bukkit ItemMeta and refuse vanilla stacking.
     */
    private fun componentDifferences(
        mainName: Component?,
        offhandName: Component?,
        mainLore: List<Component>?,
        offhandLore: List<Component>?
    ): List<String> {
        val changes = mutableListOf<String>()
        fun decorations(component: Component): String =
            "bold=${component.decoration(TextDecoration.BOLD)}, " +
                "italic=${component.decoration(TextDecoration.ITALIC)}"

        fun compare(path: String, main: Component?, offhand: Component?, depth: Int) {
            if (changes.size >= 16 || main == offhand) return
            if (main == null || offhand == null) {
                val only = main ?: offhand ?: return
                changes += "$path: component only in ${if (main == null) "offhand" else "main"} " +
                    "(children=${only.children().size}, ${decorations(only)})"
                return
            }
            if (main.javaClass != offhand.javaClass) {
                changes += "$path: component types differ"
            }
            if (main.children().size != offhand.children().size) {
                changes += "$path: child count ${main.children().size}/${offhand.children().size}"
            }
            for (decoration in TextDecoration.entries) {
                val first = main.decoration(decoration)
                val second = offhand.decoration(decoration)
                if (first != second) {
                    changes += "$path: ${decoration.name.lowercase()} $first/$second"
                }
            }
            if (main.color() != offhand.color()) changes += "$path: color differs"
            if (main is TextComponent && offhand is TextComponent &&
                main.content() != offhand.content()) {
                changes += "$path: text differs (redacted)"
            }
            if (main.hoverEvent() != offhand.hoverEvent()) changes += "$path: hover differs (redacted)"
            if (main.clickEvent() != offhand.clickEvent()) changes += "$path: click differs (redacted)"
            if (main.insertion() != offhand.insertion()) changes += "$path: insertion differs (redacted)"
            if (depth >= 2 || changes.size >= 16) return
            val count = maxOf(main.children().size, offhand.children().size).coerceAtMost(3)
            for (index in 0 until count) {
                compare("$path/$index", main.children().getOrNull(index),
                    offhand.children().getOrNull(index), depth + 1)
            }
        }

        if (mainName != offhandName) compare("name", mainName, offhandName, 0)
        if (mainLore != offhandLore) {
            if (mainLore?.size != offhandLore?.size) {
                changes += "lore: line count ${mainLore?.size ?: 0}/${offhandLore?.size ?: 0}"
            }
            val count = maxOf(mainLore?.size ?: 0, offhandLore?.size ?: 0).coerceAtMost(3)
            for (index in 0 until count) {
                compare("lore[$index]", mainLore?.getOrNull(index), offhandLore?.getOrNull(index), 0)
            }
        }
        if (changes.isEmpty() && (mainName != offhandName || mainLore != offhandLore)) {
            changes += "Adventure components differ (payload details redacted)"
        }
        return changes.take(16)
    }

}
