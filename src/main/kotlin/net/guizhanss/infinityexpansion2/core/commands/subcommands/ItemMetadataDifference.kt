package net.guizhanss.infinityexpansion2.core.commands.subcommands

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
        val changedStackFields: List<String>
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
            changedStackFields = stackFields
        )
    }
}
