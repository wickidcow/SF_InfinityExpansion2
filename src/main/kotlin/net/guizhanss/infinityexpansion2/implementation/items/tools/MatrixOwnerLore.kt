package net.guizhanss.infinityexpansion2.implementation.items.tools

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID

/** Updates only the first owner display line; identity and ownership remain in the existing caller. */
internal object MatrixOwnerLore {
    @JvmStatic
    fun update(meta: ItemMeta, owner: UUID?) {
        // Older items can legitimately lack lore. Copy the list without flattening
        // existing translation, styling, font or hover components into legacy text.
        val lore = meta.lore()?.toMutableList() ?: mutableListOf()
        val text = PlainTextComponentSerializer.plainText()
        val lineIdx = lore.indexOfFirst { text.serialize(it).startsWith("Owner:") }
        val line = LegacyComponentSerializer.legacySection().deserialize("\u00a7bOwner: \u00a7f${owner ?: "None"}")
        if (lineIdx == -1) lore.add(line) else lore[lineIdx] = line
        meta.lore(lore)
    }
}
