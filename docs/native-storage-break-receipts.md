# Native storage break receipt acceptance

The storage break repair was tested with the real IE2 production JAR, a real
Slimefun/Networks/Purpur server, native `Player.breakBlock` and `ItemSpawnEvent`,
an actual Mineflayer client placement, and a second independent server JVM.
The disposable fixture lives in `tests/runtime/NativeIE2BreakProbe.java`,
`tests/runtime/native_ie2_break_client.cjs`, and
`scripts/native_ie2_break_probe.py`.

## Verified 2.0.15 inputs and results

The final IE2 acceptance run used these exact source and binary inputs. It ran
entirely in process-local temporary storage; evidence was exported only after
both server JVMs exited normally.

| Input | Source revision | SHA-256 |
| --- | --- | --- |
| GuGu/Legacy core | `1cb32d6e95b305998065b8ef6b936a56f679187d` | `251e9075bb6d1629790d845569fb90994e4ff1ed76408400ee733d4041bb9e3e` |
| Networks 1.0.60 | `b5e48c5c8da4ddab93cabafca44349ee4ad26a2e` | `0588ad1e19281293daee6dd419c51d92e5b9dc7923d707dd27157b14b9238ae3` |
| IE2 2.0.15 | `19e4943a53c44589a6cfcf618150358f7bdf5a1c` | `897bd4de70c9219217ecfb0f8cf68f0147b6c38758a239085db39718dd686eef` |
| Purpur | 1.21.11 build 2568 | `ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34` |

The tested IE2 tree is `c1b954480e048ad7016ce49c36511007de4f4933` in PR #32.
The actual project build passed **158 tests**, with zero failures, errors or
skips. The source verification scripts also passed, including 31 transport
contract assertions against the pinned core and coordinated Networks source.
The build emits Java 21 bytecode; the native server ran on Java 25. This evidence
does not certify a later Networks commit or imply release publication.

The seed JVM was PID **40003** and the independent verification JVM was PID
**40438**. The exported evidence directory is
`ie2-final-b5e48c5c-19e4943a-isolated`. Its `server/inputs.json` records
`result: PASS`, all input hashes, both process results, and the exact stopped
storage files before another JVM could open them. `isolation-receipt.json`
records the process-local execution and successful runner exit.

The Java fixture SHA-256 is
`63f30a6ee4441bd5b03bc7dbed74da0fc2cf1b788593b0a85942f10cc3926410`;
the runner SHA-256 is
`f3c149ce1041aad2e50f396eca7a34eca369136efcdbcd75e1b2b09a05eab369`.
Raw stopped SQLite hashes are
`9fb1a1e21de8eefebdaaff078841b3e0e64d53122f178fd2d11f467efa177c49`
after seed and
`b8a865dc7b88eaf86b18f9fe799b55f430d05b371c042fd31cf7c73907103328`
after verification. Neither stopped capture contained a WAL or SHM file. These
raw captures are retained separately from any copy opened for SQL inspection.

Six cases passed. The original five continue to use native `Player.breakBlock`:

1. A known zero cache plus 11 matching physical output items produces an exact
   shell containing 11 items; all three independent input items also survive.
2. A full 1,024-item cache with Void Excess enabled retains all 64 physical
   overflow items outside the shell and all three input items.
3. An output stack with a different PDC owner remains a separate physical stack.
   Its 11 items are never reassigned to the 23 stored items.
4. Cancellation of the second actual item-spawn event cancels the block break,
   removes both captured entities, retains the exact cache and physical inputs,
   releases ownership after verified rollback, and permits a healthy retry.
5. An actual item-spawn callback invokes the same protected production break
   handler recursively and attempts a cross-direction input receipt. Both are
   refused while the outer break owns the cache; its final ledger stays exact.
6. A forwarding native `BlockMenu` invokes the actual protected handler and
   throws a controlled `NoClassDefFoundError` immediately after the real input
   slot is cleared. The fixture proves that the fault was reached, the break
   was cancelled, both captured entities were removed, and the exact original
   cache and physical input/output stacks were restored. Verified rollback
   releases ownership, and a subsequent healthy native `Player.breakBlock`
   succeeds with the same strict quantity and PDC ledger.

The client then places the actual recovered shell from case 1 through a native
BlockPlaceEvent. The second JVM reloads that placed storage and verifies the
original PDC owner and exactly 11 items across its cache and physical buffers.

### Failure controls and previous checkpoint

