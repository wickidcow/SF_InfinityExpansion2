## v2.0.13 - Mob Data Card guide categories and ordering

- Add Slimefun guide selectors for All Mobs, Passive Mobs, Neutral Mobs, Hostile Mobs, and Boss Mobs. All Mobs combines all registered, enabled cards without grouping them by type; each type filters the same sequence.
- Add `mob-simulation.guide.sort-order`: `alphabetical` is the default and compares displayed names without changing names or colors; `config` restores YAML section order, with `yaml` accepted as an alias.
- Reconstruct configuration order from `mob-simulation.yml` first, followed by non-overridden `mob-simulation-modern.yml` entries, so changing back from alphabetical restores the configured sequence. Historical entries retain precedence even when disabled. In config mode, unconfigured addon cards follow in normalized card-ID order, independent of registration timing.
- Classify all 88 native cards as 38 Passive, 16 Neutral, 30 Hostile, and 4 Boss, with known DynaTech cards included under Hostile. The practical Boss tier contains Ender Dragon, Wither, Warden, and Elder Guardian.
- Use iron, copper, diamond, and netherite chestplates for bundled Passive, Neutral, Hostile, and Boss defaults respectively, and when a card has no `texture` field. Always preserve explicitly configured textures, names, and colors, including custom chestplates, spawn eggs, and player heads.
- Add independent `mob-simulation.guide.categories` toggles. Hiding a type selector leaves its cards in All Mobs; hiding every selector leaves machines and empty cards visible.
- Extend per-card `guide-group` values to `passive`, `neutral`, `hostile`, and `boss`, preserving `friendly`, `aggressive`, and `bosses` aliases. Configuration changes take effect after a restart.
- Keep all card IDs, recipes, drop metadata, energy, XP, enabled settings, and stacking behavior intact. Existing physical cards retain their persistent identities and appearance; no automatic inventory or saved-card rewrite is performed.

## v2.0.12 - SmartSpawner Mob Data Card coverage

- Add 44 native Mob Data Cards for the previously unsupported mob types in the supplied SmartSpawner configuration, including Dolphin, Zoglin, Vindicator, Zombie Horse, Tropical Fish, Elder Guardian, Wandering Trader, and Salmon.
- Include the previously merged Stray, Evoker, and other modern-card additions in the published JAR. The shipped roster now covers all 87 source mob types plus Sulfur Cube.
- Preserve the supplied new mobs' loot items and base XP, translating SmartSpawner percentages and zero-inclusive amount ranges into equivalent per-mob independent drop probabilities.
- Support optional fixed or inclusive random equipment damage on drops, preserving SmartSpawner 1.6.6 worn-weapon behavior. Keep the MobDataCardProps public constructor and existing addon/card output behavior unchanged.
- Add survival-obtainable infuser recipes and the existing armor difficulty icons. Preserve all previous card definitions and administrator overrides; merge only entirely absent modern card sections on startup.
- Organize guide data cards as Friendly, Passive, then Aggressive, alphabetically within each group. Include existing cards and DynaTech integrations, preserve utility positions and the live group list, and support optional per-card `guide-group` overrides.
- Add runtime regression tests for card coverage, material resolution, recipe ambiguity, all 182 new loot distributions, equipment damage, metadata preservation, and invalid-input rejection.

## v2.0.11 - Storage withdrawal and existing-item safety

- Deducts the actual number of items accepted by a player's inventory during Storage Unit withdrawals, including partial insertions into nearly full inventories.
- Keeps unaccepted items in storage and preserves the stored template's metadata and existing fill order.
- Adds real inventory regression tests covering partial/full transfers, stack limits, metadata, and repeated withdrawals.
- Includes the already-merged legacy metadata preservation and Matrix owner-lore fixes: failed metadata copies stop conversion, and older Matrix items retain their existing rich lore.
- Preserves storage keys, item IDs, recipes, production rates, and the opt-in legacy migration policy.

## v2.0.10 - Rare Geo Quarry discoveries

- Added automatic ultra-rare discovery rolls covering the registered GEO-Miner resource pool.
- Standard Geo Quarry defaults to a 0.05% automatic discovery roll per production cycle; Advanced Geo Quarry defaults to 0.10%.
- Automatic discoveries output exactly one eligible GEO resource and preserve its world/biome availability.
- Added `quarry.geo-miner-discoveries.include-external-addon-resources` so server owners can opt external-addon GEO resources into the rare discovery pool.
- External-addon GEO resources are discovery-only and never enter the normal weighted quarry pool, preventing addon supply values from unexpectedly flooding quarry output.
- Retained `rare-geo-drops` as a precise per-item override layer; `IE_ENDER_ESSENCE` remains 0.05% by default.
- Kept the existing optional DracFun Ender Draconium discovery path separate and compatible.

