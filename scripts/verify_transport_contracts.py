#!/usr/bin/env python3

import argparse
from pathlib import Path


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"Missing required transport-contract source: {path}")
    return path.read_text(encoding="utf-8")


def require(text: str, needles: list[str], label: str) -> int:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        formatted = "\n  - ".join(missing)
        raise SystemExit(f"{label} transport contract changed; missing:\n  - {formatted}")
    print(f"OK: {label}")
    return len(needles)


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify IE2 automation contracts against Slimefun Legacy and Networks.")
    parser.add_argument("--slimefun", required=True, type=Path, help="Checkout path for wickidcow/Slimefun-Legacy")
    parser.add_argument("--networks", required=True, type=Path, help="Checkout path for wickidcow/SF_NetworksExp")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    checks = 0

    storage = read(root / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageUnit.kt")
    checks += require(storage, [
        "override fun getInputSlots(menu: DirtyChestMenu, item: ItemStack): IntArray",
        "!item.isBlacklisted() &&",
        "cache.isEmpty() || (cache.matches(item) && isSimilar(item, menu.getItemInSlot(outputSlots[0])))",
    ], "IE2 StorageUnit empty-Cargo seeding")

    growing = read(root / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/machines/GrowingMachine.kt")
    checks += require(growing, [
        "InvUtils.fitAll(menu.toInventory(), generated, *outputSlots)",
        "menu.setStatus { GuiItems.NO_ROOM }",
        "generated.forEach { menu.pushItem(it, *outputSlots) }",
    ], "IE2 grower output backpressure")

    for machine in ("Quarry", "GeoQuarry"):
        source = read(root / f"src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/machines/{machine}.kt")
        checks += require(source, [
            "menu.fits(output, *outputSlots)",
            "menu.setStatus { GuiItems.NO_ROOM }",
            "menu.pushItem(output, *outputSlots)",
        ], f"IE2 {machine} output backpressure")

    mob_sim = read(root / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/mobsim/MobSimulationChamber.kt")
    checks += require(mob_sim, [
        "InvUtils.fitAll(menu.toInventory(), drops.toTypedArray(), *outputSlots)",
        "menu.setStatus { GuiItems.NO_ROOM }",
    ], "IE2 Mob Simulation output backpressure")

    infinity_reactor = read(root / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/generators/InfinityReactor.kt")
    checks += require(infinity_reactor, [
        "override fun getInputSlots(menu: DirtyChestMenu, item: ItemStack): IntArray",
        "if (item.isVoidIngot()) intArrayOf(VOID_INPUT)",
        "else if (item.isInfinityIngot()) intArrayOf(INFINITY_INPUT)",
    ], "IE2 Infinity Reactor item-aware routing")

    singularity_reactor = read(root / "src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/generators/InfinitySingularityReactor.kt")
    checks += require(singularity_reactor, [
        "override fun getInputSlots(menu: DirtyChestMenu, item: ItemStack): IntArray",
        "return if (item.isInfinitySingularity()) intArrayOf(INPUT)",
    ], "IE2 Infinity Singularity Reactor item-aware routing")

    cargo = read(args.slimefun / "src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/cargo/CargoUtils.java")
    checks += require(cargo, [
        "getSlotsAccessedByItemTransport(menu, ItemTransportFlow.INSERT, wrapper)",
        "getSlotsAccessedByItemTransport(menu, ItemTransportFlow.WITHDRAW, null)",
    ], "Slimefun Legacy Cargo preset routing")

    pusher = read(args.networks / "src/main/java/io/github/sefiraat/networks/slimefun/network/pusher/AbstractNetworkPusher.java")
    checks += require(pusher, [
        "BlockMenuUtil.getSafeTransportSlots(targetMenu, ItemTransportFlow.INSERT, template)",
    ], "Networks Expansion pusher routing")

    grabber = read(args.networks / "src/main/java/io/github/sefiraat/networks/slimefun/network/NetworkGrabber.java")
    checks += require(grabber, [
        "BlockMenuUtil.getSafeTransportSlots(targetMenu, ItemTransportFlow.WITHDRAW)",
    ], "Networks Expansion grabber routing")

    integration = read(args.networks / "src/main/java/io/github/sefiraat/networks/integrations/infinityexpansion2/InfinityExpansion2Integration.java")
    checks += require(integration, [
        "getInputSlots(@NotNull SlimefunItem storageUnit)",
        "getOutputSlots(@NotNull SlimefunItem storageUnit)",
    ], "Networks Expansion IE2 storage adapter")

    barrel = read(args.networks / "src/main/java/io/github/sefiraat/networks/network/barrel/InfinityExpansion2Barrel.java")
    checks += require(barrel, [
        "integration.getInputSlots(storageUnit)",
        "integration.getOutputSlots(storageUnit)",
    ], "Networks Expansion IE2 barrel slot routing")

    print(f"Transport contract verification passed ({checks} assertions).")


if __name__ == "__main__":
    main()
