package net.guizhanss.infinityexpansion2.core.migration

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import java.util.TreeMap
import java.util.UUID
import java.util.logging.Level

/** Exact loaded IE1 placed-block migration used only behind Slimefun Doctor authorization. */
class LegacyDoctorBlockMigrationService {

    data class Candidate(
        val worldId: UUID,
        val x: Int,
        val y: Int,
        val z: Int,
        val sourceId: String,
        val targetId: String,
        val stateClaim: String,
    )

    enum class Status { MIGRATED, SKIPPED_CHANGED, BLOCKED, FAILED }

    data class Result(val status: Status, val detail: String)

    private data class Live(
        val controller: Any,
        val data: Any,
        val location: Location,
    )

    private val itemMigrator: LegacyItemMigrator
        get() = InfinityExpansion2.migrationService.itemMigrator

    fun mappings(): Map<String, String> = LegacyIdMapper.resolvedAliases()

    fun scanLoadedCandidates(): List<Candidate> {
        val controller = blockDataController() ?: return emptyList()
        val mappings = mappings()
        return loadedBlockData(controller).mapNotNull { data ->
            val sourceId = call(data, "getSfId") as? String ?: return@mapNotNull null
            val targetId = mappings[sourceId] ?: return@mapNotNull null
            val location = call(data, "getLocation") as? Location ?: return@mapNotNull null
            val world = location.world ?: return@mapNotNull null
            if (!world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) return@mapNotNull null
            Candidate(
                world.uid,
                location.blockX,
                location.blockY,
                location.blockZ,
                sourceId,
                targetId,
                claim(data, sourceId, targetId),
            )
        }
    }

    fun isCandidateStillValid(candidate: Candidate): Boolean {
        val expectedTarget = mappings()[candidate.sourceId] ?: return false
        if (expectedTarget != candidate.targetId) return false
        val live = live(candidate) ?: return false
        return candidate.stateClaim == claim(live.data, candidate.sourceId, candidate.targetId)
    }

    fun migrate(candidate: Candidate): Result {
        val expectedTarget = mappings()[candidate.sourceId]
            ?: return Result(Status.BLOCKED, "Legacy mapping is no longer available.")
        if (expectedTarget != candidate.targetId) {
            return Result(Status.BLOCKED, "Legacy mapping changed after authorization.")
        }

        val live = live(candidate)
            ?: return Result(Status.SKIPPED_CHANGED, "Block is no longer loaded with the approved legacy ID.")
        if (candidate.stateClaim != claim(live.data, candidate.sourceId, candidate.targetId)) {
            return Result(Status.SKIPPED_CHANGED, "Block state changed after authorization; nothing was modified.")
        }

        return try {
            migrateBlockLosslessly(live.controller, live.data, candidate.sourceId, candidate.targetId, live.location)
            Result(Status.MIGRATED, "${candidate.sourceId} -> ${candidate.targetId}")
        } catch (t: Throwable) {
            val cause = if (t is InvocationTargetException && t.cause != null) t.cause!! else t
            InfinityExpansion2.log(Level.WARNING, cause, "Exact IE1 block migration failed at ${live.location}")
            Result(Status.FAILED, "Migration failed; IE2 attempted rollback to the original block record.")
        }
    }

    private fun live(candidate: Candidate): Live? {
        val world: World = InfinityExpansion2.instance.server.getWorld(candidate.worldId) ?: return null
        if (!world.isChunkLoaded(candidate.x shr 4, candidate.z shr 4)) return null
        val location = Location(world, candidate.x.toDouble(), candidate.y.toDouble(), candidate.z.toDouble())
        val controller = blockDataController() ?: return null
        val data = loadedBlockData(controller).firstOrNull { blockData ->
            val found = call(blockData, "getLocation") as? Location ?: return@firstOrNull false
            sameBlock(location, found) && candidate.sourceId == (call(blockData, "getSfId") as? String)
        } ?: return null
        return Live(controller, data, location)
    }

