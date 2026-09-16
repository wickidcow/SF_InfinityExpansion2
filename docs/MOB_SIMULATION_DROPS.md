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
