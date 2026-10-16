# ShopChest automated-transfer fix, build 795

This is the historical build-795 verification record. Its target, test results
and artifact checksum describe that build. See the
[stable migration record](paper-26.3-stable-2026-10-11.md) for the maintained
Paper target and subsequent verification.

Version `1.15.4-SNAPSHOT`, build **795**, compiles for Java 25 and Paper
`26.3.build.41-alpha`. The previous build-794 owner-name cache, bounded refresh
sweep and unchanged-text suppression are retained. Installation and the live
restart are left to the operator. No shared or live server files were changed.

## Root cause and scope

The supplied local investigation recorded 10 minutes 27 seconds of **ticks over
80 ms**, with a 10 ms sampling interval, on Paper `26.3-43-d289ae1` / Java 27.
ShopChest accounted for 6,840 ms, approximately 48.1% of sampled server-thread
time **within those selected slow ticks**, not overall CPU usage.

Both `ChestProtectListener.onItemMove` and
`ShopUpdateListener.onInventoryUpdate` resolved inventory holders before asking
the shop registry whether a shop was involved. `DoubleChest`'s holder-side
accessors created block-state snapshots and decoded inventory/item components.
Ordinary hopper transfers therefore incurred shop-identification costs even
when neither endpoint was a shop. Merely cancelling additional transfers would
have left this expensive discovery path intact.

The current source still contained both paths at the start of this change.
The supplied evidence is in the sibling project's
`.scratch/live-tps-diagnosis-2026-09-26/diagnosis.md`,
`capture-analysis.json`, `slow-ticks.sanitized.sparkprofile`, and
`shopchest-bytecode.txt`. No new live profile was captured for this fix.

The regular 30-second stalls in that investigation were attributed separately
to BlueMapAddons' offline-owner name lookup. This fix makes no claim about those
stalls or all TPS drops.

## Implementation

- `ShopUtils.getShopByInventory` uses the authoritative location registry.
  Double chests use each `DoubleChestInventory` side's location; using the
  combined inventory's midpoint could miss a half, especially at negative
  coordinates. A transfer needs at most four coordinate lookups, regardless of
  shop count. There is no cache of ordinary inventories or scan of all shops.
- Single inventories first check location membership. Only a positive match
  calls `getHolder(false)` to distinguish a block inventory from a minecart or
  player inventory occupying the same coordinates. This obtains a wrapper
  without a block-entity snapshot. Neither path reads contents or item data.
- Protection runs at `HIGH`, with `ignoreCancelled = true`, before monitoring
  listeners. Either endpoint being a shop cancels the move, without inspecting
  owner/admin identity or initiator type. Pickup events use the same policy.
  The handler never reads/replaces the event item or restores items itself.
- Automated-transfer and pickup refresh handlers were removed. Manual click,
  drag and close updates still schedule the shop refresh on the server thread,
  now using the same cheap lookup. Trade logic and periodic reconciliation remain.
- Accepted double-chest placement/break events change membership at `MONITOR`,
  after cancellation decisions. Both halves are protected immediately; a split
  removes the detached half immediately. Display creation waits for the next
  server tick, checks current membership and chunk availability, and cannot
  resurrect a removed shop. Uncreated records are also removable. Chunk reload
  can finish creating a replacement whose display task missed an unloaded chunk.
- Reshaping no longer deletes and reinserts the persisted shop. Extension needs
  no database write. Moving the anchor after splitting writes only immutable
  world-name/coordinate snapshots using `UPDATE ... WHERE id = ?`. Pending
  writes for a shop are ordered/coalesced off the server thread. Disconnect clears
  the pending registry so queued workers skip writes that have not started; it
  does not cancel SQL already in progress. A late update cannot recreate a deleted
  row or replace owner, price or product fields. Pending state exists only while
  writes are outstanding.
  A shop awaiting its initial persisted ID cannot be resized yet.

Ownership remains the existing authoritative UUID. No identity resolution,
external request, database operation or forced chunk load occurs in transfer
checks. All inventory, world, player and display access stays on the server thread.

## Official API and implementation verification

