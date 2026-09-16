#!/usr/bin/env python3
"""Guard Mob Simulation Chamber input filtering."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHAMBER = ROOT / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/mobsim/MobSimulationChamber.kt"
chamber = CHAMBER.read_text()

checks = {
    "automated insertion uses item-aware transport filtering": "override fun getInputSlots(menu: DirtyChestMenu, item: ItemStack): IntArray" in chamber,
    "automated insertion only exposes the input slot for registered cards": "if (item.getMobDataCardProps() != null) layout.inputSlots else intArrayOf()" in chamber,
    "manual input slot uses advanced cursor-aware filtering": "ChestMenu.AdvancedMenuClickHandler" in chamber and "cursor.getMobDataCardProps() != null" in chamber,
    "number-key swaps validate the hotbar item": "event.hotbarButton" in chamber and "hotbarItem.getMobDataCardProps() != null" in chamber,
    "offhand swaps validate the offhand item": "ClickType.SWAP_OFFHAND" in chamber and "offhandItem.getMobDataCardProps() != null" in chamber,
    "invalid player-inventory shift-click inserts are rejected": "!action.isShiftClicked || item.isAir() || item.getMobDataCardProps() != null" in chamber,
    "card validation requires a registered Mob Data Card": "isSlimefunItem<MobDataCard>()" in chamber and "IERegistry.mobDataCards[id]" in chamber,
    "runtime processing reuses the same card validator": "val props = input.getMobDataCardProps() ?: return null" in chamber,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Mob Simulation input-filter invariant failed: " + "; ".join(failed))

print("Mob Simulation Chamber input-filter invariants verified.")
