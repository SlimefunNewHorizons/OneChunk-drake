# Shared dimension entry

The built-in generator creates Nether and End terrain only in chunk `(0,0)`.
Their world border is 16 blocks wide. An arrival copied from a distant island
can therefore land outside the border even though its world is correct.

`world.onechunk.safe-dimension-entry: true` enables a listener at `HIGHEST`
priority for both player teleports and portal events. Its default is `false`
to preserve existing behavior. External generators are excluded.

Safe arrivals are preserved. Otherwise the listener checks the interior of the
already loaded origin chunk at the generated platform height. It requires an
occluding, non-magma floor, two air blocks and a destination inside the current
border. It cancels the entry if no safe spot exists. It never loads a chunk,
places blocks, regenerates terrain, widens a border or writes player data.

A Nether portal event supplies a search origin, not the final destination.
After finding a safe spot, the listener cancels that search and requests a
normal asynchronous player teleport, allowing teleport protection listeners to
run again. It does not generate an exit portal on the shared platform.
Reference: [Paper portal event API](https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerPortalEvent.html).

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

## Rollout and rollback

Install only through the normal backup/SHA-256 deployment workflow. Enable the
switch after testing; leave it off for an untested installation. Existing empty
or unloaded platforms cause a cancelled entry rather than a world repair.
Repairing an existing world is a separate operation requiring its own backup.
Restore the previous config or set the switch to `false` and reload the addon
configuration through its supported path; use a safe restart if required.
The previous jar is sufficient to revert the implementation. No data migration
is involved. Do not advertise this behavior as active until installed and enabled.
