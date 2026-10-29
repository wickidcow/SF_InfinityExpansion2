# Generator callback modernization

Baseline: IE2 PR #46 `de70d55311ef15a8baca9696b973ee027ee73ad3`.
Core validation: `f5656f43d220de1fac59e1492eb1f5248af129ab` and the unchanged normal workflow's `a538adddaf58e2f219f383eb2be9bed27da7bafc`. Their EnergyNetProvider source has the same blob `e0a65708dace2ed67341ad12a418d3c4e57aa7f6`.

## Changes and compatibility

EnergyGenerator, InfinityReactor and InfinitySingularityReactor implement both modern `getGeneratedOutput(Location, ASlimefunDataContainer)` and `getGeneratedOutputLong(Location, ASlimefunDataContainer)`. They run one private copy of the original generation body. Modern generation no longer allocates BlockDataConfigWrapper or dispatches through the Config method. The historical public Config descriptor is retained, marked deprecated and forwards to the same body; older concrete block overloads remain callable through the core's existing bridges. No superclass/interface or existing method is removed.

Non-block storage containers still fail with the original unsupported-generation error. This does not enable universal-storage or Folia support. Existing location-based storage resolution, progress parsing, fuel identity, completed-cycle timing and exception behavior stay unchanged. No new data-load/readiness policy is introduced.

EnergyGenerator's no-op per-block ticker handles modern and concrete block data directly; the historical Config no-op remains. Only uniqueTick advances its existing counter. Generation still belongs to the energy-provider callback, not the per-block ticker.

No settings, capacities, generation rates, IDs, recipes, fuel durations, item metadata, permissions, saved formats, migration flags, resources or runtime dependencies change. Existing reactor file-wide deprecation annotations are not expanded or treated as proof that all hidden deprecated usage is removed. Config compatibility descriptors remain intentionally; this is a routing migration, not a removal of support for old callers.

## Evidence required

- Exact-source parity guard compares all three old generation bodies with the new helper bodies; also preserves generator click output, reactor fuel/presentation suffixes, GeneratorType and AbstractReactor.
- 58 new JUnit cases exercise all five legacy/modern entry paths, single progress advancement, final-cycle behavior, missing menus/fuel, void refill pauses, generation exceptions, capacity/multiplier parity, unsupported data and unique-tick cadence. Wrapper-construction and invocation checks prove modern callbacks bypass Config.
- Tests use real production method bodies/core interface defaults with mocked storage/services; no native server, real fuel registration, energy-network routing or Folia region certificate is claimed. Successful fresh fuel consumption is unchanged source, not a new live-world test.
- Normal build plus independently pinned candidate core; candidate Paper compilation expanded to 1.21.11, 26.1, 26.2 and 26.3. Java 21 distributable and test-dependency exclusions retained.
- Inspect exact-head XML, compiler diagnostics, packaged descriptors/resources and class signatures before promotion. Tests/compilation alone do not authorize a release.

This branch does not publish, replace a release asset, migrate storage, or deploy to a server. Remaining storage/callback work is intentionally separate.
