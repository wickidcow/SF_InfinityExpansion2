# Machine energy deprecation migration: shared consumer classes

Parent: IE2 combined candidate #44 at `146da01adf3d8a861fbe1efbf95c70ca84d0ae26`.
Candidate validation retains core `f505b9f79e8e4598eedf1160b0831dbec758eb78` and Networks `ebdaba23186b0be2328d88726179fe22a1c6f85d`. Normal CI independently retains its prior pinned dependency pair.

## Scope

Replace three deprecated integer charge reads and three integer debit calls in AbstractTickingMachine, AbstractTickingActionMachine and AbstractEnergyCraftingMachine. Use getChargeLong and explicitly convert the existing configured consumption to Long to select the modern removeCharge overload. Do not remove any core compatibility API or change public machine signatures.

The machines retain their existing integer settings (1..1,000,000,000), capacities, tick counters, recipe validation and process-before-debit order. AbstractTickingMachine retains the prior signed-int clamp when presenting out-of-range saved balances; no saved charge is changed by reading it. Action/crafting comparisons against positive integer settings have identical outcomes with either an int-clamped or long balance.

No BlockStorage, Config callback, generator, recipe, inventory, persistent key/type, migration-policy or storage-format changes. Remaining individual-machine energy deprecations and storage/ticker migrations are separate work.

## Regression evidence required

MachineEnergyAccessTest executes real production tick/craft entry points with mocked energy IO and inventories. It checks shortage, exact/full power, maximum settings, cadence, rejected work and exceptions, debit order and overload selection. Crafting tests deliberately stop at the unchanged recipe/notification boundary; they are not a complete crafting integration test. Additional tests exercise the real core long-energy defaults with loaded/unloaded/pending-removal data containers and a balance above the int range.

Require all 18 new tests and the complete existing suite to execute without skips in both normal and candidate-core CI. Compile all existing API lanes and inspect retained Kotlin warnings: the six diagnostics in these three files must disappear; this is not a zero-warning claim for the addon. Verify Java 21 packaged output and exclusion of Mockito, Byte Buddy, Objenesis, JUnit and MockBukkit classes.

No new native gameplay, Folia/Leaf scheduling or historical-world upgrade certification is inferred from unit tests. Keep this independently reviewable and do not replace published artifacts or deploy to a server from the audit branch.
