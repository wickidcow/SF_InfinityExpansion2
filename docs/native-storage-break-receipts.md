# Native storage break receipt acceptance

The storage break repair was tested with the real IE2 production JAR, a real
Slimefun/Networks/Purpur server, native `Player.breakBlock` and `ItemSpawnEvent`,
an actual Mineflayer client placement, and a second independent server JVM.
The disposable fixture lives in `tests/runtime/NativeIE2BreakProbe.java`,
`tests/runtime/native_ie2_break_client.cjs`, and
`scripts/native_ie2_break_probe.py`.

## Verified inputs and results

The successful run used the following exact source and binary inputs:

| Input | Source revision | SHA-256 |
| --- | --- | --- |
| GuGu/Legacy core | `1cb32d6e95b305998065b8ef6b936a56f679187d` | `251e9075bb6d1629790d845569fb90994e4ff1ed76408400ee733d4041bb9e3e` |
| Networks | `834df8dd424a6749e5f1723f63cfdabca936831f` | `03db0adc9d6a20074b398e048bc248171f8809e677c37f8fd05b9d884857d829` |
| IE2 | local `fa4b7adf9539511b13f919b29de1c5bb9222d14b` | `ef25cc5022734988b8aef77ebb316764f570300a2e2608bb0239b98abd8d01f4` |
| Purpur | 1.21.11 build 2568 | `ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34` |

The tested IE2 production tree is
`74680f3ec78546d2cffa1c72e2c8d1618a91da45`, also published in PR #32 as
`274a1833488fba8dbd8e3bbc44b95e59d49c09b7`. The local and PR commit IDs differ;
their complete source trees are identical. The real project build passed all
150 tests before this native run. The plugin reports version 2.0.14; no release
publication or version bump is implied.

The successful native seed JVM was PID 14864. The verification JVM was PID
15153. Its generated `inputs.json` records `result: PASS`, all JAR hashes,
both process IDs and results, and the Java fixture source digest
`5f0f55d166306e49aff8b0f687b97b1cac7b6fb5bb8f41add5d5d89dccd0594a`.

Five native break cases passed:

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

The client then places the actual recovered shell from case 1 through a native
BlockPlaceEvent. The second JVM reloads that placed storage and verifies the
original PDC owner and exactly 11 items across its cache and physical buffers.

## Reproduce

Provide Java 21, Python 3.11+, Node, `curl`, the four verified JARs, and a client
directory containing a locked installation of Mineflayer 4.39.0. The work
directory must not exist; the runner refuses to reuse a server/world. It binds
the server to loopback and accepts only the named disposable client.

```sh
export CORE_HEAD=1cb32d6e95b305998065b8ef6b936a56f679187d
export NETWORKS_HEAD=834df8dd424a6749e5f1723f63cfdabca936831f
export IE2_HEAD=fa4b7adf9539511b13f919b29de1c5bb9222d14b
python3 scripts/native_ie2_break_probe.py \
  /absolute/core.jar /absolute/networks.jar /absolute/ie2.jar \
  /absolute/purpur-1.21.11-2568.jar /new/disposable-directory /absolute/client
```

The runner verifies the pinned Purpur and its pinned Mojang server input before
launch. It emits logs, exact input hashes and a machine-readable manifest. A
network environment requiring an HTTP proxy must supply the corresponding Java
proxy properties for runtime library downloads; the normal environment proxy
variables alone do not configure Java. Certificate verification remains enabled.

## Evidence limits and prior fixture failures

The starting boundary balances are explicit synthetic fixture state. Native
server APIs perform the breaks; the client performs the placement. Only the
reentry attempt is an injected nested event. This is clean-restart evidence, not
forced-crash or Folia region-scheduling proof. The synchronous production receipt
does not claim atomic world/database persistence across a process crash or
recovery from arbitrary external mutation or a failed entity removal.

Earlier runs failed in fixture setup: reflection resolved an absent optional
SlimeHUD companion signature, then attempted public access to a protected break
handler. A later run passed all five break ledgers but attempted placement before
the client received the replacement shell and teleport. The final fixture reads
the exact storage-key field, uses declared access only for deliberate reentry
injection, and waits for the actual shell and acknowledged position. No production
assertion or quantity ledger was weakened to obtain the passing run.
