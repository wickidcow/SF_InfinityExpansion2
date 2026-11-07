package net.guizhanss.infinityexpansion2.core.commands.subcommands

import net.guizhanss.guizhanlib.minecraft.commands.AbstractCommand
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun
import net.guizhanss.infinityexpansion2.core.commands.AbstractSubCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.Locale

class DoctorCommand(parent: AbstractCommand) : AbstractSubCommand(
    parent, "doctor", "<status|scan|migrate|refresh|compare>"
) {
    override fun onExecute(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission()) {
            sender.sendMessage(colored("You do not have permission to use IE2 Doctor.", NamedTextColor.RED))
            return
        }

        val service = InfinityExpansion2.migrationService
        when (args.firstOrNull()?.lowercase(Locale.ENGLISH) ?: "status") {
            "status" -> {
                val aliases = service.aliasesInstalled
                sender.sendMessage(colored("InfinityExpansion2 Doctor", NamedTextColor.GOLD))
                sender.sendMessage(
                    colored("Slimefun runtime: ", NamedTextColor.GRAY)
                        .append(colored(service.compatibility.runtimeDescription(), NamedTextColor.WHITE))
                )
                sender.sendMessage(
                    colored("Migration enabled: ", NamedTextColor.GRAY)
                        .append(status(InfinityExpansion2.configService.migrationEnabled.value))
                )
                sender.sendMessage(
                    colored("Automatic block migration: ", NamedTextColor.GRAY)
                        .append(status(InfinityExpansion2.configService.migrationAutoBlocks.value))
                )
                sender.sendMessage(
                    colored("Automatic item migration: ", NamedTextColor.GRAY)
                        .append(status(InfinityExpansion2.configService.migrationAutoItems.value))
                )
                sender.sendMessage(
                    colored("Refresh current IE2 gear on join: ", NamedTextColor.GRAY)
                        .append(status(InfinityExpansion2.configService.migrationRefreshModernItems.value))
                )
                sender.sendMessage(
                    colored("Legacy aliases: ", NamedTextColor.GRAY)
                        .append(colored(
                            "${aliases.installed} installed, ${aliases.skippedExisting} already owned, " +
                                "${aliases.totalResolved} resolved, ${aliases.failed} failed",
                            NamedTextColor.WHITE
                        ))
                )
            }

            "scan" -> {
                val stats = service.scanLoaded(false)
                sender.sendMessage(colored(
                    "IE2 Doctor scan complete (loaded Slimefun blocks, world inventories/entities, and online players).",
                    NamedTextColor.GOLD
                ))
                sender.sendMessage(
                    colored("${stats.legacyBlocksFound}", NamedTextColor.YELLOW)
                        .append(colored(" IE1 block records need migration.", NamedTextColor.GRAY))
                )
                sender.sendMessage(
                    colored("${stats.legacyItemsFound}", NamedTextColor.YELLOW)
                        .append(colored(" IE1 inventory items need migration.", NamedTextColor.GRAY))
                )
                sender.sendMessage(colored(
                    "Unloaded chunks are checked automatically when they load.",
                    NamedTextColor.GRAY
                ))
            }

            "migrate" -> {
                val stats = service.scanLoaded(true)
                sender.sendMessage(colored("IE2 migration pass complete.", NamedTextColor.GREEN))
                sender.sendMessage(
                    colored("Blocks: ", NamedTextColor.GRAY)
                        .append(colored("${stats.blocksMigrated}/${stats.legacyBlocksFound}", NamedTextColor.WHITE))
                        .append(colored("; Items: ", NamedTextColor.GRAY))
                        .append(colored("${stats.itemsMigrated}/${stats.legacyItemsFound}", NamedTextColor.WHITE))
                        .append(colored("; Failures: ", NamedTextColor.GRAY))
                        .append(colored("${stats.blockFailures + stats.itemFailures}", NamedTextColor.WHITE))
                )
                sender.sendMessage(colored(
                    "After the first full migration pass, perform a clean server shutdown/restart and run /ie2 doctor scan again.",
                    NamedTextColor.YELLOW
                ))
            }

            "compare" -> compareHeldItems(sender)

            "refresh" -> {
                if (sender !is Player) {
                    sender.sendMessage(colored(
                        "Run this subcommand as a player to refresh that player's IE2 items/armor.",
                        NamedTextColor.RED
                    ))
                    return
                }
                val stats = service.refreshPlayer(sender)
                sender.sendMessage(colored(
                    "Refreshed ${stats.refreshedItems + stats.itemsMigrated} IE/IE2 items in your inventory and ender chest.",
                    NamedTextColor.GREEN
                ))
            }

            else -> sender.sendMessage(colored(
                "Usage: /ie2 doctor <status|scan|migrate|refresh|compare>",
                NamedTextColor.RED
            ))
        }
    }

    /**
     * Diagnostic for a real machine-produced stack versus one from /sf cheat.
     * Entirely read-only; never replace or normalize player-held items.
     */
    private fun compareHeldItems(sender: CommandSender) {
        val player = sender as? Player ?: run {
            sender.sendMessage(colored("Run this comparison in game as a player.", NamedTextColor.RED))
            return
        }
        val main = player.inventory.itemInMainHand
        val offhand = player.inventory.itemInOffHand
        if (main.type.isAir || offhand.type.isAir) {
            sender.sendMessage(colored(
                "Place /sf cheat item in main hand and machine item in offhand, then run /ie2 doctor compare.",
                NamedTextColor.YELLOW
            ))
            return
        }
        val report = ItemMetadataDifference.inspect(main, offhand)
        fun idOf(item: org.bukkit.inventory.ItemStack): String {
            val meta = item.itemMeta ?: return "(none)"
            return Slimefun.getItemDataService().getItemData(meta).orElse("(none)")
        }
        sender.sendMessage(colored("Item comparison: main hand / offhand (read-only)", NamedTextColor.GOLD))
        sender.sendMessage(colored(
            "Bukkit isSimilar: " + (if (report.isSimilar) "MATCH" else "DIFFERENT"),
            if (report.isSimilar) NamedTextColor.GREEN else NamedTextColor.RED
        ))
        sender.sendMessage(colored("Slimefun IDs: ${idOf(main)} / ${idOf(offhand)}", NamedTextColor.GRAY))
        sender.sendMessage(colored(
            "Material: ${main.type} / ${offhand.type}; amount: ${main.amount} / ${offhand.amount}",
            NamedTextColor.GRAY
        ))
        sender.sendMessage(colored(
            "Adventure name: ${if (report.nameSame) "MATCH" else "DIFFERENT"}; " +
                "ordered lore: ${if (report.loreSame) "MATCH" else "DIFFERENT"}; " +
                "ItemMeta: ${if (report.itemMetaSame) "MATCH" else "DIFFERENT"}; " +
                "PDC: ${if (report.pdcSame) "MATCH" else "DIFFERENT"}",
            NamedTextColor.GRAY
        ))
        fun showFields(label: String, fields: List<String>) {
            sender.sendMessage(colored(
                "$label: " + if (fields.isEmpty()) "(none)"
                else fields.take(12).joinToString() + if (fields.size > 12) " (+${fields.size - 12} more)" else "",
                NamedTextColor.GRAY
            ))
        }
        showFields("PDC keys only in main", report.onlyMainPdcKeys)
        showFields("PDC keys only in offhand", report.onlyOffhandPdcKeys)
        showFields("Different serialized metadata fields", report.changedMetaFields)
        showFields("Different non-metadata stack fields", report.changedStackFields)
        if (!report.isSimilar && report.changedMetaFields.isEmpty() && report.changedStackFields.isEmpty()) {
            sender.sendMessage(colored(
                "Stacking differs without an obvious serialized field: inspect Paper components or a plugin-specific rule.",
                NamedTextColor.YELLOW
            ))
        }
        sender.sendMessage(colored(
            "No items, quantities, PDC values, or storage records were modified.",
            NamedTextColor.GREEN
        ))
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size != 1) return emptyList()
        val current = args[0].lowercase(Locale.ENGLISH)
        return listOf("status", "scan", "migrate", "refresh", "compare").filter { it.startsWith(current) }
    }

    companion object {
        internal fun colored(value: String, color: NamedTextColor): Component = Component.text(value, color)

        internal fun status(value: Boolean): Component =
            colored(if (value) "enabled" else "disabled", if (value) NamedTextColor.GREEN else NamedTextColor.RED)
    }
}