    private fun claim(data: Any, sourceId: String, targetId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        update(digest, "ie2-block-v1\n$sourceId\n$targetId\n")
        snapshotData(data).toSortedMap().forEach { (key, value) ->
            update(digest, "kv:$key=$value\n")
        }
        val menu = snapshotMenu(data)
        if (menu == null) {
            update(digest, "menu:none\n")
        } else {
            menu.forEachIndexed { slot, stack ->
                if (stack == null || stack.type.isAir) return@forEachIndexed
                update(digest, "slot:$slot:")
                digest.update(stack.serializeAsBytes())
                update(digest, "\n")
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun migrateBlockLosslessly(
        controller: Any,
        oldData: Any,
        sourceId: String,
        targetId: String,
        location: Location,
    ) {
        SlimefunItem.getById(targetId) ?: error("Target Slimefun item $targetId is not registered")

        val originalData = snapshotData(oldData)
        val originalMenu = snapshotMenu(oldData)
        val targetData = originalData.toMutableMap()
        val targetMenu = originalMenu?.map { stack ->
            stack?.let { itemMigrator.migrate(it.clone(), false)?.item ?: it.clone() }
        }?.toTypedArray()

        if (sourceId in LEGACY_STORAGE_IDS) {
            targetData["stored"]?.let { oldAmount -> targetData.putIfAbsent("stored_amount", oldAmount) }
            targetData.remove("stored")
        }

        val remove = removeMethod(controller)
        val create = createMethod(controller)
        remove.invoke(controller, location)

        try {
            val newData = create.invoke(controller, location, targetId)
                ?: error("Slimefun block controller returned null while creating $targetId")
            restoreData(newData, targetData, sourceId)
            restoreMenuLosslessly(newData, targetMenu)
            saveBlockInventory(controller, newData)
        } catch (migrationFailure: Throwable) {
            try {
                remove.invoke(controller, location)
                val restored = create.invoke(controller, location, sourceId)
                    ?: error("Slimefun block controller returned null while rolling back $sourceId")
                restoreData(restored, originalData, null)
                restoreMenuLosslessly(restored, originalMenu)
                saveBlockInventory(controller, restored)
            } catch (rollbackFailure: Throwable) {
                migrationFailure.addSuppressed(rollbackFailure)
            }
            throw migrationFailure
        }
    }

    private fun snapshotData(data: Any): Map<String, String> {
        @Suppress("UNCHECKED_CAST")
        return ((call(data, "getAllData") as? Map<String, String>) ?: emptyMap()).toMap()
    }

    private fun snapshotMenu(data: Any): Array<ItemStack?>? {
        val direct = call(data, "getMenuContents")
        if (direct is Array<*>) {
            return Array(direct.size) { index -> (direct[index] as? ItemStack)?.clone() }
        }
        if (direct is Collection<*>) {
            return direct.map { (it as? ItemStack)?.clone() }.toTypedArray()
        }
        val menu = call(data, "getBlockMenu") as? BlockMenu ?: return null
        return menu.toInventory().contents.map { it?.clone() }.toTypedArray()
    }

    private fun restoreData(data: Any, values: Map<String, String>, migratedFrom: String?) {
        val setData = data.javaClass.methods.firstOrNull { it.name == "setData" && it.parameterCount == 2 }
        if (setData == null && values.isNotEmpty()) error("Slimefun block data exposes no setData method")
        values.forEach { (key, value) -> setData?.invoke(data, key, value) }
        if (migratedFrom != null) setData?.invoke(data, LegacyMigrationService.MIGRATION_KEY, "ie1:$migratedFrom")
    }

    private fun restoreMenuLosslessly(data: Any, contents: Array<ItemStack?>?) {
        if (contents == null) return
        val menu = call(data, "getBlockMenu") as? BlockMenu
        if (menu == null) {
            if (contents.any { it != null && !it.type.isAir }) {
                error("Target block did not hydrate a menu required to preserve legacy contents")
            }
            return
        }

        val inventory = menu.toInventory()
        for (slot in inventory.size until contents.size) {
            val stack = contents[slot]
            if (stack != null && !stack.type.isAir) {
                error("Target menu is too small to preserve legacy slot $slot")
            }
        }

        val length = minOf(contents.size, inventory.size)
        for (slot in 0 until length) {
            inventory.setItem(slot, contents[slot]?.clone())
        }
    }

    private fun saveBlockInventory(controller: Any, data: Any) {
        val method = controller.javaClass.methods.firstOrNull {
            it.name == "saveBlockInventory" && it.parameterCount == 1 &&
                it.parameterTypes[0].isAssignableFrom(data.javaClass)
        } ?: return
        runCatching { method.invoke(controller, data) }
    }

    private fun removeMethod(controller: Any) = controller.javaClass.methods.firstOrNull {
        it.name == "removeBlock" && it.parameterCount == 1 &&
            it.parameterTypes[0].isAssignableFrom(Location::class.java)
    } ?: controller.javaClass.methods.firstOrNull { it.name == "removeBlock" && it.parameterCount == 1 }
        ?: error("Slimefun block controller has no compatible removeBlock method")

    private fun createMethod(controller: Any) = controller.javaClass.methods.firstOrNull {
        it.name == "createBlock" && it.parameterCount == 2 &&
            it.parameterTypes[0].isAssignableFrom(Location::class.java) &&
            it.parameterTypes[1] == String::class.java
    } ?: controller.javaClass.methods.firstOrNull { it.name == "createBlock" && it.parameterCount == 2 }
        ?: error("Slimefun block controller has no compatible createBlock method")

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

    private fun loadedBlockData(controller: Any): List<Any> {
        val result = ArrayList<Any>()
        loadedChunkData(controller).forEach { chunkData ->
            @Suppress("UNCHECKED_CAST")
            val data = call(chunkData, "getAllBlockData") as? Collection<Any> ?: return@forEach
            result.addAll(data)
        }
        return result
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

    private fun sameBlock(a: Location, b: Location): Boolean =
        a.world?.uid == b.world?.uid && a.blockX == b.blockX && a.blockY == b.blockY && a.blockZ == b.blockZ

    private fun update(digest: MessageDigest, text: String) {
        digest.update(text.toByteArray(StandardCharsets.UTF_8))
    }

    companion object {
        private val LEGACY_STORAGE_IDS = setOf(
            "BASIC_STORAGE", "ADVANCED_STORAGE", "REINFORCED_STORAGE", "VOID_STORAGE", "INFINITY_STORAGE"
        )
    }
}
