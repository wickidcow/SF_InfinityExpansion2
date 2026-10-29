# Controller-backed existence and ID lookups

Baseline: PR #48 `ea5d6d283f309b205c065097beb09bbb3d79fd46`.

## Scope

Replace only the BlockStorage existence check in MobDataCardRefreshListener and the stored-ID read in NetherStarReactor. StoredBlockLookup resolves through Slimefun's supported BlockDataController, conditionally loads the original record once if its payload is not loaded, and returns the same existence/ID result. Exceptions propagate; they are not converted to an absent block or permission to alter a container. The original facade implementation is identical at normal core `a538adddaf58e2f219f383eb2be9bed27da7bafc` and candidate core `f5656f43d220de1fac59e1492eb1f5248af129ab` (BlockStorage blob `fa36873e6bba315c7332c8303e9a2e2b287b246a`).

Existence does not require a registered item or readable ID. The ID comes from getSfId(), not generic metadata, material, or a current template. No caching, write/create/remove operation, additional readiness filter, retry, new chunk scan or storage-mode policy is added. Any loading already performed inside the controller or facade's historical conditional load remains possible; this is not advertised as an unloaded-record-free lookup.

The listener's region/chunk/view/holder checks, exception handling and deferred current-slot reads are unchanged. Reactor tick cadence, scheduler, ray trace, case-sensitive WITHER_PROOF substring test and potion parameters are unchanged. No new Folia compatibility is claimed. No setting, recipe, item ID, quantity, capacity, data key, release version or runtime dependency changes.

## Evidence required

- 44 parameterized Java cases run identical states against the actual old facade and new lookup: missing/loaded/unloaded records, unchanged readiness after load, failures at lookup/readiness/load/ID, unregistered/null stored IDs, repeated calls and original-record retention.
- Eight listener cases exercise its production default storage callback with mocked controller IO, including safe ordinary-container refresh, skipping stored containers, failed lookup/load/readiness, queue recovery, foreign regions and virtual inventories.
- Source-scope guard reverses only the two imports/calls to reconstruct exact historical file hashes. The earlier ticker/generator and migration/transport guards remain active.
- Compiler gate allows only the five deliberately retained Config descriptor warnings in the audited visible baseline; it does not add suppressions or claim bundled/suppressed libraries are clean.
- Inspect actual test XML, candidate compiler logs and packaged signatures/resources before promotion. These are unit/source/package checks, not native world/restart/cargo/Folia certification.

Build validation is pending. No version bump, master merge, publication, existing asset replacement or live-server deployment is performed by this branch.
