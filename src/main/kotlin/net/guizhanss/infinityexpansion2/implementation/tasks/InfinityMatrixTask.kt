package net.guizhanss.infinityexpansion2.implementation.tasks

import net.guizhanss.guizhanlib.kt.slimefun.extensions.isSlimefunItem
import net.guizhanss.infinityexpansion2.InfinityExpansion2
import net.guizhanss.infinityexpansion2.core.scheduling.IEScheduler
import net.guizhanss.infinityexpansion2.implementation.items.tools.InfinityMatrix
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InfinityMatrixTask : Runnable {

    init {
        IEScheduler.repeat(20L * 20L, this)
    }

    override fun run() {
        for (uuid in ACTIVE_PLAYERS.toList()) {
            val player = Bukkit.getPlayer(uuid)
            if (player == null) {
                ACTIVE_PLAYERS.remove(uuid)
                continue
            }

            IEScheduler.runFor(
                player,
                Runnable { checkPlayer(player) },
                Runnable { ACTIVE_PLAYERS.remove(uuid) }
            )
        }
    }

    private fun checkPlayer(p: Player) {
        val uuid = p.uniqueId
        if (!ACTIVE_PLAYERS.contains(uuid)) return

        // if active players are in creative or spectator mode, no need to track them
        if (p.gameMode == GameMode.CREATIVE || p.gameMode == GameMode.SPECTATOR) {
            ACTIVE_PLAYERS.remove(uuid)
            return
        }

        // check if player's inv still have matrix
        val found = p.inventory.contents.any { it.isSlimefunItem<InfinityMatrix>() }

        // not found, disable flying
        if (!found) {
            ACTIVE_PLAYERS.remove(uuid)
            InfinityExpansion2.localization.sendMessage(p, "infinity-matrix.flight-disabled")
            p.allowFlight = false
            p.isFlying = false
        }
    }

    companion object {

        val ACTIVE_PLAYERS: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    }
}
