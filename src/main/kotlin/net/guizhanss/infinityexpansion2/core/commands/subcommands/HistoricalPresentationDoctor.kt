package net.guizhanss.infinityexpansion2.core.commands.subcommands

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.implementation.items.storage.HistoricalPresentationBackupStore
import net.guizhanss.infinityexpansion2.implementation.items.storage.HistoricalPresentationTransaction
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Explicit, disabled-by-default, main-hand-only IE2 historical item NAME repair.
 * No block, Cargo, Fluffy Barrel, closed inventory or arbitrary lore rewriting.
 * Commands run on the player's owning execution context.
 */
internal object HistoricalPresentationDoctor {
    private const val TTL_MILLIS = 120_000L
    private const val PERMISSION = "infinityexpansion2.doctor.presentation"
    private val pending = mutableMapOf<UUID, Pending>()

    private data class Pending(
        val originalSlot: Int,
        val expiresAt: Long,
        val transaction: HistoricalPresentationTransaction
    )

    private fun send(sender: CommandSender, msg: String, color: NamedTextColor = NamedTextColor.GRAY) {
        sender.sendMessage(Component.text(msg, color))
    }

    private fun directory() =
        InfinityExpansion2.instance.dataFolder.toPath().resolve("doctor-presentation-backups")

    fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player
        if (player == null) {
            send(sender, "Historical IE2 item presentation repair is player-only.", NamedTextColor.RED)
            return
        }
        if (!player.isOp && !player.hasPermission(PERMISSION)) {
            send(sender, "This repair requires operator access or explicit IE2 presentation permission.",
                NamedTextColor.RED)
            return
        }
        pending.entries.removeIf { (_, entry) -> entry.expiresAt <= System.currentTimeMillis() }
        when (args.firstOrNull()?.lowercase(Locale.ENGLISH)) {
            "preview" -> preview(player)
            "confirm" -> confirm(player, args.getOrNull(1))
            "rollback" -> rollback(player, args.getOrNull(1))
            else -> send(player,
                "Usage: /ie2 doctor presentation <preview|confirm TOKEN|rollback BACKUP_UUID>",
                NamedTextColor.YELLOW)
        }
    }

    private fun preview(player: Player) {
        if (!InfinityExpansion2.configService.historicalPresentationRepairEnabled.value) {
            send(player, "Historical presentation repair is OFF. " +
                "To opt in, enable doctor.historical-presentation-repair in config.yml and restart.",
                NamedTextColor.YELLOW)
            return
        }
        if (InfinityExpansion2.configService.lang.value.lowercase(Locale.ENGLISH) != "en") {
            send(player, "Cannot prove an English registered template while IE2 lang is not en.",
                NamedTextColor.RED)
            return
        }
        val live = player.inventory.itemInMainHand
        if (live.type.isAir || !live.hasItemMeta()) {
            send(player, "Hold the old IE2 item in your MAIN hand.", NamedTextColor.YELLOW)
            return
        }
        val meta = live.itemMeta
        val id = Slimefun.getItemDataService().getItemData(meta).orElse(null)
        if (id == null || !id.startsWith("IE_")) {
            send(player, "Not a verified registered IE2 item; no changes.", NamedTextColor.RED)
            return
        }
        val registered = SlimefunItem.getById(id)
        if (registered == null || registered.id != id) {
            send(player, "Missing authoritative IE2 item registration; no changes.", NamedTextColor.RED)
            return
        }
        val registeredStack = registered.item
        val template = ItemStack(registeredStack.type, 1)
        val registeredMeta = registeredStack.itemMeta
        if (registeredMeta == null || !template.setItemMeta(registeredMeta)
                || Slimefun.getItemDataService().getItemData(template.itemMeta).orElse(null) != id) {
            send(player, "Registered template has no trusted Slimefun ID; no changes.",
                NamedTextColor.RED)
            return
        }
        // Limit this action to evident historical CJK names, never custom English
        // owner labels or rich display names that might contain protected data.
        val oldName = meta.displayName()
        val newName = registeredMeta.displayName()
        if (oldName == null || newName == null
                || !containsHan(oldName) || containsHan(newName) || isRich(oldName)) {
            send(player, "No safely identifiable Chinese-to-English name-only change found.",
                NamedTextColor.YELLOW)
            return
        }
        val tx = HistoricalPresentationTransaction.preview(live, template, id, id)
        if (tx == null || live.isSimilar(tx.proposed())) {
            send(player, "No safe preview. Dynamic or custom historical lore is never replaced. " +
                "Use /ie2 doctor compare to inspect source differences.", NamedTextColor.YELLOW)
            return
        }
        pending[player.uniqueId] = Pending(
            player.inventory.heldItemSlot,
            System.currentTimeMillis() + TTL_MILLIS, tx
        )
        send(player, "READ-ONLY preview: " + id + " x" + live.amount +
            " — changes DISPLAY NAME ONLY; retains entire lore, typed PDC and amount.",
            NamedTextColor.GOLD)
        send(player, "No item was changed. Save your world/inventory first. " +
            "Confirm within two minutes using:", NamedTextColor.YELLOW)
        send(player, "/ie2 doctor presentation confirm " + tx.confirmationToken(),
            NamedTextColor.GREEN)
    }

    private fun confirm(player: Player, token: String?) {
        if (!InfinityExpansion2.configService.historicalPresentationRepairEnabled.value) {
            send(player, "Repair is disabled. Existing backups remain available for rollback.",
                NamedTextColor.RED)
            return
        }
        val operation = pending[player.uniqueId]
        if (token == null || operation == null || operation.expiresAt <= System.currentTimeMillis()
                || operation.originalSlot != player.inventory.heldItemSlot) {
            pending.remove(player.uniqueId)
            send(player, "Preview missing, expired, or selected hotbar slot changed. Run preview again.",
                NamedTextColor.RED)
            return
        }
        val actual = player.inventory.itemInMainHand
        val expected = operation.transaction.backup()
        if (actual != expected) {
            pending.remove(player.uniqueId)
            send(player, "Item contents changed since preview. Nothing was modified.",
                NamedTextColor.RED)
            return
        }
        if (token != operation.transaction.confirmationToken()) {
            send(player, "Incorrect confirmation token. No item changed.", NamedTextColor.RED)
            return
        }
        val proposed = operation.transaction.proposed()
        // Durable original+candidate backup verified by fresh readback BEFORE
        // mutating the hotbar. This method fails closed on IO or lost metadata.
        val backupId = try {
            HistoricalPresentationBackupStore.save(
                directory(), player.uniqueId, expected, proposed
            )
        } catch (failure: Exception) {
            InfinityExpansion2.instance.logger.log(
                Level.SEVERE, "Cannot durably back up historical IE2 item; repair refused", failure)
            send(player, "Backup failed; nothing modified. Inspect console.", NamedTextColor.RED)
            return
        }
        val repaired = operation.transaction.confirm(token, actual)
        pending.remove(player.uniqueId)
        if (repaired == null || repaired != proposed) {
            send(player, "Live item changed or confirmation was consumed. " +
                "Backup retained as " + backupId + "; item unchanged.", NamedTextColor.RED)
            return
        }
        try {
            player.inventory.setItemInMainHand(repaired)
            if (player.inventory.itemInMainHand != proposed) {
                throw IllegalStateException("Hotbar write did not preserve the previewed item exactly")
            }
        } catch (failure: Exception) {
            // Restore the exact clone from BEFORE the attempted write.
            player.inventory.setItemInMainHand(expected)
            InfinityExpansion2.instance.logger.log(
                Level.SEVERE, "IE2 historical item write failed; original was restored", failure)
            send(player, "The repair failed; original restored. Backup: " + backupId,
                NamedTextColor.RED)
            return
        }
        send(player, "Applied NAME-ONLY English repair, quantity " + repaired.amount +
            " preserved. Durable backup: " + backupId, NamedTextColor.GREEN)
        send(player, "To restore the original while holding this exact item: " +
            "/ie2 doctor presentation rollback " + backupId, NamedTextColor.YELLOW)
    }

    private fun rollback(player: Player, argument: String?) {
        val id = try {
            UUID.fromString(argument)
        } catch (_: Exception) {
            send(player, "Usage: /ie2 doctor presentation rollback BACKUP_UUID",
                NamedTextColor.YELLOW)
            return
        }
        val backup = try {
            HistoricalPresentationBackupStore.load(directory(), player.uniqueId, id)
        } catch (failure: IOException) {
            send(player, "No valid backup for this player and UUID; item unchanged.",
                NamedTextColor.RED)
            return
        }
        if (player.inventory.itemInMainHand != backup.proposed()) {
            send(player, "Hold the EXACT repaired item in your main hand. " +
                "Any changed amount, PDC, metadata or slot blocks rollback.",
                NamedTextColor.RED)
            return
        }
        try {
            player.inventory.setItemInMainHand(backup.original().clone())
            if (player.inventory.itemInMainHand != backup.original()) {
                throw IllegalStateException("Restored item failed exact equality verification")
            }
        } catch (failure: Exception) {
            InfinityExpansion2.instance.logger.log(
                Level.SEVERE, "IE2 historical presentation rollback failed", failure)
            send(player, "Rollback write failed. Keep the backup file and inspect console.",
                NamedTextColor.RED)
            return
        }
        try {
            HistoricalPresentationBackupStore.markRolledBack(directory(), player.uniqueId, id)
            send(player, "Original Chinese item restored exactly; backup retained as .rolledback.",
                NamedTextColor.GREEN)
        } catch (failure: IOException) {
            InfinityExpansion2.instance.logger.log(
                Level.WARNING, "Item restored, but backup could not be marked rolled back", failure)
            send(player, "Original restored, but backup archival failed; inspect console.",
                NamedTextColor.YELLOW)
        }
    }

    fun tab(args: Array<String>): List<String> = when (args.size) {
        1 -> listOf("preview", "confirm", "rollback")
            .filter { it.startsWith(args[0].lowercase(Locale.ENGLISH)) }
        else -> emptyList()
    }

    private fun containsHan(c: Component): Boolean =
        PlainTextComponentSerializer.plainText().serialize(c).codePoints().anyMatch {
            Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
        }

    private fun isRich(c: Component): Boolean =
        c.hoverEvent() != null || c.clickEvent() != null || c.insertion() != null ||
            c.children().any { isRich(it) }
}
