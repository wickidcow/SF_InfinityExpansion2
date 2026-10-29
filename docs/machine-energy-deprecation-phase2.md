# Individual-machine energy API migration

This second batch is based on PR #45 head `5a6642fc7b6d44558610054e42a4d1e8a5ef3a76`, including its concurrent descriptor/permission regression fix. It does not edit the parent branch or master.

## Narrow scope

Nine compiler-confirmed deprecated calls are replaced: two Advanced Anvil calls, two Powered Bedrock calls, two Mob Simulation Chamber gameplay calls and three HUD energy reads. Gameplay uses the core's long read/debit overloads. The HUD keeps the historical signed-int clamp via `LegacyHudEnergy`; it never writes or repairs stored balances. Costs, integer capacities, recipes, output probabilities, XP, card modes and persistent keys/types are unchanged. No new production warning suppression.

Powered Bedrock's existing Config callback remains intact. The audited core's deprecated `getCharge(Location, Config)` ignores Config, resolves the location's storage container, requests loading when needed and returns zero while unavailable. The location-based long getter follows that same storage lookup/load boundary. With the registered positive 10,000-J cost, widening does not change the power comparison. The 7-of-8 cadence, transform-before-debit, continuous powered consumption and depleted-block reversion are retained.

## Required checks

- Existing normal and exact-candidate core compile/test contexts, Paper 1.21.11 / 26.2 / 26.3 and universal Java 21 packaging.
- 30 new unit cases: 9 executing the original Powered Bedrock anonymous ticker, 16 executing real Anvil craft/Chamber tick entry points with mocked services and IO, and 5 HUD parity/read-failure tests (including 256 deterministic random balances).
- Real compiled method-reference checks for all nine sites, preventing a mistaken integer-overload selection even if future warnings change.
- Actual XML must show no failed/errored/skipped required cases. Retain existing permission metadata, source-safety and packaging checks.
- Compare deprecations with the same API/core inputs. Expected delta: nine energy diagnostics removed, not a claim of zero total warnings.

## Limits

The tests do not launch Paper, simulate real players or certify Folia region safety. Chamber cases deliberately exercise non-output ticks; random loot/XP distribution and real cargo remain separate integration concerns. Anvil push failure is checked only to preserve debit ordering, not to claim transactional inventory rollback. Old Config/BlockStorage/ticker/generator APIs and already-existing third-party or file-level suppressions are not erased. No version bump, publication, deployment or saved-data migration is included.
