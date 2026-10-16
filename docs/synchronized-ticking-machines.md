# Synchronized HopperMachine producer transactions

## Root cause

InfinityExpansion2 shades GuizhanLib 2.5.0. In that exact release,
`TickingMenuBlock` delegates `BlockTicker.isSynchronized()` to `isSync()`,
whose default implementation returns `false`.

The Infinity Dust Extractor inherits IE2's `RandomHopperMachine` and
`HopperMachine`. Its asynchronous process consumes input and pushes a random
dust into the machine output slots. Networks performs its source-protected
inventory transfers on the synchronized Slimefun ticker.

That allows the Dust Extractor to change an output slot between Networks'
source snapshot and source commit. Networks 1.0.56 correctly detects this,
compensates any accepted items, preserves the changed source, and reports:

`The menu input changed during the network deposit`.

## Change

`HopperMachine` now overrides `isSync()` to return `true`. This covers the
Dust Extractor and the same simple hopper-style conversion family, keeping
their short input-consume/output-insert transaction on the synchronized ticker.

The broader `AbstractTickingMachine` and `AbstractTickingActionMachine`
families remain on their existing GuizhanLib scheduling behavior. In
particular, this patch does not move heavier Quarry/Growing-style machines to
the main thread simply to fix the Dust Extractor race.

Networks' existing source-change guard remains the safety boundary for other
asynchronous producers.

No recipe, tick-rate setting, energy amount, item ID, output quantity, storage
format, migration behavior, or transport slot changes are included.

## Validation

The transport-contract verifier requires the synchronized override on
`HopperMachine`. Normal IE2 CI still compiles against Paper 1.21.11, 26.2 and
26.3, builds the Java 21 universal JAR, runs unit tests, and runs all migration,
Doctor, mob-simulation, and cross-plugin transport checks.
