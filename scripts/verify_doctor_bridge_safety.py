#!/usr/bin/env python3
"""Verify Slimefun Legacy Doctor bridges preserve IE2 migration safety boundaries."""

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
service = read("src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/LegacyMigrationService.kt")
compat = read("src/main/kotlin/net/guizhanss/infinityexpansion2/core/migration/SlimefunCompatibilityBridge.kt")
config = read("src/main/resources/config.yml")

for name, bridge in (("migration provider", provider), ("addon doctor", addon_doctor)):
    require("!InfinityExpansion2.configService.migrationEnabled.value" in bridge,
            f"{name} must not register when migration.enabled is false")
    require("migrationService.scanLoaded(repair)" in bridge,
            f"{name} must delegate to the addon-owned loaded-scope migration service")
    require("unloaded chunks were not force-loaded" in bridge,
            f"{name} must disclose the loaded-only scope")
    reject("unloaded chunks migrate when loaded" in bridge,
           f"{name} must not promise automatic migration when auto migration is disabled")

require("Class.forName(PROVIDER_CLASS" in provider,
        "dedicated migration provider must remain reflective/optional")
require("LegacyIdMapper.resolvedAliases()" in provider,
        "dedicated provider must expose the live resolved mapping authority")
require("publishDiagnosticMappings(registry, aliases)" in compat,
        "IE2 aliases must also publish the same mappings to Slimefun Doctor diagnostics")
require("registerLegacySlimefunItemId" in compat,
        "Slimefun Doctor legacy-id mapping publication is missing")
require("world.loadedChunks.forEach" in service,
        "manual migration must scan already-loaded chunks")
reject("world.getChunkAt(" in service,
       "manual/automatic migration must not force-load chunks through getChunkAt")
require("auto-migrate-blocks: false" in config and "auto-migrate-items: false" in config,
        "automatic IE1 migration must remain opt-in by default")

if errors:
    print("IE2 Doctor bridge safety verification failed:")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("IE2 Doctor bridge safety verification passed.")
