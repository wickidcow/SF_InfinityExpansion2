# Read-only item-source stacking comparison (AlbionMC)

This is a diagnostic procedure, **not** an item repair or migration. It does not alter inventories, world drops, machines, or stored quantities. Use an exact-master verified diagnostic build containing `/ie2 doctor compare`; published v2.0.17 does **not** contain this diagnostic. Do not treat a successful fresh-item native test as proof that historical live items are equal.

## Capture the three source pairs

Use the **same Slimefun ID** for each source, and preserve the original items without dropping, re-crafting, or relabeling them. Obtain one item from `/sf cheat` (or separately label `/sf give`), one from actual crafting, and one from the actual machine output. A particular item such as `SMALL_URANIUM` may not have all three source paths; use a different craftable and machine-producible Slimefun ID for the full three-way comparison, and retain the `SMALL_URANIUM` cheat-versus-GEO Quarry reproduction separately.

1. MAIN: cheat; OFFHAND: crafted. Run `/ie2 doctor compare`.
2. MAIN: cheat; OFFHAND: machine output. Run `/ie2 doctor compare`.
3. MAIN: crafted; OFFHAND: machine output. Run `/ie2 doctor compare`.

Capture **all** lines, including Bukkit `isSimilar`, max stack sizes, name/lore, ItemMeta/PDC, changed metadata/component field names, registered Slimefun template comparisons, and registered GEO resource comparisons. The report intentionally does not print private PDC/skull payload values. Record the precise source of each item and whether the pair stacks in vanilla inventory, dropped-world entities, and FluffyMachines barrels. **Never** infer an item mismatch merely from the ordering of keys in a textual NBT dump.

## Interpret safely

- `isSimilar: DIFFERENT` and different name/lore/component fields: identify which source deviates from the registered template and GEO resource; then trace only that creation/transfer boundary. Matching PDC keys alone do not prove all metadata equal.
- `isSimilar: DIFFERENT` with PDC, profile, model, or max-stack differences: preserve functional PDC, ownership, profiles, and model semantics; investigate their actual origin before changing code.
- `isSimilar: MATCH` with an effective max stack size of 1: the limit explains nonstacking. Otherwise, reproduce the actual inventory/entity/transport path and inspect event listeners and stack-limit rules; do not rewrite item metadata speculatively.
- Both held items matching their registered templates does **not** automatically prove they will merge after a plugin event or historical data load.

Do not bulk-rewrite historical player inventories, Fluffy barrels, stored items, or dropped entities. A fix requires an exact-head regression on the responsible production path plus native Paper 1.21.11/26.3 and historical saved-world/restart conservation verification before merging.
