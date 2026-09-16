#!/usr/bin/env python3
"""Guard Mob Simulation random drop amount range support."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

props = (ROOT / "src/main/kotlin/net/guizhanss/infinityexpansion2/api/mobsim/MobDataCardProps.kt").read_text()
setup = (ROOT / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/setup/MobSimulationSetup.kt").read_text()
chamber = (ROOT / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/mobsim/MobSimulationChamber.kt").read_text()
docs = (ROOT / "docs/MOB_SIMULATION_DROPS.md").read_text()

checks = {
    "public MobDataCardProps constructor stays source-compatible": "val drops: List<Pair<ItemStack, Double>>" in props,
    "fixed API-card amounts remain the default range": "item.amount..item.amount" in props,
    "config ranges are attached without changing the constructor": "configureDropAmountRanges" in props and "configureDropAmountRanges(configuredDrops.map { it.amountRange })" in setup,
    "range roll is inclusive": "range.last.toLong() + 1L" in props,
    "drop ranges require positive values": "Drop amount ranges must be positive" in props,
    "drop parser accepts min-max strings": "DROP_AMOUNT_RANGE" in setup and "Regex(\"\"\"(\\d+)\\s*-\\s*(\\d+)\"\"\")" in setup,
    "recipe item parsing remains separate from drop range parsing": "private fun ConfigurationSection?.getAsItem()" in setup,
    "independent drops roll amount only after chance succeeds": "props.drops.forEachIndexed" in chamber and "expandDrop(props.getDrop(index), multiplier)" in chamber,
    "random-one drops also roll configured amounts": "expandDrop(props.getRandomDrop(), multiplier)" in chamber,
    "stacked-card multiplier remains in expandDrop": "item.amount.toLong() * multiplier.toLong()" in chamber,
    "operator documentation includes 1-5 example": 'amount: "1-5"' in docs,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Mob Simulation drop-range invariant failed: " + "; ".join(failed))

print("Mob Simulation random drop amount range invariants verified.")