## v2.0.6 - Cargo and output backpressure hardening

- Fixed Slimefun Legacy Cargo seeding of completely empty IE2 Storage Units while preserving blacklist and typed-storage checks.
- Made Tree Grower, Flower Grower, and Virtual Farm production transactional when output slots are full; blocked production now preserves generated items and power.
- Added equivalent backpressure protection to Quarry and Geo Quarry so generated resources cannot be lost when automation backs up.
- Added transport-contract CI coverage against Slimefun Legacy Cargo and Networks Expansion to catch future slot-routing and integration regressions.

## v2.0.5 - Modern Mob Simulation and upstream hardening

- Added modern/default Mob Simulation cards for Goat, Frog, Sniffer, Armadillo, Breeze, Warden, Creaking, Shulker, Phantom, Drowned, Hoglin, Zombified Piglin, and Rabbit.
- Added `mob-simulation-modern.yml` so existing servers receive the new defaults without overwriting or re-serializing customized `mob-simulation.yml` files.
- Historical/custom `mob-simulation.yml` definitions take priority when the same card id is present.
- Added per-card `drop-mode: random-one` support for exclusive output pools.
- Goat simulation can produce all eight functional horn instruments: Ponder, Sing, Seek, Feel, Admire, Call, Yearn, and Dream.
- Frog simulation can produce Ochre, Verdant, or Pearlescent Froglight; Sniffer simulation can produce Torchflower Seeds or Pitcher Pods.
- Added Goat Horn instrument metadata parsing so simulated horns retain their real playable instrument variant.
- Hardened malformed external Mob Data Cards against negative energy values before energy arithmetic.
- Backported upstream overflow-safe capacity calculations for generators and ticking machines without replacing the Legacy Mob Simulation compatibility path.

## Legacy runtime hotfix 6 - Slimefun Doctor migration bridge

- Registered the existing IE1 -> IE2 migration engine with Slimefun Legacy's addon-doctor API.
- `/sf doctor addons scan` now reports recognized IE1 machine/block records and IE1 item stacks in the loaded server scope.
- `/sf doctor addons repair confirm` migrates those records through the same engine used by `/ie2 doctor migrate`.
- Kept the bridge reflective so IE2 can still load on supported Slimefun forks that do not expose the Legacy diagnostics API.
- Migration remains chunk-driven and does not force-load unloaded worlds or chunks.

## Legacy runtime hotfix 5 - cross-addon Slimefun ID ownership

- Fixed IE1 migration aliases pre-claiming legitimate Slimefun IDs from other addons during startup.
- Startup now installs only explicit historical InfinityExpansion v1 aliases; generic `IE_` prefix aliases wait until Slimefun addon registration has finalized.
- Added canonical-owner protection so the migration scanner will not rewrite an item/block ID that is currently owned by another registered addon.
- Prevents known collisions such as ExoticGarden `ENDER_ESSENCE` versus IE2 `IE_ENDER_ESSENCE` and ExtraTools `COBBLESTONE_GENERATOR` versus IE2 `IE_COBBLESTONE_GENERATOR`.
- Slimefun's duplicate-ID protection remains intact; this fix does not weaken core conflict detection.

# Fork changelog

## Legacy compatibility foundation

- Added IE1 persisted block-ID aliasing and permanent IE1 -> IE2 record migration.
- Added migration of IE1 items across players, containers, Slimefun menus and loaded entities.
- Added renamed/tiered ID translations plus dynamic old MobSim card and quarry oscillator mappings.
- Added IE1 filled-storage PDC conversion and capacity-equivalent IE2 Storage Unit tiers.
- Added `/ie2 doctor status|scan|migrate|refresh`.
- Added current IE2 item/armor refresh preserving durability, enchantments, trims and non-conflicting PDC.
- Preserved the Legacy Mob Simulation power fix (base chamber power by default), and hardened output transaction/stacked-card behavior.
- Set Paper 26.2 as the primary compile/run target with Java 25 build tooling and Java 21 addon bytecode.
- Disabled runtime upstream JAR replacement.
- Added CI, release and reviewable upstream-sync GitHub Actions workflows.
