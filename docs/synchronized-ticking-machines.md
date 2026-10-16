# Synchronized ticking for inventory-facing IE2 machines

## Root cause

InfinityExpansion2 shades GuizhanLib 2.5.0. In that exact GuizhanLib release,
`TickingMenuBlock` delegates `BlockTicker.isSynchronized()` to `isSync()`,
whose default implementation returns `false`.

IE2's `AbstractTickingMachine`, `AbstractTickingActionMachine`, and
`StrainerBase` inherited that default while directly reading and mutating
`BlockMenu` inventories and, for machine bases, energy state.

This allows a producer such as the Infinity Dust Extractor to change an output
slot concurrently with another synchronized inventory consumer. Networks then
correctly detects that its source snapshot changed during a deposit and
compensates the accepted items, producing the warning:

`The menu input changed during the network deposit`.

## Change

All three direct IE2 `TickingMenuBlock` consumers now override `isSync()` to
return `true`.

The Dust Extractor inherits `AbstractTickingMachine`, so its input consume,
random output selection, and output insertion now occur on the synchronized
Slimefun ticker. The same safety boundary applies to the other IE2 ticking
inventory/action machines and the Strainer.

No recipe, tick-rate setting, energy amount, item ID, output quantity, storage
format, migration behavior, or transport slot changes are included.

## Validation

The transport-contract verifier requires the synchronized override on all three
direct bases. The normal IE2 CI still compiles against Paper 1.21.11, 26.2 and
26.3, builds the Java 21 universal JAR, runs unit tests, and runs all migration,
Doctor, mob-simulation, and cross-plugin transport checks.
