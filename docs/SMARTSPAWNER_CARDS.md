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
| Additional undead and cave mobs (7) | `camel_husk`, `cave_spider`, `endermite`, `silverfish`, `vex`, `zoglin`, `zombie_villager` |

The native Vex card uses `IE_MOB_DATA_CARD_VEX`. DynaTech's separately named card,
`IE_MOB_DATA_CARD_DYNATECH_VEX`, keeps its existing identity and integration.

Stray and Evoker cards were already present before these additions. If a current
Doctor plan shows either as an empty chamber, check that the updated IE2 addon
loaded successfully and that the corresponding card remains enabled. These new
defaults do not replace existing Stray, Evoker, or other card settings.

## Slimefun guide categories

Open Mob Simulation in the Slimefun guide to choose **All Mobs**, **Passive Mobs**,
**Neutral Mobs**, **Hostile Mobs**, or **Boss Mobs**. The default order is alphabetical
by the displayed mob name. You can instead follow the order of the card sections
in your configuration files. All Mobs combines every type in the selected order;
it never groups cards by type. The empty card, Infuser, chambers, and other machine
entries remain available on the main Mob Simulation page.

### Configure sorting and category visibility

In `config.yml`, locate the existing `mob-simulation:` block and add or update only
the following `guide:` subsection. Keep the two-space indentation shown: `guide`
is a sibling of `output-interval`, `allow-stacked-card`, `charge-card-energy`,
`exp-multiplier`, and `legacy-output`. Preserve your current values for all those
existing settings.

```yaml
  guide:
    sort-order: alphabetical
    categories:
      all: true
      passive: true
      neutral: true
      hostile: true
      boss: true
```

The full sorting key is `mob-simulation.guide.sort-order`:

| Value | Guide order |
| --- | --- |
| `alphabetical` | Default. Sort all enabled cards by their displayed mob names, ignoring letter case and color/format codes for comparison. Equal names use normalized card IDs as a stable tie-breaker. The displayed names and colors remain unchanged. |
| `config` | Follow top-level card order in `mob-simulation.yml`, then non-overridden entries in `mob-simulation-modern.yml`. The alias `yaml` selects the same order. |

To restore your configuration order, change only `sort-order: alphabetical` to
`sort-order: config`, then restart the server. Every key in `mob-simulation.yml`
takes precedence over the same key in the modern file, including historical
entries with `enabled: false`. A disabled historical definition therefore keeps
the modern definition disabled too; it does not fall through to the bundled card.
Only registered, enabled cards appear in the resulting guide sequence.

In `config` mode, addon cards without a configured position are appended after
the configured cards, sorted by normalized card ID. Their order is independent
of when the addon registers them. Each type category filters the same combined
sequence and preserves its relative order. For example, the Passive list keeps
the Passive cards in the order they appear in All Mobs.

The guide reconstructs configuration order from the card sections, so switching
from alphabetical to `config` restores your YAML order even after the guide was
previously sorted alphabetically. It does not rely on the current guide list's
order. Sorting changes the presentation only; card names, colors, textures, IDs,
recipes, and stacking behavior remain unchanged.

A `false` category value hides only that selector. All Mobs continues to include
all registered, enabled cards even when their type selector is hidden. Disabling
all five selectors leaves the machines and empty cards visible. To disable an
individual card itself, use its existing `enabled: false` setting in the card
configuration. Apply sorting, category, and card configuration changes with a
server restart.

### Default mob categories

The bundled definitions contain **88 native cards**: 38 Passive, 16 Neutral,
30 Hostile, and 4 Boss. All Mobs lists all 88 when they are available and enabled.
Disabled cards and cards unavailable on the server's Minecraft version remain
absent, so the counts describe the shipped definitions rather than every server's
loaded roster.

| Guide category | Default card material | Bundled card keys |
| --- | --- | --- |
| Passive (38) | Iron chestplate | `allay`, `armadillo`, `axolotl`, `bat`, `camel`, `camel_husk`, `cat`, `chicken`, `cod`, `copper_golem`, `cow`, `donkey`, `fox`, `frog`, `glow_squid`, `happy_ghast`, `horse`, `mooshroom`, `mule`, `ocelot`, `parrot`, `pig`, `pufferfish`, `rabbit`, `salmon`, `sheep`, `skeleton_horse`, `sniffer`, `snow_golem`, `squid`, `strider`, `sulfur_cube`, `tadpole`, `tropical_fish`, `turtle`, `villager`, `wandering_trader`, `zombie_horse` |
| Neutral (16) | Copper chestplate | `bee`, `cave_spider`, `dolphin`, `enderman`, `goat`, `iron_golem`, `llama`, `nautilus`, `panda`, `piglin`, `polar_bear`, `spider`, `trader_llama`, `wolf`, `zombie_nautilus`, `zombified_piglin` |
| Hostile (30) | Diamond chestplate | `blaze`, `bogged`, `breeze`, `creaking`, `creeper`, `drowned`, `endermite`, `evoker`, `ghast`, `guardian`, `hoglin`, `husk`, `magma_cube`, `parched`, `phantom`, `piglin_brute`, `pillager`, `ravager`, `shulker`, `silverfish`, `skeleton`, `slime`, `stray`, `vex`, `vindicator`, `witch`, `wither_skeleton`, `zoglin`, `zombie`, `zombie_villager` |
| Boss (4) | Netherite chestplate | `elder_guardian`, `ender_dragon`, `warden`, `wither` |

