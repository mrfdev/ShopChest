# Persistent copper shop lifecycle, 2026-10-20

This extends the [Paper, CMI and Java compatibility record](paper-cmi-java-2026-10-20.md)
with one persistent copper shop. It uses the same frozen ShopChest build 797,
Paper 26.3 build 207 STABLE, CMI 9.8.11.0, CMILib 1.6.1.0 and Oracle 27.0.1.
The fixture is compiled with Oracle 25.0.5 and packaged separately from ShopChest.

## Results

The copper lifecycle passed **9,832 assertions** over first start and clean
restart, using persistent shop ID **3** throughout:

| Check | First start | After restart |
| --- | --- | --- |
| Copper assertions | 4,805 | 5,027 |
| Copper materials / state stages | 8 / 16 | 8 / 16 |
| Actual saved chunk unload/reload cycles | 8 | 8 |
| Successful buy/sell probe pairs | 24 | 25 |
| Denied full-inventory / empty-stock attempts | 24 / 24 | 25 / 25 |
| Blocked incoming / outgoing hopper events | 40 / 40 | 40 / 40 |
| Blocked hopper-minecart events | 240 | 240 |

The extra restart trade probe runs on the original saved exposed-copper state,
before the next transition. In total, 98 successful trades and 98 denied
attempts passed their item, balance and event assertions. The 315 unit tests
also passed with no failures, errors or skips.

No production Java change was needed. The tests confirm the existing
ShopChest container resolution accepts the current copper variant and retains
the persistent shop across material changes and cold boot.

All four runner processes exited zero. Both healthy runs also passed the
existing 1,691-assertion transfer suite, CMILib `PaperRawMessage` checks,
CMI/CMILib reload checks, three-shop audit with zero known issues and SQLite
integrity. Unavailable database and missing economy cases both disabled
ShopChest as expected and shut down cleanly. Metadata and documentation
validation passed.

## Scope and assertions

The shop begins as `COPPER_CHEST` and retains its database ID, synthetic owner
UUID, coordinates, normal-shop type, buy/sell prices and two-item trade bundle.
Sixteen stages cover all eight waxed and unwaxed copper chest materials:
oxidation to exposed, weathered and oxidized copper; waxing and wax removal at
each oxidation level; scraping back toward copper; and a final exposed-copper
checkpoint for restart.

Each transition uses supported `Block.setBlockData` with normal physics.
Common chest properties are copied and checked across each material change.
Inventory contents are never copied back to conceal a transition failure.
Random ticks are disabled so the specified transition is deterministic. These
are simulated material changes, not evidence of client honeycomb/axe input or
natural oxidation timing.

The stock includes a product with a custom name, long lore, foreign PDC strings
and bytes, glint override and a maximum stack size of 16. Unrelated stock
includes a shulker box containing another five copies of the metadata-rich
product. Exact contents, quantities, components, capacity and authoritative
container resolution are checked throughout.

For every material, native hoppers attempt insertion and extraction, and an
actual hopper minecart attempts extraction. Observed transfer events must be
cancelled and all affected inventories must remain unchanged. An adjacent
unregistered chest/hopper control must transfer items successfully.

Each material also undergoes an actual saved chunk unload and reload. The
fixture releases its force-load ticket, waits for native lifecycle events and
proves the chunk remains absent across ticks before loading it again. It
requires automatic ShopChest display recovery without calling the repair
helper. Separately, deliberate entity removal exercises idempotent public
display restoration. Both checks inspect actual `TextDisplay` and `ItemDisplay`
entities, their content and validity, duplicate counts and retired UUIDs.

The cold boot reads the saved exposed-copper checkpoint before making any new
transition. Database identity, world UUID, contents and display recovery must
survive a clean process shutdown and restart. The runner also compares complete
persisted shop rows and performs a read-only SQLite integrity check.

## Trade boundary

The trade probe invokes the registered ShopChest `ShopInteractListener` through
its public event-handler method. It uses the actual CMI/Vault economy provider,
UUID-keyed synthetic CMI accounts, real Bukkit inventory storage and real
`ShopBuySellEvent` delivery. Buy and sell operations must conserve money and
items, transfer the exact product bundle and preserve unrelated metadata.
Full-inventory and empty-stock attempts must produce the intended denial with
no trade event, delivery, debit or credit.

Only the `Player` and `PlayerInventory` interface boundary is synthetic.
Unexpected interface calls fail the fixture. Confirmation, click pacing and
WorldGuard integration are temporarily disabled within the synchronous probe
and restored in `finally`; no player may be online. This does not establish
connected-client input, ordinary-player permission behavior, WorldGuard region
policy, authentication, or client feedback rendering.

## Repeatable verification

Build the separate fixtures without rebuilding an already-frozen release JAR:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.5.jdk/Contents/Home
export JAVA27_HOME=/Library/Java/JavaVirtualMachines/jdk-27.0.1.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :plugin:paperSmokeJars --no-build-cache
python3 scripts/verification/run-paper-stable-smoke.py \
  --paper-jar servers/Paper-26.3/Paper-26.3-207.jar \
  --dependencies servers/Paper-26.3/plugins \
  --java-home "$JAVA27_HOME" \
  --copper-lifecycle
```

The optional flag adds the copper fixture to a new disposable, loopback-only
server. It imports JARs only and retains the original stable, CMI message and
transfer checks. Expected persistent shop count becomes three. Its four
process runs cover first start, restart, missing database and missing economy;
each process must stop cleanly. Do not install fixture JARs on shared or live
servers.

## API review and evidence

Discovery used the [live PaperMC index](https://docs.papermc.io/llms.txt),
[scheduler documentation](https://docs.papermc.io/paper/dev/scheduler/) and
[display documentation](https://docs.papermc.io/paper/dev/display-entities/).
Exact contracts came from the official
[build 207 API sources and Javadocs](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.3.build.207-stable/paper-api-26.3.build.207-stable-sources.jar).
`setBlockData` does not promise inventory retention, so the test checks it
directly. Unload requests alone are insufficient: actual chunk absence,
native events and entity invalidation are required.

Ignored evidence is retained under `logs/copper-lifecycle-2026-10-20/`.
The successful server was `servers/paper-26.3-stable-81udbzl_` and is stopped.
Its `verification.json`, four process logs, fixture/source hashes and unit test
XML are retained in the evidence directory. The maintained and shared servers
were not started, the existing visual inspection server was not restarted,
and no live-server access was required.

The initial run exposed a fixture timing assumption about force-load ticket
removal. It passed initial stock, display and native transfer checks, then
stopped on an immediate unload assertion. The fixture now waits for ticket
processing and verifies the same physical unload requirements.

The frozen release SHA-256 remains:

```text
c30802cc06132f2a33b49dff80f0bf58bcebb7b81c04055342b6ea6235d5bc85
```
