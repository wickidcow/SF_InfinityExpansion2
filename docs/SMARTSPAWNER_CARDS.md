# Mob Data Cards for SmartSpawner migration

The modern Mob Simulation defaults include 44 additional cards based on the supplied
`spawners_settings_backup_1.6.6.yml`. Their loot items and base experience values use
that configuration, including its custom drops. Each new card has its own Mob Data
Infuser recipe, an armor icon, and an independent roll for every configured drop.

These additions fill the gaps between the 87 mob types in that reference file and
the previously bundled cards. The reference file describes configured loot tables;
it does not establish which mobs have placed SmartSpawner blocks in a world. Run a
fresh Slimefun Doctor SmartSpawner plan after installing the updated addon to see
the actual conversion coverage.

## Added cards

The identifiers below are the keys in `mob-simulation-modern.yml`. A card's guide
item ID is `IE_MOB_DATA_CARD_` followed by its uppercase key, for example
`IE_MOB_DATA_CARD_ELDER_GUARDIAN`.

| Group | New card keys |
| --- | --- |
| Animals and companions (15) | `allay`, `bat`, `camel`, `cat`, `donkey`, `fox`, `horse`, `llama`, `mooshroom`, `mule`, `ocelot`, `panda`, `parrot`, `polar_bear`, `wolf` |
| Aquatic mobs (9) | `axolotl`, `dolphin`, `elder_guardian`, `pufferfish`, `salmon`, `tadpole`, `tropical_fish`, `turtle`, `zombie_nautilus` |
| Golems and other riding mobs (6) | `copper_golem`, `happy_ghast`, `skeleton_horse`, `snow_golem`, `strider`, `zombie_horse` |
| Traders and raiders (7) | `piglin`, `piglin_brute`, `pillager`, `ravager`, `trader_llama`, `vindicator`, `wandering_trader` |
| Other hostile mobs (7) | `camel_husk`, `cave_spider`, `endermite`, `silverfish`, `vex`, `zoglin`, `zombie_villager` |

The native Vex card uses `IE_MOB_DATA_CARD_VEX`. DynaTech's separately named card,
`IE_MOB_DATA_CARD_DYNATECH_VEX`, keeps its existing identity and integration.

Stray and Evoker cards were already present before these additions. If a current
Doctor plan shows either as an empty chamber, check that the updated IE2 addon
loaded successfully and that the corresponding card remains enabled. These new
defaults do not replace existing Stray, Evoker, or other card settings.

## Drop chance and amount conversion

SmartSpawner expresses each loot chance as a percentage and permits amount ranges
that include zero. IE2's drop chances use fractions, and its amount ranges contain
positive item counts. A zero-inclusive range therefore needs both values adjusted
to preserve the distribution for one simulated mob.

For a source chance of `c` percent and an inclusive range `0-N`:

```text
IE2 amount = 1-N
IE2 chance = (c / 100) * N / (N + 1)
```

The source's zero result becomes part of the chance of producing nothing. Every
positive quantity retains its original probability. For a positive source range
such as `2-3`, the range is unchanged and the chance is simply divided by 100.

For example, the source Dolphin's cod drop is `0-1` at `50%`. Half the successful
source rolls select zero cod, so its overall chance of one cod is `25%`. The IE2
definition uses `amount: '1-1'` and `chance: 0.25`. A Dolphin prismarine-shard drop
of `0-2` at `7.5%` becomes `1-2` at `0.05`: one shard and two shards each still
occur with probability `2.5%`.

All 44 cards explicitly use `drop-mode: independent`; their loot chances are not
relative weights. Multiple loot entries may succeed in the same production cycle.
The existing global `mob-simulation.legacy-output` option is still honored. If it
is enabled, the chamber uses the legacy single weighted drop behavior, so the
independent per-mob distribution described here does not apply.

This conversion preserves the per-mob loot distribution, not the former
SmartSpawner's production rate. SmartSpawner's mob counts, stack multipliers,
timers and storage behavior differ from Mob Simulation Chambers.

