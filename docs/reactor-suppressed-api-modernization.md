# Reactor suppressed-API modernization

Baseline: PR #49 `f9ba102a6a2283eade0fda205071d60b1692b1bd`.

## Scope

Remove the blanket file-level deprecation suppressions from InfinityReactor and InfinitySingularityReactor. Replace their direct BlockStorage inventory lookups with StoredBlockLookup.getInventory, retaining the same original-record lookup and conditional single payload load. Errors still propagate before fuel/progress work. A missing menu still produces no energy. No readiness filtering, new retry/caching policy, reset, migration or storage write is introduced by the lookup.

Replace six ChatColor.GRAY numeric strings with Adventure components serialized at the existing legacy-string localization boundary. ReactorText still uses MachineLore's unchanged locale-sensitive formatter; expected output is byte-for-byte equivalent to the previous gray prefix plus formatted number. There is no change to item identity, saved data, capacities, recipes, rates, fuel checks, timing, GUI slots, or dependency versions.

Existing Config overloads remain explicitly deprecated and binary-compatible. Removing their file suppressions deliberately exposes two previously hidden Config type diagnostics: the visible accepted count becomes seven, not zero or five. These are existing retained signatures, not reintroduced normal-path calls. Other suppressed owned source and bundled GuizhanLib are not claimed modernized. AbstractReactor progress access still goes through unchanged GuizhanLib helpers; this batch does not alter their persistence semantics.

## Validation requirements

- Preserve the 58 prior generation cases and add five failed-menu-lookup cases across historical/modern dispatch paths. Verify no duplicate lookup and no fuel/progress/energy mutation after lookup failure.
- 24 parity cases execute actual legacy and modern menu lookups on identical missing, loaded, unloaded, failing and record-replacement fixtures, including loads which populate a menu without flipping the loaded flag.
- Six locale cases each compare ten boundary values and 128 deterministic random integers with the old Bukkit formatting output. Restore all default Locale categories after each case.
- Exact-source guard reverses only approved edits and verifies both complete baseline Git blobs. Earlier generation-body, ticker, identity/transport and storage guards remain active.
- Preserve all existing public/protected API descriptors and resource bytes; inspect the built JAR separately from source tests.
- Both normal and independently pinned candidate dependency configurations must pass. Candidate API lanes remain 1.21.11 / 26.1.2 / 26.2 / 26.3; Java 21 remains the distributable bytecode floor.

The suppression inventory is lexical evidence, not a compiler or bug count. Unit and source checks do not certify native runtime, long-running fuel cycles, connected-player/cargo stress, restart durability or Folia. No release or production deployment is performed by this audit branch.
