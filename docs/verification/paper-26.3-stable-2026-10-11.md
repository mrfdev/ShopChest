# Paper 26.3 stable migration, 2026-10-11

ShopChest `1.15.4-SNAPSHOT`, build **796**, now targets **Paper 26.3 build 175
STABLE**. Minecraft and `plugin.yml` remain at **26.3**. ShopChest's pre-live
snapshot status is independent of Paper's stable channel.

## Release verification

Discovery began with the live [PaperMC index](https://docs.papermc.io/llms.txt).
Neither the index nor `llms-full.txt` is vendored.

- The [Fill feed](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds)
  listed 175 as the newest STABLE build. Its
  [exact metadata](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/175)
  identifies commit `bd9daa8fba19cb288d690c1621ad1749de3e0a78`, published
  `2026-10-11T15:32:14Z`. The tested runtime was `26.3-175-bd9daa8 (MC: 26.3)`.
- [Maven metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)
  published `io.papermc.paper:paper-api:26.3.build.175-stable`. Compile and test
  dependencies are strictly pinned to that exact coordinate.
- The [exact module metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.3.build.175-stable/paper-api-26.3.build.175-stable.module)
  declares Java 25 variants, consistent with the official
  [runtime requirements](https://docs.papermc.io/paper/getting-started/) and
  [project setup guide](https://docs.papermc.io/paper/dev/project-setup/).
- The [hosted 26.3 Javadocs](https://jd.papermc.io/paper/26.3/) still identified
  build 157 beta. Exact verification used the official
  [build-175 Javadoc artifact](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.3.build.175-stable/paper-api-26.3.build.175-stable-javadoc.jar)
  and corresponding sources JAR, whose titles identify `26.3.build.175-stable`.

Paper server SHA-256, pinned in `gradle.properties` and independently verified
by PaperScript after download:

```text
ece5afb6d0cdf0c930489618bf92341e33d2ebcd4f21e6d365daff6ed26dafb0
```

## Changes and API review

- Updated release properties, strict compile/test dependencies, README,
  installation guidance, agent instructions, documentation manifest, release
  checks, and maintained PaperScript channel selectors to STABLE build 175.
  There are no paperweight/dev-bundle dependencies, dependency lockfiles, or CI
  workflows requiring migration in this repository.
- Retained Gradle wrapper 9.4.1, JDK **25.0.4.1**, Java 25 toolchains, and
  `--release 25`. Server tests used Oracle **27+35-2325**. ShopChest's classfile
  major version is **69**, confirming Java 25 bytecode.
- Increased the existing uncommitted build 795 to 796. Release numbers now
  advance independently of commit counts; the guard rejects regression below
  the committed build. This permits successive tested artifacts without
  committing unrelated working-tree changes. Retries keep 796.
- Strengthened drift checks for STABLE-only development, matching API/build,
  checksum format, Minecraft/Java targets, and current setup documentation.
  Validate PaperScript's staged release without claiming that staging launched
  it. Preserve clearly dated alpha records and the separate 26.2 archive.
- Reviewed the [roadmap](https://docs.papermc.io/paper/dev/roadmap/),
  [lifecycle](https://docs.papermc.io/paper/dev/how-do-plugins-work/),
  [scheduler](https://docs.papermc.io/paper/dev/scheduler/), descriptor, inventory,
  PDC, data-component and database contracts. Relevant build-feed changes include
  hopper container dirty-state handling (147), bundle/shelf corruption (157),
  and the additive PersistentDataKey API (164). The existing item persistence,
  namespaced PDC, snapshot-free inventory, display and local-profile APIs remain
  available. No NMS workaround or warning suppression was introduced.
- Fixed two database connection-error branches that called the failure handler
  directly from the SQL worker. Both now use `callSyncError`, allowing the startup
  handler to disable ShopChest on the main server thread. Two focused regression
  cases failed before the fix and pass afterward. Trade rules, permissions,
  configuration, schema and stored item formats remain unchanged.
- Added separately packaged runtime fixtures and a repeatable isolated runner.
  Fixture classes are absent from the release JAR. Existing uncommitted work was
  preserved.

## Results

| Check | Result |
| --- | --- |
| Clean build and smoke-fixture compilation, JDK 25.0.4.1 | Passed; 315 tests, zero failures/errors/skips |
| Full suite on Java 27, retaining JDK 25 compilation | Passed; 315 tests, zero failures/errors/skips |
| Release, generated descriptor, documentation and link checks | Passed |
| Negative drift probes: ALPHA, wrong API, regressed build, missing checksum | All four rejected |
| Resolved Paper API test/runtime dependency | `26.3.build.175-stable`, strict constraint |
| First start, reload and clean shutdown | Passed |
| Restart, second reload and clean shutdown | Passed |
| Unavailable SQLite database | Detected; plugin disabled cleanly without asynchronous lifecycle violation |
| Vault present without an economy provider | Detected; plugin disabled cleanly |

The disposable fixture is `servers/paper-26.3-stable-ev977gtk`. It binds only to
loopback, enables a whitelist, disables RCON/query, and starts with fresh worlds,
configuration and databases. Only dependency JARs were copied. No existing world,
database, player record or credential was imported. Its `verification.json`
records exact JAR hashes and four exit-0 runs.

Both healthy runs verified diagnostics, CMI economy, SQLite schema 2, command
availability, and a read-only audit of two ready shops with zero known issues.
Two synthetic UUIDs, one Java-shaped and one Bedrock-shaped, retained separate
shops and profiles across reload and restart. This is persistence coverage, not
an authenticated Java/Floodgate login test. ShopChest dispatches no LuckPerms
user commands; authoritative UUID handling and the local owner cache are retained.

Each healthy run passed **1,691 transfer assertions** across 28 supported
container materials and 10 double-container variants. Actual hopper and
hopper-minecart engine events were cancelled for registered shops without
changing counts/components or causing transfer-driven display refreshes.
Ordinary transfers still moved items and conserved counts.

Further runtime assertions checked TextDisplay/ItemDisplay reconstruction,
nested shulker contents, modified data components, foreign namespaced PDC, both
existing shop and binary ItemStack persistence, complete-item currency matching,
forged metadata and corrupt-payload rejection, full inventory insertion failure,
and SQL callbacks returning to the main thread. SQLite integrity was `ok` and
all persisted shop rows matched after restart. Unit coverage also includes
escrow/refund state, duplicate/retry behavior, stock/capacity, interaction guards,
owner identities and profile policies.

The first draft runner queried diagnostics before asynchronous initialization
finished on restart. It now waits for ShopChest's loaded-shops message; the whole
suite was rerun successfully. This harness readiness issue was separate from the
reproduced database failure-callback bug.

## Repeatable isolated verification

From the repository root, with the pinned Paper JAR staged as documented in the
[local server guide](../agents/test-server.md):

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.1.jdk/Contents/Home
export JAVA27_HOME=/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build :plugin:paperSmokeJars --no-build-cache
./gradlew :plugin:test -PshopchestTestJavaVersion=27 --rerun-tasks
python3 scripts/verification/run-paper-stable-smoke.py \
  --paper-jar servers/Paper-26.3/Paper-26.3-175.jar \
  --dependencies servers/Paper-26.3/plugins \
  --java-home "$JAVA27_HOME"
```

The runner requires Python 3.9+, rejects incorrect Paper checksums and missing
or duplicate dependency JARs, and creates a new directory every time. It never
starts or writes to the dependency source server. It leaves stopped fixtures and
logs for inspection. Never install smoke JARs on shared or production servers.

Tested dependencies: Vault **1.7.3-CMI** (filename `Vault-1.7.4.jar`), CMI
**9.8.10.1**, CMILib **1.6.0.0**, LuckPerms **5.5.85**, WorldEdit
**7.4.6-beta-02+2c90a77a1**, and WorldGuard **7.0.19+2400-f395a16**. WorldEdit's
artifact remains a beta; no newer third-party compatibility is claimed.

## Artifact and development staging

```text
plugin/build/libs/1MB-ShopChest-v1.15.4-SNAPSHOT-796-j25-26.3.jar
SHA-256: b452c4f2a16e0972ad94205c907dd2610b23aa13a066c6bef5488e4789489d73
```

The exact tested JAR is installed in `servers/Paper-26.3/plugins/`, using a
lock, process/port inactivity checks and manifest identity. Build 795 is backed
up under `plugins-disabled/stable-796-20261011/`. Paper 175 is checksum-verified
and staged, with build 41 preserved as the previous launched JAR. The maintained
instance remains stopped; its launch history still correctly records the alpha
run. The 26.2 archive and shared/production servers were untouched.

Build logs, XML reports for both JVMs, drift probes, official metadata,
PaperScript verification, and runtime/staging receipts are retained under
`logs/paper-26.3-stable-2026-10-11/`. Generated artifacts and all server data stay
out of Git.

## Remaining player and infrastructure checks

No player client was driven. MySQL, AutoSell, AFKShrine, Geyser/Floodgate and the
other optional protection integrations were absent from this isolated fixture.
Complete the [Storefront checklist](../storefront-beta-test.md) with synthetic
data in another isolated instance before live deployment:

1. Join with Java and `.bedrock` clients. Use LuckPerms with their actual server
   UUIDs to deny/grant create, buy/sell, profile, advertising and admin permissions.
   Reconnect and test renamed accounts; ownership must remain with the same UUID.
2. Create normal/admin shops and trade in both directions; compare balances and
   item counts. Repeat with full inventories, insufficient funds/stock, no shop
   capacity, concurrent/rapid interactions and AutoSell enabled.
3. Exercise click, shift-click, drag, double-click, hotbar, offhand, drop, creative,
   close, quit, death and world changes. Verify display visibility, stock text,
   cooldowns and stale-session rejection with real clients.
4. Trade metadata-heavy items from the intended live plugins. Capture an actual
   AFKShrine token on the isolated server, reject forged lookalikes, and test
   confirmation, queue cancellation, refunds and restart/reconnect recovery.
   Force disposable process failures between transaction stages; inspect recovery
   before retrying and verify that no items or benefits are delivered twice.
5. Configure an actual WorldGuard marketplace and check region/location boundaries.
   If using MySQL, repeat persistence, rollback and unavailable-database cases
   against a dedicated disposable MySQL instance.

Non-fatal notices remain: OSHI does not recognize the macOS 27 codename;
LuckPerms/Commodore warns about reflective final-field mutation on Java 27;
the unit-test SQLite driver warns about future native-access restrictions.
No warning suppression or weakened Paper exploit setting was added. Review those
upstream dependencies before moving to a stricter future Java runtime.
