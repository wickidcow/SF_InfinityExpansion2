# Ticker callback modernization

Exact baseline: `08e215fc23997c1b31ff7d7d2ac1ea0c4ee7ec9d` (generator changes integrated into PR #46).

## Scope

PoweredBedrock, StorageUnit and NetherStarReactor anonymous tickers implement `tick(Block, SlimefunItem, SlimefunBlockData)` directly. The core's inherited neutral-container callback already validates non-universal block storage and dispatches to this overload. Neither modern route needs BlockDataConfigWrapper or the deprecated Config callback. Existing Config method descriptors remain declared, explicitly deprecated, and continue to execute their historical behavior. Do not interpret their intentionally visible Config type diagnostics as new normal-path calls.

PoweredBedrock has one private copy of its historical per-block body, reached once from either entry. StorageUnit's existing private tick, inventory receipts, quarantine behavior and all persistence code are unchanged. NetherStarReactor's per-block callbacks remain no-ops; only uniqueTick increments its counter. The original synchronization flags, non-universal type checks, universal-overload no-op, capacities, recipes, fuel durations and wither-effects scheduler remain unchanged. No new Folia support is declared.

## Verification

`scripts/verify_ticker_callback_parity.py` reverses only the approved adapters and checks the complete three source files against their exact historical Git blob IDs. This detects any unrelated changes, including storage transfer/order/recovery code, reactor effects and bedrock timing. The optional compiler-log check permits only the three retained Config descriptor warnings and NetherStarReactor's unchanged BlockStorage references; it does not suppress compiler output.

`TickerCallbackDispatchTest` adds 40 execution cases: the three modern/historical routes are exercised for bedrock cadence/depletion/failure, reactor unique-tick cadence, storage missing/unknown/blocked caches, successful conserved output, full output, uncertain physical output and failed inventory lookup. Four additional checks cover actual core dispatch, wrapper-construction avoidance, unsupported storage, inherited universal behavior and ABI/ownership flags. These execute production ticker bodies and storage receipts with mocked external IO; they do not simulate native server/cargo/player/Folia concurrency.

Retain the existing six migration/Doctor/mob-simulation/transport source checks, generator-body parity, prior energy tests and Java 21/packaging checks. Candidate API lanes remain 1.21.11, 26.1.2, 26.2 and 26.3, using the existing exact core/Networks pins. No runtime dependency or configuration changes are part of this batch.

Build evidence must be inspected before promotion. Remaining deprecated BlockStorage access and existing hidden library deprecations are separate work. No version bump, release, replacement release asset, saved-data migration or production deployment is performed by this branch.