Categories use the mobs' behavior toward players. Neutral includes retaliating
and conditionally aggressive mobs such as Bee, Iron Golem, Enderman, Spider, and
Piglin. Fox, Axolotl, Snow Golem, and Pufferfish remain Passive. Boss is a practical
guide tier including Ender Dragon, Wither, Warden, and Elder Guardian.

Nautilus and Zombie Nautilus are Neutral; the zombie mount is hostile only while
ridden by a hostile mob, as documented in the [Java 1.21.11 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11).
Camel Husk and Zombie Horse are Passive mounts, even when their riders are
hostile; see Mojang's [Camel Husk introduction](https://www.minecraft.net/en-us/article/more-mobs-from-mounts-of-mayhem)
and [Mounts of Mayhem overview](https://www.minecraft.net/en-us/article/unveiling-mounts-of-mayhem).
Sulfur Cube is also Passive, as described in the [official Chaos Cubed announcement](https://www.nintendo.com/us/whatsnew/chaos-cubed-drop-is-now-available-for-minecraft-on-nintendo-switch/).

DynaTech's separately registered `dynatech_vex` and fallback `dynatech_phantom`
cards appear under Hostile and All Mobs. Alphabetical mode sorts them by their
displayed names, Vex and Phantom; `config` mode places unconfigured addon cards
after configured cards in normalized card-ID order. These integrations retain
their own IDs and add to the native counts. Unknown addon or custom card IDs
default to Hostile.

### Change an individual card's category

Add the optional field below inside that card's existing complete section in
`mob-simulation.yml` or `mob-simulation-modern.yml`:

```yaml
guide-group: passive
```

Accepted values are `passive`, `neutral`, `hostile`, and `boss`. The aliases
`friendly` -> `passive`, `aggressive` -> `hostile`, and `bosses` -> `boss` are also
accepted. Values ignore surrounding spaces and letter case. Omitting the field
uses the built-in classification, including for older customized sections that
predate the field. Ordinary chestplate icons follow the selected category; custom
artwork, names and colors are preserved. Restart the server after changing a category.

### Card materials, custom appearance, and existing items

All ordinary Mob Data Card chestplates follow the same category as the guide:

| Category | Material |
| --- | --- |
| Passive | Iron chestplate |
| Neutral | Copper chestplate |
| Hostile | Diamond chestplate |
| Boss | Netherite chestplate |

This applies to guide icons, newly infused cards and cards registered by other addons,
including older definitions that explicitly selected a different ordinary chestplate.
A configured `guide-group` controls both category placement and the material. Public
addon registration signatures and card IDs remain unchanged.

Intentional custom models, item-model components, armor trims, dyed leather, heads,
spawn eggs and nonblank named carpets keep their artwork. Configured names and colors
are preserved. No card configuration is rewritten to apply the category style.

Existing cards refresh automatically with `mob-simulation.refresh-card-appearance: true`
(the default, also when the setting is missing). The event listener waits until the next
tick after joining, opening/closing/clicking/dragging an inventory, picking up items, changing the
held slot or swapping hands. It then reads the current player slots and cursor; it never
writes back an inventory snapshot captured before the transaction. The ender chest is
included on join/open. A currently open single vanilla container may also be refreshed
when it is region-owned and has no Slimefun block state. Virtual guide menus, other
machine inventories and double chests are excluded from that traversal. Mob Simulation
Chambers refresh their own card input during normal processing.

The update preserves the original Slimefun ID (including the guide's per-mob variant),
`mob_data_id`, stack amount, effective stack capacity and all persistent-data keys/types.
Only the ordinary icon and standard/blank display-name or first mob-label formatting are
updated. Player-renamed cards, fixed item names, interactive names/labels, extra rich-text lore, enchantments,
damage and other metadata are retained. Current cards are a no-op. The existing
modern-item refresh on join also uses this narrow card path rather than rebuilding a
filled card from the generic empty-label template.

A blank/formatting-only configured `name` still receives the humanized mob-name fallback.
When paired with an ordinary carpet texture, it receives the category chestplate. A saved
blank-label carpet card can likewise refresh to its current registered chestplate template.
Named carpet artwork and explicit custom models are preserved.

Set `mob-simulation.refresh-card-appearance: false` to disable automatic saved-card
refresh; newly created cards still use the category style. Restart to load edited card
configuration or the updated plugin. Once loaded, encountered existing cards update
without another restart. Closed containers, offline inventories and unloaded chunks are
not scanned; retrieving a card makes it eligible. This feature does not enable IE1
migration, change simulation rates/recipes/drops/energy/XP, or reconstruct stored contents.
The entity/region ownership used by the new listener is not a claim that every IE2 system
has been validated on Folia.

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
The source's head textures are menu artwork, not loot metadata. New bundled card
icons use the chestplate categories described above; explicit custom artwork
remains unchanged.

## Recipes and existing settings

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

The guide categories and card materials do not rebalance recipe or energy costs.
Card energy values keep the existing tiers: 75-300 for many animal or utility
cards, 300-600 for most hostile cards, and 1800 for the Elder Guardian and Ravager.
These costs have not been measured against a particular server economy.

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