Discovery began with the live [Paper documentation index](https://docs.papermc.io/llms.txt).
The official [event guide](https://docs.papermc.io/paper/dev/event-listeners/)
and [scheduler guide](https://docs.papermc.io/paper/dev/scheduler/)
define cancellation/monitoring and server-thread boundaries. The
[InventoryMoveItemEvent contract](https://jd.papermc.io/paper/26.3/org/bukkit/event/inventory/InventoryMoveItemEvent.html)
assigns restoration of a cancelled move to the server.

Supported API references:
[Inventory](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/Inventory.html),
[DoubleChestInventory](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/DoubleChestInventory.html),
[BlockInventoryHolder](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/BlockInventoryHolder.html).
The build-41 API is pinned in both compiler and test classpaths, with a focused
API-presence contract test.

Implementation was read at Paper build 41's commit
`a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa`:

- [CraftInventory](https://github.com/PaperMC/Paper/blob/a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftInventory.java)
  delegates location to its container and supports snapshot-free holders.
- [CraftInventoryDoubleChest](https://github.com/PaperMC/Paper/blob/a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftInventoryDoubleChest.java)
  returns existing inventory sides and computes its combined location as a midpoint.
- [BaseContainerBlockEntity](https://github.com/PaperMC/Paper/blob/a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa/paper-server/patches/sources/net/minecraft/world/level/block/entity/BaseContainerBlockEntity.java.patch)
  obtains location directly from block position and level.
- [CraftBlockEntityState](https://github.com/PaperMC/Paper/blob/a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa/paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlockEntityState.java)
  bypasses `createSnapshot` for the snapshot-free API.

The first three relevant implementation files were also fetched at the captured
build-43 commit `d289ae1` and compared byte-for-byte: all matched build 41.
These internals were inspected as evidence, not introduced into plugin code.

## Regression and runtime verification

The initial real-listener regression failed against the old source: an incoming
restock was allowed, and both listeners invoked snapshot-capable source-holder
resolution during 100 ordinary transfers. After the fix those tests pass.

The full clean JDK `25.0.4.1` build passed **311 tests**, zero failures/errors/skips:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.1.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk-25.0.4.1.jdk/Contents/Home/bin:$PATH \
./gradlew clean build --no-build-cache
```

Focused tests dispatch real events through real listeners and the real registry,
with Paper boundaries mocked. Coverage includes all 28 supported materials,
both directions and initiators, both double halves, negative coordinates,
minecarts, coincident entity/block locations, missing locations, pre-cancellation,
unchanged event items/inventories, manual updates, removal, chunk unload and
container transitions. The 1,000-shop/1,000-transfer case performs exactly 3,000
endpoint location reads and no snapshot-holder or contents reads. It schedules
no tasks. SQL tests verify ordering, non-blocking submission during an in-flight
write, cleanup, preservation of UUID/price fields and no deleted-row resurrection.
All prior identity/hologram, stock, capacity, price and trade-guard tests also pass.

The frozen jar was tested on the **project-local** Paper `26.3-41-a15fed9` server
under Java `27+35-2325`, using a separate temporary test plugin:

```text
TRANSFER_SMOKE_PASS assertions=1691 blockedEngineEvents=149
blockedMinecartEvents=119 transferRefreshes=0 periodicRefreshes=9
materials=28 doubleVariants=10
```

That test uses actual Paper inventories with large lore/PDC components, dispatches
events for all supported materials and all ten double-chest variants, and observes
actual hopper insertion/extraction and hopper-minecart attempts for 120 ticks.
Blocked inventories and event items retain their counts and components; an
ordinary hopper transfers items without loss or metadata changes. Periodic
reconciliation remains active and is counted separately from event-driven work.

The first runtime attempt caught an overly broad test assertion that counted
periodic reconciliation as transfer-driven work. Only the test instrumentation
was corrected; the installable jar was unchanged for the passing rerun.

Startup, ShopChest enable/build/API/runtime diagnostics, economy availability,
all 11 existing shops' audit/displays, clean stop and SQLite integrity passed.
All original shop rows matched the pre-test snapshot. The local server is stopped
and the temporary test jar was removed. No plugin reload was performed.

The reusable test-plugin source is
[`scripts/verification/ShopTransferSmoke.java`](../../scripts/verification/ShopTransferSmoke.java).
It is deliberately outside the production jar. Local ignored evidence is in
`logs/hopper-transfer-795/`, including the frozen artifact manifest, original
local jars, fixture rows, smoke driver and `smoke-transfer-final.log`. Build logs
are `/tmp/shopchest-transfer-clean-build.log` and `/tmp/shopchest-transfer-red.log`.

## Artifact and limits

Installable artifact:

```text
/Users/floris/Projects/Codex/ShopChest/plugin/build/libs/1MB-ShopChest-v1.15.4-SNAPSHOT-795-j25-26.3.jar
SHA-256: 76d57118cf42547289dc089bafd13fc796cdd4b5358b2338fbcc0daae55de634
```

Embedded metadata identifies build 795 and Paper 26.3 build 41 ALPHA. Classfile
inspection confirms Java 25 major version **69**, with no Java 27 bytecode.

Eliminating the measured snapshot call path and extra event refresh work is
demonstrated by tests and source inspection. Lower live allocation/decoding cost
is expected; no live wall-clock, CPU or TPS improvement has been measured for
build 795. Paper build 43 was source-compared, not run locally. Player-operated
buy/sell and interactive container-extension/splitting checks remain manual;
automated tests cover their refresh, membership and persistence boundaries.
Database write failures are logged; this work does not add crash-durable queues
to the plugin's existing asynchronous persistence lifecycle.

The automation policy covers the inventory transfer/pickup event contracts.
Other plugins can still directly change inventories; legitimate changes continue
to be reconciled by the periodic sweep. This artifact targets an ALPHA Paper build.

## After the operator's planned restart

1. Verify jar checksum, build 795, Java/Paper diagnostics and a clean startup.
   Check owner names (including prefixed Bedrock names), prices, stock and capacity.
2. Exercise owner/admin hopper insertion, extraction and minecarts; check both
   double halves, normal containers, manual restocking, purchases/sales, container
   extension/splitting, removal and chunk unload/reload. Compare exact components
   and counts before/after blocked attempts.
3. Under comparable hopper activity, record about 10 minutes 27 seconds using
   Spark's `--only-ticks-over 80 --interval 10` settings. Compare the two ShopChest
   listener paths and snapshot/component-decoding ancestry. Record workload and
   retained-tick counts; percentages apply only to the selected samples. Use a
   separate normal recording if an overall CPU comparison is needed.
4. Assess the separate BlueMapAddons owner-name path independently. Do not infer
   that this transfer fix resolves its 30-second stalls.
