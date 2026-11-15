# AlbionMC historical IE2 presentation acceptance (release blocker)

This checklist is for an **offline clone** of AlbionMC with an independently verified full backup. It is not permission to repair production data, bypass a failed preview, or rewrite storage/Cargo/Fluffy inventories. See [DOCTOR_HISTORICAL_PRESENTATION.md](DOCTOR_HISTORICAL_PRESENTATION.md).

## Evidence capture

Record the exact IE2 candidate JAR SHA256 and source commit, Slimefun Legacy and addon versions, Paper build, Java version, server world/database backup checksum, and date. Record all relevant player UUIDs only in private test artifacts (not public CI logs).

Choose at least one **actual pre-fix historical** Chinese IE2 storage item with nested typed storage data, one historical item in Cargo transit, and one item from a Fluffy Barrel. Do not substitute freshly generated /sf cheat items for the historical sample. Capture read-only serialized ItemStack bytes, PDC key/type/value fingerprints, display name, all lore lines, outer amount, nested stored-item identity and quantity, void flag, capacity and owner before any operation. Record every relevant storage/Cargo/Fluffy location and quantity, including pending buffers and player inventories, so totals can be reconciled without double-counting in-transit stacks.

## Offline-clone sequence

1. Start the cloned server with the candidate plugin and the repair config **disabled**. Verify that every old item and stored quantity survives boot, shutdown and restart unchanged. Record snapshots and SHA256s at both boots.
2. Enable `doctor.historical-presentation-repair` **only on the clone**, with authorized operator permissions. Hold a single backed-up historical item in the main hand and run `/ie2 doctor presentation preview`. Capture the preview; assert it makes **no** item or ledger changes.
3. For an eligible item, run `/ie2 doctor presentation confirm TOKEN` once. Require an owner-scoped durable backup created and readable **before** any item replacement. Verify only the specifically permitted localized presentation fields changed; assert identical outer amount, Slimefun ID, typed PDC, nested stored item, ledger amount/capacity, void flag and owner. Reuse of the token must fail.
4. Shut down cleanly and boot again. Compare persisted bytes and all inventory/storage ledgers with the post-confirm snapshot. Then hold the exact repaired item and run `/ie2 doctor presentation rollback BACKUP_UUID`. Verify the exact original item is restored, backup is retired/audited, and replay fails. Restart once more and verify the restored item and totals persist.
5. Test a custom owner lore line, unknown Chinese text, modified nested ledger, changed stack amount, changed owner/PDC, and wrong-player/wrong-slot confirmation. Each must fail closed **without any mutation**.
6. Exercise a Cargo transfer and Fluffy Barrel deposit/withdrawal on the clone, including shutdown/restart with items in transit. Reconcile quantities and identities across **all** participating locations before/after each operation; do not run the presentation command on storage contents, blocks, or offline inventories.

## Release acceptance

For each tracked item ID, compare total physical + stored + in-transit quantities at every checkpoint. Require exact equality; do not claim conservation from a GUI counter alone. Store anonymized item fingerprints, ledger snapshots, restart logs, commands, backup/rollback audit results and SHA256s with the test report. Any unexplained missing/duplicated item, changed typed PDC, invalid owner, missing backup, or failed rollback is a **release blocker**.

Synthetic MockBukkit/native two-boot tests and green CI are necessary but **not** substitutes for these historical AlbionMC samples. Repeat applicable native compatibility tests on Paper 1.21.11, 26.2 and 26.3 with Java 21/25 as specified by the workflow. Do not mark PR ready, merge, tag, publish a JAR, or replace the Legacy addons ZIP until the repository's AGENTS.md gates and real-data evidence are satisfied.
