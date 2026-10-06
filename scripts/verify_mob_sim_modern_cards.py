#!/usr/bin/env python3
"""Guard the modern Mob Simulation card set and typed potion drops."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODERN = ROOT / "src/main/resources/mob-simulation-modern.yml"
SETUP = ROOT / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/setup/MobSimulationSetup.kt"

modern = MODERN.read_text()
setup = SETUP.read_text()

required_cards = (
    "squid",
    "glow_squid",
    "husk",
    "stray",
    "bogged",
    "parched",
    "nautilus",
    "evoker",
)

missing = [card for card in required_cards if f"\n{card}:\n" not in "\n" + modern]
if missing:
    raise SystemExit("Missing recommended modern Mob Data Cards: " + ", ".join(missing))

checks = {
    "Stray keeps slowness-tipped arrows": "potion: SLOWNESS" in modern,
    "Bogged keeps poison-tipped arrows": "potion: POISON" in modern,
    "Parched keeps weakness-tipped arrows": "potion: WEAKNESS" in modern,
    "Evoker can simulate Totems of Undying": "item: TOTEM_OF_UNDYING" in modern,
    "Nautilus retains its rare shell output": "nautilus:" in modern and "chance: 0.05" in modern,
    "Potion-capable items require PotionMeta": "item.itemMeta as? PotionMeta ?: return null" in setup,
    "Potion names are resolved through PotionType": "PotionType.valueOf" in setup,
    "Potion metadata is applied to the output item": "meta.basePotionType = potionType" in setup,
    "Deprecated PotionData API is not used": "PotionData" not in setup,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Modern Mob Simulation invariant failed: " + "; ".join(failed))

print("Modern Mob Simulation cards and potion metadata invariants verified.")
