package net.guizhanss.infinityexpansion2.core.migration

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.Chunk
import org.bukkit.block.Container
import org.bukkit.entity.Item
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Field
import java.util.logging.Level

/** Loaded-only item migration used by Slimefun Doctor. Never rewrites placed block identities. */
class LegacyDoctorItemMigrationService {

    private val itemMigrator: LegacyItemMigrator
        get() = InfinityExpansion2.migrationService.itemMigrator

    fun scanLoaded(migrate: Boolean): LegacyMigrationService.MigrationStats {
        val stats = LegacyMigrationService.MigrationStats()

        // Machine menus are inventories too, but block identity is deliberately read-only here.
        loadedSlimefunBlockData().forEach { data ->
            val menu = call(data, "getBlockMenu") as? BlockMenu ?: return@forEach
            stats += scanInventory(menu.toInventory(), migrate)
        }

        InfinityExpansion2.instance.server.worlds.forEach { world ->
            world.loadedChunks.forEach { chunk -> stats += scanChunkItems(chunk, migrate) }
        }
        InfinityExpansion2.instance.server.onlinePlayers.forEach { player ->
            stats += scanInventory(player.inventory, migrate)
            stats += scanInventory(player.enderChest, migrate)
        }
        return stats
    }

    private fun scanInventory(inventory: Inventory, migrate: Boolean): LegacyMigrationService.MigrationStats {
        val stats = LegacyMigrationService.MigrationStats()
        for (slot in 0 until inventory.size) {
            val original = inventory.getItem(slot) ?: continue
            val sourceId = itemMigrator.rawSlimefunId(original)
            val target = LegacyIdMapper.targetFor(sourceId)
            if (target != null) stats.legacyItemsFound++
            if (!migrate || target == null) continue

            try {
                val result = itemMigrator.migrate(original, false) ?: continue
                if (result.changed) {
                    inventory.setItem(slot, result.item)
                    stats.itemsMigrated++
                }
            } catch (t: Throwable) {
                stats.itemFailures++
                InfinityExpansion2.log(Level.WARNING, t, "IE1 Doctor item migration failed in inventory slot $slot")
            }
        }
        return stats
    }

    private fun scanChunkItems(chunk: Chunk, migrate: Boolean): LegacyMigrationService.MigrationStats {
        val stats = LegacyMigrationService.MigrationStats()

        chunk.tileEntities.forEach { state ->
            val container = state as? Container ?: return@forEach
            stats += scanInventory(container.inventory, migrate)
        }

        chunk.entities.forEach { entity ->
            when (entity) {
                is Player -> Unit
                is Item -> {
                    val result = migrateSingle(entity.itemStack, migrate)
                    stats += result.first
                    if (migrate && result.second != null) entity.itemStack = result.second!!
                }
                is ItemFrame -> {
                    val result = migrateSingle(entity.item, migrate)
                    stats += result.first
                    if (migrate && result.second != null) entity.setItem(result.second!!)
                }
                is ItemDisplay -> {
                    val result = migrateSingle(entity.itemStack, migrate)
                    stats += result.first
                    if (migrate && result.second != null) entity.setItemStack(result.second!!)
                }
                is LivingEntity -> {
                    val equipment = entity.equipment ?: return@forEach
                    val armor = equipment.armorContents
                    var armorChanged = false
                    val modernArmor = armor.map { stack ->
                        val result = migrateSingle(stack, migrate)
                        stats += result.first
                        if (result.second != null) armorChanged = true
                        result.second ?: stack
                    }.toTypedArray()
                    if (migrate && armorChanged) equipment.armorContents = modernArmor

                    val main = migrateSingle(equipment.itemInMainHand, migrate)
                    stats += main.first
                    if (migrate && main.second != null) equipment.setItemInMainHand(main.second)
                    val off = migrateSingle(equipment.itemInOffHand, migrate)
                    stats += off.first
                    if (migrate && off.second != null) equipment.setItemInOffHand(off.second)
                }
            }

            if (entity is InventoryHolder && entity !is Player) {
                stats += scanInventory(entity.inventory, migrate)
            }
        }
        return stats
    }

    private fun migrateSingle(
        stack: ItemStack?,
        migrate: Boolean,
    ): Pair<LegacyMigrationService.MigrationStats, ItemStack?> {
        val stats = LegacyMigrationService.MigrationStats()
        if (stack == null || stack.type.isAir) return stats to null
        val sourceId = itemMigrator.rawSlimefunId(stack)
        val target = LegacyIdMapper.targetFor(sourceId)
        if (target != null) stats.legacyItemsFound++
        if (!migrate || target == null) return stats to null

        return try {
            val result = itemMigrator.migrate(stack, false)
            if (result?.changed == true) {
                stats.itemsMigrated++
                stats to result.item
            } else {
                stats to null
            }
        } catch (t: Throwable) {
            stats.itemFailures++
            InfinityExpansion2.log(Level.WARNING, t, "IE1 Doctor item migration failed for an entity-held item")
            stats to null
        }
    }

    private fun loadedSlimefunBlockData(): List<Any> {
        val controller = blockDataController() ?: return emptyList()
        val result = ArrayList<Any>()
        loadedChunkData(controller).forEach { chunkData ->
            @Suppress("UNCHECKED_CAST")
            val data = call(chunkData, "getAllBlockData") as? Collection<Any> ?: return@forEach
            result.addAll(data)
        }
        return result
    }

    private fun blockDataController(): Any? {
        val databaseManager = runCatching {
            Slimefun::class.java.methods.first { it.name == "getDatabaseManager" && it.parameterCount == 0 }
                .invoke(null)
        }.getOrNull() ?: return null
        val getter = databaseManager.javaClass.methods.firstOrNull {
            it.name == "getBlockDataController" && it.parameterCount == 0
        }
        getter?.let { return runCatching { it.invoke(databaseManager) }.getOrNull() }
        val field = findField(databaseManager.javaClass, "blockDataController") ?: return null
        return runCatching {
            field.isAccessible = true
            field.get(databaseManager)
        }.getOrNull()
    }

    private fun loadedChunkData(controller: Any): Collection<Any> {
        val preferred = listOf("loadedChunk", "loadedChunks", "loadedChunkData")
            .asSequence()
            .mapNotNull { findField(controller.javaClass, it) }
            .mapNotNull { mapValues(controller, it) }
            .firstOrNull { it.isNotEmpty() }
        if (preferred != null) return preferred

        for (field in allFields(controller.javaClass)) {
            val values = mapValues(controller, field) ?: continue
            val sample = values.firstOrNull() ?: continue
            if (sample.javaClass.methods.any { it.name == "getAllBlockData" && it.parameterCount == 0 }) return values
        }
        return emptyList()
    }

    private fun mapValues(target: Any, field: Field): Collection<Any>? = runCatching {
        field.isAccessible = true
        val value = field.get(target) as? Map<*, *> ?: return@runCatching null
        value.values.filterNotNull()
    }.getOrNull()

    private fun allFields(type: Class<*>): Sequence<Field> = sequence {
        var current: Class<*>? = type
        while (current != null) {
            val clazz = current
            yieldAll(clazz.declaredFields.asSequence())
            current = clazz.superclass
        }
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun call(target: Any, name: String): Any? = runCatching {
        target.javaClass.methods.first { it.name == name && it.parameterCount == 0 }.invoke(target)
    }.getOrNull()
}
