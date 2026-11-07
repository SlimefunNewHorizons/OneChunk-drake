# OneChunk Nether & End Dimensions (`DimensionEntryListener`)

OneChunk supports per-island 16x16 mining columns in Nether (`world.nether.islands: true`)
and The End (`world.end.islands: true`), with a shared `(0,0)` origin column fallback.

`world.onechunk.safe-dimension-entry: true` (enabled by default; set to `false` only as an
emergency bypass) registers `DimensionEntryListener` at `HIGHEST` priority for teleports,
portal events, and dimension respawns:

1. **Micro-border healing:** Any legacy persisted `16x16` world border (`size < 1000`) on
   `onechunk_world_nether` or `onechunk_world_the_end` is automatically reset on startup and
   before arrival checks so islands across the grid (e.g. `Z=800`) never die to `WORLD_BORDER`.
2. **Per-island Nether/End columns & return portals:** When `nether.islands` / `end.islands`
   is enabled, arriving at an island's Nether or End coordinates generates (or adopts) a full
   16x16 mining column from `minHeight` (`BEDROCK` floor) up to `Y=64`, seeded with dimension
   ores and rotating dimension biomes (`OneChunkDimensionBiome`), plus a compact obsidian
   return portal pad immediately west of the regenerating 16x16 footprint (`ox-4..ox-1`).
   Arrivals above the Nether roof (`Y >= 120`, e.g. `Y=310`) are clamped to safe ground (`Y=65`).
3. **Overworld return routing:** Entering a portal in `onechunk_world_nether` or
   `onechunk_world_the_end` routes directly back to the player's Overworld island spawn,
   overriding `Multiverse-NetherPortals` `8x` coordinate scaling.
4. **Origin `(0,0)` fallback & repair:** `ChunkGeneratorWorld` populates a full `BEDROCK` to
   `Y=64` column at `(0,0)` and `ensureOriginPlatform` heals worlds whose `(0,0)` region was
   previously saved empty when Multiverse loaded them without a generator.

## Validation

Build with the resource limiter, JDK 25 and these focused regressions:

```sh
build_seguro.sh 25 "$PWD" mvn -B clean package -Djava.version=25 \
  -Dtest=DimensionEntryListenerTest,OneChunkDepthTest,OneChunkGeneratorTest,OneChunkRelocationTest
```

`src/test/server/DimensionEntrySmoke.java` is an optional probe for a **fresh,
disposable** Paper/Purpur 26.2 server containing only BentoBox and this addon.
Compile it against Paper API and its dependencies; package the class with a
`plugin.yml` naming `DimensionEntrySmoke` as both name and main, declaring
`depend: [BentoBox]`, `api-version: '1.21'`, and a version. Add
`paperweight-mappings-namespace: mojang` to its manifest. Start that isolated
server with `-Donechunk.disposable.smoke=true`, bound to localhost.

The probe checks the real generated platforms, listener registration, border
containment and both handler paths. It uses a synthetic Player proxy; it does
not establish actual Java/Bedrock movement, inventories or portal round trips.
It prints `ONECHUNK_DIMENSION_SMOKE PASS` or `FAIL`, saves and shuts down the
test server. **Never install the probe on an existing server.**

Measured on 2026-11-05: the resource-limited JDK 25 build passed 31 focused
tests without failures, errors or skipped tests. The disposable Purpur
26.2-138 server with BentoBox 3.22.4 loaded the addon and passed all 10 probe
checks. Both real generated dimension platforms were present and the server
saved and stopped cleanly. These results do not certify client portal travel.

The probe schedules shutdown two ticks after `save-all flush`. Requesting both
in one callback can trigger Purpur's shutdown watchdog branch, which checks
`!isRunning && !hasStopped` against a one-second-old tick even with a 60-second
normal timeout. That branch was confirmed in the actual runtime bytecode after
an additional successful probe emitted a shutdown dump. The watchdog remains enabled.
The final repeat with the delayed shutdown passed all 10 checks, saved all six
worlds and exited with status 0 without an ERROR or watchdog dump (2026-11-05,
12:24 CLT, UTC-4). This repeat used the same game addon jar.

## Rollout and rollback

Install only through the normal backup/SHA-256 deployment workflow. Enable the
switch after testing; leave it off for an untested installation. Existing empty
or unloaded platforms cause a cancelled entry rather than a world repair.
Repairing an existing world is a separate operation requiring its own backup.
Restore the previous config or set the switch to `false` and reload the addon
configuration through its supported path; use a safe restart if required.
The previous jar is sufficient to revert the implementation. No data migration
is involved. Do not advertise this behavior as active until installed and enabled.
