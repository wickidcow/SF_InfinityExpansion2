# Native Mob Data Card refresh probe

This helper runs the real InfinityExpansion2 registry and refresh implementation on Paper 1.21.11. It checks category icons in both guide entries and crafted output, addon and config category registration, generic and per-mob identities, native carpet-to-armor conversion, stack capacity, typed PDC, custom names/lore/models, idempotence, item identity guards, and the existing modernization path.

The probe **stops its server JVM when finished**. Use only a new isolated test directory. The runner refuses to populate a nonempty directory, binds the server to loopback with a random port, and copies only immutable server assets from the supplied Paper cache. It never uses another server's worlds, player data, or configuration.

Build the candidate first. Then run:

```sh
python scripts/runtime/mob_card_refresh/run.py \
  --runtime /absolute/path/to/new-card-probe \
  --paper-source /absolute/path/to/cached-paper-1.21.11-runtime \
  --core /absolute/path/to/exact-Slimefun.jar \
  --ie2 /absolute/path/to/candidate-InfinityExpansion2.jar \
  --java-home /absolute/path/to/jdk-25 \
  --kotlin-stdlib /absolute/path/to/kotlin-stdlib-2.3.21.jar \
  --kotlin-reflect /absolute/path/to/kotlin-reflect-2.3.21.jar
```

The Paper source directory must contain `server.jar` and its cached `libraries`, `versions`, and `cache` directories. The exact Kotlin 2.3.21 artifacts are seeded into InfinityExpansion2's normal library cache before startup; the candidate JAR and plugin classloader behavior are unchanged.

The runner preserves input SHA-256 hashes, complete console output and the probe result under the new runtime directory. It exits successfully only after a normal JVM stop and a result reporting zero failures. Runtime files are test evidence, not production plugin assets; keep them out of Git.
