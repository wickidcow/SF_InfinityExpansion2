# Preserve legacy item metadata before accepting conversion

## Scope

This batch is based on `1e0bc7f6bdf8370cdf502ff2513c88b8a05d0e43` and changes the existing private legacy-item PDC-copy boundary only. Item mappings, target template selection, target-wins collisions, quantities, recipes, storage units, serialized formats, Doctor provider/fingerprint rules, feature defaults and migration opt-in policy are unchanged. Production dependencies are unchanged; only test dependencies and test execution are added.

The existing explicit legacy-to-IE2 conversion is not an instruction to replace every player item. It remains governed by its existing supported mapping and owner controls. This change makes that already-authorized path refuse a failed metadata copy instead of silently accepting a result missing old custom data.

## Defect and correction

The old helper rediscovered `copyTo` through reflection and swallowed copy errors after logging. It also ignored a false `ItemStack#setItemMeta` result. A replacement could therefore continue without the original persistent custom values. The new implementation directly calls the native `PersistentDataContainer.copyTo(target, false)` API available at the supported baseline and checks the metadata application result. Failure propagates before the candidate replacement is accepted; the source item is unchanged.

The `false` overwrite flag is retained, preserving established target-ID and typed-value collision behavior. Unknown custom keys and nested containers continue to copy when absent on the target. No key/type coercion, serializer rewrite or missing-addon guess is added.

## Exact-core validation

[Run 36781312385](https://github.com/wickidcow/SF_InfinityExpansion2/actions/runs/36781312385) completed the real Gradle build against the pinned Legacy test merge `d600e077552baab2086a056d04b1cb0a6a9051a5` (PR head `788b89e1`) and transport checks against Networks candidate `cda26048b792900dd3dd16c1f6ca0075ed8de07b`.

The old implementation was restored for a negative control. Its one targeted JUnit test failed because metadata rejection did not throw, proving the silent-success defect rather than substituting a compilation error. The corrected implementation passed all eight actual metadata tests with zero failures, errors or skips. Downloaded XML, evidence digest and all three exact source hashes were independently verified.

Coverage includes exact fractional FLOAT bits, long counters, owner strings and primitive arrays; original target IDs/types winning collisions; nested unknown-addon data; repeated-copy idempotence; a no-meta source; rejected metadata application; a native copy exception; and source/target independence. Tests exercise the actual private Kotlin migration helper on real MockBukkit ItemStack/PDC implementations, not a second copy algorithm.

All five existing source guards passed: migration, Doctor bridge, mob drop ranges, mob input filtering and transport contracts. The initial test run could not compile because Paper was missing from the test classpath; this was corrected with a test-only API matching MockBukkit. No failed assertion was disabled. The production compile-API override remains separate.

Evidence artifact `11128590626`, SHA-256 `4aa1d1938cffec69f924a5e9c92e4434a01e13d78d4f3daed2d9b37aa32b7460`; exact-core candidate artifact `11128380771`, SHA-256 `96425f6e7bb44bfa46fd4d18a59470c3ef3655d83a7d922252d1212d22833257`.

## Remaining work and limits

The real build still emitted 66 Kotlin warning lines, including ChatColor and historical storage/energy bridges. This batch is not a zero-deprecation claim and does not suppress those warnings. Presentation API cleanup and data-sensitive bridge auditing remain distinct follow-ups.

The tests are a focused metadata boundary check, not a complete old-world conversion, live storage throughput test or certification of every legacy addon payload. Placed-machine migration acknowledgement/rollback paths require their own tests; this patch does not claim to fix those separate paths. The normal Paper 1.21.11/26.2/26.3 PR compile/build matrix must independently validate the promoted source. No merge, stable release or production migration is performed here.

Only the tested Kotlin file, test file, test build configuration and these maintenance docs are promoted. Temporary validation workflows and compressed review parts are excluded. Version 2.0.10 remains an unmerged development candidate until a coordinated release bump and bundle validation.
