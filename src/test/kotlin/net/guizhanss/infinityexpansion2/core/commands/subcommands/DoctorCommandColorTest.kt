package net.guizhanss.infinityexpansion2.core.commands.subcommands

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The Doctor command keeps its historical visible colors without using deprecated Bukkit ChatColor. */
class DoctorCommandColorTest {
    private val legacy = LegacyComponentSerializer.legacySection()

    @Test
    fun enabledAndDisabledStatusesKeepTheirExistingColors() {
        assertEquals("\u00a7aenabled", legacy.serialize(DoctorCommand.status(true)))
        assertEquals("\u00a7cdisabled", legacy.serialize(DoctorCommand.status(false)))
    }

    @Test
    fun labelsAndValuesKeepSeparateGrayAndWhiteColors() {
        val message = DoctorCommand.colored("Blocks: ", NamedTextColor.GRAY)
            .append(DoctorCommand.colored("2/5", NamedTextColor.WHITE))
            .append(DoctorCommand.colored("; Items: ", NamedTextColor.GRAY))
            .append(DoctorCommand.colored("3/4", NamedTextColor.WHITE))

        assertEquals("\u00a77Blocks: \u00a7f2/5\u00a77; Items: \u00a7f3/4", legacy.serialize(message))
    }

    @Test
    fun commandHeadingsKeepTheirOriginalGoldColor() {
        val heading = DoctorCommand.colored("InfinityExpansion2 Doctor", NamedTextColor.GOLD)
        assertEquals("\u00a76InfinityExpansion2 Doctor", legacy.serialize(heading))
    }
}
