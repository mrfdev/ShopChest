# Periodic hologram refresh: local I/O regression fix

This is the historical build-794 verification record. Its target, test results
and artifact checksum describe that build. See the
[stable migration record](paper-26.3-stable-2026-10-11.md) for the maintained
Paper target and subsequent verification.

## Scope and baseline

The reported production sample used Paper 26.2 build 129 and
`1MB-ShopChest-v1.15.3-SNAPSHOT-792-j25-26.2.jar`. The supplied
[Spark report](https://spark.lucko.me/OkMVydn72r) attributed approximately
278 seconds to `ShopUpdater.runTick`, 239 seconds to `PeriodicRefreshSweep.tick`,
and 209 seconds to the nested offline-owner lookup path during approximately
one hour of server-thread samples. These figures are nested, not additive.
The report page was not readable through the investigation's web tool;
those timings are user-supplied evidence, not a new measurement.

The behavior still existed in this checkout, based on `a137ea6`. At the time,
the target was Paper **26.3 build 41 ALPHA**, API **26.3.build.41-alpha**, and Java 25.
The target and release metadata were retained. The fix is not a 26.2 backport.

No server was started or restarted. No server configuration or installed JAR
was modified. The generated artifact remains in `plugin/build/libs`.

## Root cause and reachable name lookups

The periodic sweep called `Shop.updateHologramText`, which rebuilt the text
and called `OfflinePlayer.getName()` separately for:

1. The hologram format's `VENDOR` requirement.
2. The `%VENDOR%` placeholder.

A third conditional path was
`ShopProduct.getLocalizedNameComponent → LocalizedItemNameManager.getSkullOwnerName
→ SkullMeta.getOwningPlayer().getName()` for player-head products.
The formatter, item-details formatter, inventory counting, and display wrapper
contain no other offline-owner name lookup in this call chain.

Paper's build-41 implementation of `CraftOfflinePlayer.getName()` still falls
back to `getBukkitData()` when its stored profile has no name. That matches the
reported compressed player-NBT reads. ShopChest called it again on every
refresh, including twice for the same shop owner.

Separately, unchanged text was reformatted, converted into a panel, and passed
to all the display-data setters again. The sweep bounded callbacks but first
copied and deduplicated the entire shop collection on one tick.

Commands, transaction logging, catalogue operations, and the separate
Storefront Display manager have other name lookups outside this sweep. This
change does not claim to remove every offline-player lookup in ShopChest.

## Changes

- Added a shared UUID-keyed `OwnerNameCache`. Each active hologram retains an
  entry; the last release evicts it. Removal/unload clears the shop's render
  state, and plugin shutdown clears the cache and cancels the sweep.
- Resolve a newly needed name from the online player, then CMI's existing
  in-memory UUID record if available, then Paper's local profile cache.
  The CMI adapter uses `getAllUsers().get(uuid)` and `getName(false)`, never
  the loading `getUser` fallback. Optional API failure falls back once per
  plugin lifecycle. No database, NBT, Mojang, or other network lookup is added.
- Cache missing/blank names too, displaying the owner's UUID until an
  authoritative join supplies a name or a later display lifetime warms it
  again. Cache keys and ownership remain UUIDs; names and Bedrock prefixes
  are preserved verbatim. A conflicting profile UUID is rejected.
- A synchronous join listener updates the existing cache entry. The bounded
  sweep also checks name changes for formats without stock placeholders.
  Both vendor requirements and placeholders use the same cached value.
- Player-head names come from the item's stored profile directly. An unnamed
  head keeps the existing generic item-name fallback instead of loading an
  offline player record.
- Reuse formatted components while item contents, owner name, amount, prices,
  stock, capacity, formatting revision, language, colors, and relevant text
  settings are unchanged. Retain a cloned item so component changes invalidate
  the cached text. Economy-formatted values remain part of the comparison.
- Reuse the panel component when lines match and skip entity setters when
  the complete `TextDisplayData` matches. Presentation-setting changes still
  apply, and visibility remains independent of text updates.
- Traverse the existing concurrent shop-map view incrementally, examining at
  most 32 container entries per tick, including duplicates. Deduplicate during
  traversal instead of copying every shop at the start. Retain the 40-tick
  pause between sweeps. Removed displays are skipped; shutdown releases the
  iterator and visited set. Shops added during traversal may wait for the next
  sweep, consistent with the previous snapshot behavior.

All world, inventory, player, and entity access remains on the server thread.
`updateHologramText` now rejects an off-thread call before reading display or
inventory state. There is no background refresh task or asynchronous handoff.

## API verification

Discovery used the live [PaperMC documentation index](https://docs.papermc.io/llms.txt),
then the official [scheduler guide](https://docs.papermc.io/paper/dev/scheduler/)
and [display-entity guide](https://docs.papermc.io/paper/dev/display-entities/).
The following Javadoc pages identified themselves as `26.3.build.41-alpha`
when checked:

- [Server profile creation](https://jd.papermc.io/paper/26.3/org/bukkit/Bukkit.html#createProfileExact(java.util.UUID,java.lang.String))
- [Cache-only profile completion](https://jd.papermc.io/paper/26.3/com/destroystokyo/paper/profile/PlayerProfile.html#completeFromCache())
- [Offline player names and UUIDs](https://jd.papermc.io/paper/26.3/org/bukkit/OfflinePlayer.html#getName())
- [Stored skull profiles](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/meta/SkullMeta.html#getPlayerProfile())
- [Synchronous join event](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerJoinEvent.html)

The [official build metadata](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/41)
pins build 41 to Paper revision `a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa`.
Inspection of its
[profile implementation](https://github.com/PaperMC/Paper/blob/a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa/paper-server/src/main/java/com/destroystokyo/paper/profile/CraftPlayerProfile.java)
confirmed the UUID-only `completeFromCache()` route uses local caches; network
completion is in a different method. The
[offline-player implementation](https://github.com/PaperMC/Paper/blob/a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa/paper-server/src/main/java/org/bukkit/craftbukkit/CraftOfflinePlayer.java)
confirmed the measured NBT fallback. These internals were inspected as evidence,
not imported or called by the plugin.

The CMI adapter compiles against the project's existing CMI API 9.8.6.4.
Read-only bytecode inspection of the project-local CMI 9.8.10.1 copy confirmed
that `getAllUsers()` returns its in-memory map and that `getName(false)` skips
the offline-player fallback. Recheck this behavior when changing CMI versions.

## Verification results

The initial failing regression ran the real periodic sweep, shop formatter,
hologram, and display wrapper with instrumented Paper boundaries. After
warm-up, 100 refreshes of one offline-owner shop made **200 calls** to
`OfflinePlayer.getName()`. The test failed on those repeated calls.

The final refresh-path fixture runs the actual scheduled `ShopUpdater` callback,
real `ShopUtils`, sweep, `Shop`, `ShopProduct`, format parser, localized item
names, hologram, and wrapper. Only Paper/economy/optional-CMI boundaries are
faked. Its 16 tests cover:

- Two shops sharing one offline owner, followed by 100 additional sweeps:
  **one local-cache warm-up total, zero offline name calls, zero subsequent
  format renders, and zero unchanged display-data applications**.
- Cached misses, online-name precedence, rejected UUID mismatches, Java and
  prefixed Bedrock renames, static formats, and vendor requirements.
- CMI cache precedence and one-time fallback from an unavailable CMI API.
- Stock, capacity, persisted amount/price changes, sold-out localization,
  format reloads, item-name changes, panel settings, and player-head profiles.
- Visibility, removal/replacement, display recreation, off-thread rejection,
  actual plugin shutdown, and removal during a 513-shop sweep.

Five sweep tests include incremental traversal of **10,000 container entries**
representing 5,000 unique shops, with at most 32 entries consumed per tick.
Two cache tests cover shared-reference eviction, misses, UUID isolation, and
stale releases after clearing. Four platform-contract tests include the new
supported identity APIs. All **27 focused tests passed**.

With `JAVA_HOME` and `PATH` selecting JDK **25.0.4.1**:

```text
./gradlew clean build --no-build-cache --console=plain
BUILD SUCCESSFUL
268 tests, 0 failures, 0 errors, 0 skipped
```

Release metadata, generated descriptors, documentation, and platform checks
passed. Mockito and Byte Buddy are test-only and absent from the shaded JAR.
The build emitted SQLite native-access and JVM class-sharing notices; neither
caused a failure.

Generated artifact:
`plugin/build/libs/1MB-ShopChest-v1.15.4-SNAPSHOT-794-j25-26.3.jar`

SHA-256: `f6f4ba061605046545172eace7c2cc9507c3e7f4f545000a90fd01c2f9fc2c43`

## Limits and later Spark comparison

These are measured call counts in regression fixtures, not measured production
disk time or TPS improvements. Removing the offline-name route should eliminate
that NBT-read contribution to this sweep, but no claim is made about the
number of seconds saved or the recurring 30-second stalls. The separately
identified EventHunts cause remains outside this change.

Unknown names cannot be recovered from data available only in player NBT; a
UUID is displayed until trusted local information becomes available through a
join or subsequent warm-up. The cache is intentionally not persisted. Stock
and capacity scans still run on the server thread, and large collections take
multiple ticks to complete a sweep. This work did not include a running Paper
server, connected Java/Bedrock clients, packet capture, or live CMI integration
test; those remain runtime checks for a later approved staging window.

For a later authorized verification:

1. Confirm the intended Paper target first; this artifact targets 26.3, while
   the supplied production baseline is 26.2. Record the exact artifact checksum,
   server build, shop counts, online players, and dependency versions so a
   platform change is not silently attributed to this fix.
2. After a separate approved staging/deployment, verify names, a renamed Java
   player, a prefixed Bedrock player, unknown owners, trades, prices, capacity,
   visibility, and chunk unload/reload. Profile both cold warm-up and steady
   state under comparable shop/player load, ideally for the same hour-long
   sample window.
3. Compare the `ShopUpdater → PeriodicRefreshSweep → Shop.updateHologramText`
   subtree. Confirm the offline `getName → PlayerDataStorage.load` branch is
   absent, then compare normalized time/sample share, disk-read evidence,
   refresh latency, and display-update traffic. Keep EventHunts and individual
   30-second stalls as separate investigations unless new traces connect them.
