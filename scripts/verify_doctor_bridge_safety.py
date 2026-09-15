#!/usr/bin/env python3
"""Static safety contract for InfinityExpansion2 Slimefun Legacy Doctor integration."""

from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
errors: list[str] = []


def read(path: str) -> str:
    file = root / path
    if not file.is_file():
        errors.append(f"missing required file: {path}")
        return ""
    return file.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def reject(condition: bool, message: str) -> None:
    if condition:
        errors.append(message)


provider = read("src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/LegacyMigrationProviderBridge.kt")
addon_doctor = read("src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/LegacyAddonDoctorBridge.kt")
item_service = read("src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/LegacyDoctorItemMigrationService.kt")
block_service = read("src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/LegacyDoctorBlockMigrationService.kt")
block_provider = read("src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/LegacyBlockMigrationProviderBridge.kt")
config = read("src/main/resources/config.yml")

for name, bridge in (("legacy item provider", provider), ("generic addon doctor", addon_doctor)):
    require("!InfinityExpansion2.configService.migrationEnabled.value" in bridge,
            f"{name} must not register when migration.enabled is false")
    require("LegacyDoctorItemMigrationService" in bridge,
            f"{name} must use the item-only Doctor service")
    reject("migrationService.scanLoaded(repair)" in bridge,
           f"{name} must not call the old broad block-mutating scan")
    require("unloaded chunks were not force-loaded" in bridge,
            f"{name} must disclose the loaded-only scope")

require("LegacyBlockMigrationProvider" in block_provider,
        "exact placed-block bridge must target Slimefun Legacy's LegacyBlockMigrationProvider API")
require("Proxy.newProxyInstance" in block_provider,
        "exact block bridge must remain reflective/optional")
require("scanLoadedCandidates" in block_provider and "isCandidateStillValid" in block_provider,
        "exact block bridge must expose candidate scan and immediate revalidation")
require("LegacyDoctorBlockMigrationService" in block_provider,
        "exact block bridge must delegate mutation to the addon-owned block service")

require('MessageDigest.getInstance("SHA-256")' in block_service,
        "exact block state claims must use SHA-256")
require("isChunkLoaded" in block_service,
        "exact block service must reject unloaded candidate locations")
require("snapshotData" in block_service and "snapshotMenu" in block_service,
        "exact block claims must cover persisted KV data and menu contents")
require("restore" in block_service.lower() and "rollback" in block_service.lower(),
        "exact block migration must contain an explicit rollback path")
require("Target menu" in block_service or "target menu" in block_service,
        "exact block migration must reject lossy target-menu restoration")
reject("loadChunk(" in block_service,
       "exact block migration must never force-load chunks")
reject("getChunkAt(" in block_service,
       "exact block migration must never force-load chunks")

require("world.loadedChunks" in item_service,
        "item-only Doctor scanning must remain scoped to already-loaded chunks")
reject("migrateBlock(" in item_service,
       "item-only Doctor service must never mutate placed blocks")
require("auto-migrate-blocks: false" in config and "auto-migrate-items: false" in config,
        "automatic IE1 migration must remain opt-in by default")

if errors:
    print("IE2 Doctor migration safety verification failed:")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("IE2 Doctor migration safety verification passed.")