## Equipment damage and other metadata

SmartSpawner 1.6.6 calls its equipment field `durability`, but
`LootItem.createItemStack()` applies that randomly selected value directly to
Bukkit's `Damageable.setDamage()`. These cards preserve that meaning with the IE2
drop key `damage`. It is damage already sustained, not durability remaining.

| Card | Equipment | Inclusive damage range | Drop chance |
| --- | --- | --- | --- |
| Piglin | Crossbow | `1-326` | 5% |
| Piglin | Golden sword | `1-32` | 8.5% |
| Piglin Brute | Golden axe | `1-32` | 8.5% |
| Pillager | Crossbow | `1-326` | 5% |
| Vindicator | Iron axe | `1-250` | 5% |

Damage is rolled when the chamber produces the equipment. Its shared drop template
is not changed. The upper endpoints from the reference file are preserved,
including equipment that is almost or entirely worn out.

The original source's optional `potion_type` corresponds to IE2's existing
`potion` field. None of these 44 missing mob definitions contains a potion drop;
the already bundled skeleton-variant cards retain their existing potion metadata.
The source's head textures are menu artwork, not loot metadata. Card icons follow
the established armor tiers instead.

## Recipes, icons and existing settings

Each new recipe uses the Mob Data Infuser and the following pattern:

```text
ABA
CXC
ABA
```

`X` is the empty Mob Data Card. The `A`, `B` and `C` items and amounts differ by
mob and are shown in the Slimefun guide. Amounts are per occupied slot: there are
four `A` slots, two `B` slots and two `C` slots. Recipes use survival-obtainable
materials and do not require spawn eggs. The costs follow the existing card
recipes, with higher investment for rare drops and powerful mobs.

Iron chestplate icons identify the passive and lower-threat cards. Diamond
chestplates identify hostile or tougher cards; the Elder Guardian uses a netherite
chestplate. These are guide icons and do not add equipment drops. Card energy
values follow the existing tiers: 75-300 for passive or utility cards, 300-600 for
most hostile cards, and 1800 for the Elder Guardian and Ravager. These costs have
not been measured against a particular server economy.

Existing historical and modern card sections are preserved. On startup, IE2
merges only entirely missing modern sections from its bundled defaults. An
existing section, including one with `enabled: false`, is not replaced. Enabled
custom definitions in `mob-simulation.yml` continue to load before the modern
defaults. Existing Sulfur Cube and other modern card behavior stays unchanged.

This addition does not change chamber intervals, output stack limits, card
stacking, experience multipliers or chamber power policy. The listed experience
is base XP before the existing chamber multiplier. Card energy continues to be
used by the Infuser and by the existing optional chamber card-energy setting;
adding cards does not turn that option on.

Slimefun Doctor still produces one chamber per SmartSpawner block. It does not
automatically turn a source SmartSpawner stack into a matching stack of cards.
The new card definitions do not move loot or XP already stored in SmartSpawner.

## Reference and verification

`src/test/resources/mobsim/smartspawner-new-cards-reference.yml` records the raw
experience and loot values for exactly these 44 additions. It excludes head
textures and cards that already existed. Tests can compare each positive output
probability and expected quantity against that fixture, including zero-inclusive
ranges and equipment damage. This fixture is test data and is not a replacement
for either plugin's server configuration.

The source semantics were checked against SmartSpawner's tagged implementation:

- [SmartSpawner 1.6.6 LootItem](https://github.com/OpenVdra/SmartSpawner/blob/1.6.6/core/src/main/java/github/nighter/smartspawner/spawner/lootgen/loot/LootItem.java)
- [SmartSpawner 1.6.6 SpawnerLootGenerator](https://github.com/OpenVdra/SmartSpawner/blob/1.6.6/core/src/main/java/github/nighter/smartspawner/spawner/lootgen/SpawnerLootGenerator.java)
