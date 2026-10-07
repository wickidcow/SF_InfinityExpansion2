# Mob Simulation drops

Mob Simulation data cards support both fixed drop amounts and inclusive random amount ranges.

Existing fixed amounts continue to work:

```yaml
drops:
  - item: PORKCHOP
    amount: 4
    chance: 1
```

To roll a random amount each time that drop succeeds, use an inclusive range:

```yaml
drops:
  - item: PORKCHOP
    amount: "1-5"
    chance: 1
```

The example above produces between 1 and 5 porkchops whenever the chance roll succeeds. Both ends of the range are included.

`amount` may be omitted, which keeps the historical default of `1`. Invalid ranges such as `0-5`, `5-1`, negative values, or values outside the Java integer range make that card fail closed instead of silently changing its output.

`drop-mode` remains independent from amount ranges:

- `drop-mode: independent` rolls each configured drop's chance separately, then rolls the amount for each successful drop.
- `drop-mode: random-one` selects one configured drop using the existing weights, then rolls that selected drop's amount.

When stacked Mob Data Cards are enabled, IE2 keeps the existing stacked-card behavior: it rolls one amount for the production event and then applies the card-stack multiplier.

Recipe ingredient `amount` values are unchanged and remain fixed numeric amounts; random ranges apply only to entries under `drops`.

## Equipment damage

Equipment drops can optionally set a fixed damage value or an inclusive damage range:

```yaml
drops:
  - item: IRON_AXE
    chance: 0.05
    damage: "1-250"
```

`damage` means durability already used: `0` is undamaged. It does not mean remaining durability. The example retains the behavior of SmartSpawner 1.6.6's field named `durability`, which sets Bukkit item damage directly.

Each successful production event rolls the configured damage on a fresh clone of the drop. Amount rolls, card-stack multiplication, and existing metadata are preserved. Cards supplied through the addon API and drops without `damage` keep their existing item metadata. The guide displays the configured damage range.

The item must be damageable, and the range must be nonnegative, ordered, and no higher than its maximum damage (including an explicitly configured item maximum). Invalid values cause that card to remain unregistered rather than silently producing different equipment. Damage ranges apply to drops only; crafting ingredients still use their normal fixed items.