On the earlier IE2 `274a1833488fba8dbd8e3bbc44b95e59d49c09b7` build, the
original five native cases passed, but the sixth controlled setter fault escaped
the production break handler. The corrected handler rolls back supported
runtime/linkage failures while still propagating other `Error` categories.
Two actual `StorageInputReceipt` regressions also reproduced rejection of a
legitimate empty-cache reseed, then passed after the post-credit identity check
was restricted to an originally positive balance. Both different-material and
different-PDC reseeding retain exact source/cache quantities.

The earlier five-case native acceptance remains historical evidence for IE2
2.0.14 local `fa4b7adf9539511b13f919b29de1c5bb9222d14b`, whose complete tree
`74680f3ec78546d2cffa1c72e2c8d1618a91da45` matched PR commit `274a1833`.
It used Networks `834df8dd424a6749e5f1723f63cfdabca936831f`, passed 150 project
tests, and completed seed/verify JVMs 14864/15153. Those older results are not
relabelled as validation of 2.0.15.

## Execution boundary recovered from prior work

A workspace run of the corrected build passed every seed ledger and actual
placement but failed the independent reload. Its seed shutdown backup retained
the correct placed block, while the later database exposed initial fixture
records instead. That run remains failed; a subsequent workspace pass was not
treated as an explanation or fix.

The existing [core 4.1.72 storage assessment](https://github.com/wickidcow/Slimefun-Legacy/blob/1cb32d6e95b305998065b8ef6b936a56f679187d/docs/release-candidates/4.1.72.md)
already records a matching historical boundary: a traced workspace run observed
matched staging-directory moves that reintroduced WAL/SHM and a clean marker
after the JVM deleted them. Its controls kept all mutable runtime and trace
files in process-local temporary storage until both JVMs exited, then exported
evidence. The historical actor PID and the cause of a separate older failed run
remain unproven; this does not identify the actor in the current workspace.

The final acceptance above reused that established execution procedure with
the same plugin bytes and unchanged quantity oracles. It introduced no storage
repair, forced flush, arbitrary save delay, restart reseeding, or core source
change. The runner now preserves stopped database/journal bytes and SHA-256
receipts before starting the next JVM. Old workspace diagnostics remain
separate from this isolated two-JVM pass.

## Reproduce

Provide Java 25, Python 3.11+, Node, `curl`, the four verified JARs, and a client
directory containing a locked installation of Mineflayer 4.39.0. The work
directory must not exist; the runner refuses to reuse a server/world. It binds
the server to loopback and accepts only the named disposable client. Keep the
runtime, JVM temporary files and logs outside a synchronized workspace until
the runner exits; then copy the evidence to a staging directory and rename it
into its final location. Preserve failed runs as well as successful ones.

```sh
export CORE_HEAD=1cb32d6e95b305998065b8ef6b936a56f679187d
export NETWORKS_HEAD=b5e48c5c8da4ddab93cabafca44349ee4ad26a2e
export IE2_HEAD=19e4943a53c44589a6cfcf618150358f7bdf5a1c
ie2_runtime=$(mktemp -d /tmp/ie2-break-XXXXXX)
mkdir "$ie2_runtime/java-tmp"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=$ie2_runtime/java-tmp"
python3 scripts/native_ie2_break_probe.py \
  /absolute/core.jar /absolute/networks.jar /absolute/ie2.jar \
  /absolute/purpur-1.21.11-2568.jar "$ie2_runtime/server" /absolute/client \
  > "$ie2_runtime/run.log" 2>&1
```

The runner verifies the pinned Purpur and its pinned Mojang server input before
launch. It emits logs, exact input hashes and a machine-readable manifest. A
network environment requiring an HTTP proxy must supply the corresponding Java
proxy properties for runtime library downloads; the normal environment proxy
variables alone do not configure Java. Certificate verification remains enabled.

## Evidence limits and prior fixture failures

The starting boundary balances are explicit synthetic fixture state. Native
server APIs perform the first five breaks and healthy retries; the client
performs the placement. The reentry attempt and sixth-case setter fault are
deliberate injections into actual production handlers. This is clean-restart evidence, not
forced-crash or Folia region-scheduling proof. The synchronous production receipt
does not claim atomic world/database persistence across a process crash or
recovery from arbitrary external mutation or a failed entity removal.

Earlier runs failed in fixture setup: reflection resolved an absent optional
SlimeHUD companion signature, then attempted public access to a protected break
handler. A later run passed all five break ledgers but attempted placement before
the client received the replacement shell and teleport. The final fixture reads
the exact storage-key field, uses declared access for deliberate reentry and
setter-fault injection, and waits for the actual shell and acknowledged position. No production
assertion or quantity ledger was weakened to obtain the passing run.
