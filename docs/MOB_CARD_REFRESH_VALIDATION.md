# Mob Data Card category and refresh validation

Validated on October 21, 2026.

## Candidate

- Base: `168815bb99e5701d3680095a9eb9170d83a19740` (InfinityExpansion2 2.0.15).
- Production/test change: `d9af003d5b9fb43e72f38b4d04c80cd773135e0b`.
- Preview build version: `2.0.15-card-refresh`; the default release version remains 2.0.15.
- File: `SF_InfinityExpansion2.0.15-card-refresh.jar`, 1,722,358 bytes.
- SHA-256: `306fbf55d3b328847b85ca1e9b8a6f2e67ca59c10cfb6e5cfbb0528ec490ee13`.
- Exact Slimefun core source: `e7ba40469eae1aa54b4347af19e58341f492edcb`.
- Exact core JAR SHA-256: `e11d024fa3ed4aca3f891d61af61f2542ec62efeeda7a98646941247331da659`.

## Full build and regression suite

The checked-in Gradle 9.3.0 wrapper built the candidate with Java 25, the Paper
1.21.11 API floor, and Java 21 production bytecode. All **183 tests passed**, with
zero failures, errors or skipped tests. The original master baseline passed all
158 tests against the same exact core before this change.

The added regressions cover physical card appearance/metadata and deferred player
inventory events. They verify quantities, effective stack capacity, generic and
per-mob IDs, typed persistent data, player names, fixed item names, interactive
components, extra rich lore, model overrides and repeated no-op refreshes. Event
tests verify current-slot reads, coalescing, cancelled events, cursor return on
close, scheduler retirement, config disable and exclusion of virtual or unsafe
inventories. One listener test uses MockBukkit's native entity-scheduler path.

MockBukkit 4.110 omits modern model fields from its metadata copy constructor.
The model-specific unit fixture supplies only that missing copy behavior and
asserts model-bearing input before exercising the production updater. Native
Paper assertions below independently verify the actual metadata implementation.

All six existing source checks passed:

- `verify_legacy_migration.py`
- `verify_doctor_bridge_safety.py`
- `verify_mob_sim_drop_ranges.py`
- `verify_mob_sim_input_filtering.py`
- `verify_mob_sim_modern_cards.py`
- `verify_transport_contracts.py` (31 assertions, with Networks source
  `f4ba6a5d2de669e3abdead641514155132d665c4` and the core source above)

These source checks supplement the compiled tests; they are not runtime evidence.
The shaded JAR contains only one plugin artifact, Java 21 addon bytecode, and no
JUnit, Mockito, MockBukkit or native probe classes.

## Additional API compilation checks

Separate isolated archives of the production/test commit above passed both
`compileKotlin` and `compileJava` with the checked-in Gradle wrapper and exact core:

| API target | Resolved Paper API | JVM target | Result |
| --- | --- | --- | --- |
| 26.2 | `io.papermc.paper:paper-api:26.2.build.134-stable` | Java 25 (class major 69) | Passed |
| 26.3 | `io.papermc.paper:paper-api:26.3.build.209-stable` | Java 25 (class major 69) | Passed |

These checks establish source compatibility with those API versions. They do not
claim native runtime verification on those versions. No release JAR was rebuilt;
the tested Java 21 preview artifact and SHA-256 above remain unchanged.

## Native Paper verification

The exact candidate JAR ran on **Paper 1.21.11-132-c5eb079** with the exact core
above in a fresh isolated server directory. The process stopped normally and the
runner exited successfully: **535 assertions, zero failures, 22 scenario groups**.

The probe checked **91 registered cards**, including the API and configuration
fixtures, and verified:

- Registered guide icons and crafted outputs use their category material.
- An addon-supplied ordinary iron icon becomes diamond for a hostile card.
- Explicit neutral and boss registrations use copper and netherite; a config
  `guide-group` override controls both guide and output material.
- Generic and per-mob card identities, quantities and typed PDC survive refresh.
- A native blank-label carpet stack containing 64 cards becomes the expected
  chestplate while retaining count, capacity and additional rich lore.
- Item-model and custom-model-data artwork is retained.
- Player titles, first labels, fixed item names and interactive components survive.
- Repeated refreshes are no-ops.
- Modernization of a generic filled card preserves its mob label and custom state.
- Vanilla items, unrelated Slimefun items, missing/unknown mob IDs, mismatched
  variants and actual registered IE1 aliases are not rewritten.

The fixture used the plugin's normal Kotlin library cache, with the exact
stdlib/reflect dependencies preseeded. The candidate JAR and classloader behavior
were not modified for the run. The reproducible probe and runner are retained in
[`scripts/runtime/mob_card_refresh`](../scripts/runtime/mob_card_refresh/README.md).

## Scope

This establishes the compiled behavior, inventory event regression coverage and
native registration/item transformations for the tested inputs. It does not claim
live-player acceptance on AlbionMC, performance at an arbitrary machine count,
or full-addon Folia runtime certification. Existing cards update when encountered;
offline players, closed containers and unloaded chunks are not swept. Installation
of the new plugin still requires a normal server restart.
