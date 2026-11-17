# Historical IE2 Chinese item presentation recovery

Issue #73 — This is a disabled-by-default, administrator-only, main-hand item repair. It is NOT a world scan, a storage migration, or a repair for Cargo/Fluffy Barrels, offline inventories or missing Slimefun IDs.

## Preconditions

Back up the entire server, playerdata and Slimefun/IE2 storage databases. Test on a copy of the server first. In plugins/InfinityExpansion2/config.yml set the language to en and enable doctor.historical-presentation-repair: true; restart. This option defaults to false.

The player needs the existing infinityexpansion2.command.doctor permission plus operator status or the dedicated infinityexpansion2.doctor.presentation permission. Hold the original IE2 item in the main hand.

## Commands

- /ie2 doctor presentation preview
- /ie2 doctor presentation confirm TOKEN
- /ie2 doctor presentation rollback BACKUP_UUID

Preview does not mutate the item and issues a single-use token expiring after 120 seconds. Confirm requires the same player, hotbar slot, exact item and token. It saves and verifies a durable original-and-proposed pair BEFORE the hotbar item changes. Any failure leaves the item unchanged or attempts immediate restoration from the original clone.

Backups: plugins/InfinityExpansion2/doctor-presentation-backups/PLAYER_UUID/BACKUP_UUID.bin

Rollback works across restart only when the precise repaired item is held in the main hand. It restores the original and archives the backup with a .rolledback extension, preventing replay. Changed amounts, PDC, owner, lore or other metadata refuse rollback.

## What can be safely translated

The historical item must have a registered IE_ Slimefun ID, and its display name must exactly match the bundled zh-CN localized name. The replacement must match the bundled English name.

For ordinary IE2 items, the original lore must equal the registered lore; only the display name is changed.

For filled IE2 Storage Units, a separate typed-ledger verifier can translate the three generated Chinese labels (stored item, amount/capacity and Void Excess) only when all old lines exactly match IE2's own zh-CN generator using the decoded StorageCache contents. The dynamic stored-item name, amount, capacity, void flag, entire nested item, custom model, PDC/owner and outer quantity remain unchanged. Any extra, different or unknown lore line rejects. No guessing or rewriting arbitrary Chinese custom text.

## Outstanding release gates

This is a draft implementation until exact-head Gradle tests, all six migration/Doctor/transport source checks, Paper 1.21.11/26.2/26.3 checks, native real-Paper two-boot persistence tests, and operator-world backups with post-restart conservation all pass.

Native synthetic fixtures do not substitute for a real historical item or a successful Cargo/Fluffy Barrel quantity-conservation test. A refused preview means preserve that item's saved data and investigate a specific compatible reader. Do not bypass guards, change old published JAR assets or enable automatic migrations.
