# InfinityExpansion2 maintenance contract

Target Minecraft 1.21.11 and newer, prioritizing Paper/Purpur and retaining appropriate Leaf/Folia validation. Saved items, storage contents and machine state can be older than the runtime floor. Preserve IDs, research, recipes, quantities, rates, persistent keys/types, stored inventories, ownership, transport order and existing migration policy.

Do not mechanically replace BlockStorage or remove a historical reader to eliminate warnings. Keep migrations opt-in and retain fresh fingerprint/confirmation/provider guards. Missing addons, unreadable records and failed metadata copies must not become permission to erase data or pretend conversion succeeded. The existing explicit legacy-to-IE2 mapping is distinct from reconstructing all player items from current templates. Do not enable or broaden it incidentally.

Build with the checked-in Gradle wrapper and an exact Slimefun API published locally. The core used for candidate tests must be recorded. Keep the MockBukkit Paper test API separate from the production compile matrix; test-only dependencies must not enter the plugin JAR. Current canonical output is `build/libs/SF_InfinityExpansion2.0.12.jar`; synchronize version and CI names only during a coordinated, validated version bump.

Run the full build and all five existing migration/Doctor/mob-simulation/transport source checks. Keep English-facing diagnostics and docs. Preserve concurrent branch changes, use coherent regression-tested batches, and report actual compiler/runtime evidence separately from lexical source matches. A compile is not a live world upgrade or cross-fork rollback certificate. Do not merge or publish automatically from an audit branch.
