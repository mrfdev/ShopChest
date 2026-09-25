# Paper 26.3 upgrade: 2026-09-25

The Java 25 build and Java 27 local runtime verification passed. This remains a
pre-live beta snapshot; the manual player and economy checklist is incomplete.

- Previous tested release: `16019bafc2bc80b77958974b69211ee2abb77437`,
  annotated tag `v1.15.3-SNAPSHOT-paper-26.2`, version `1.15.3-SNAPSHOT`, build 793.
- Existing unfinished workspace: `10f9ba9fb8a9c461a27da4e9020ab05e813cf916`,
  branch `codex/preserve-paper-26.2-workspace`, annotated tag
  `v1.15.3-SNAPSHOT-paper-26.2-workspace`. Java 25 clean build and all 248 tests
  passed before archival. This archive consumes no release build number.
- Release: `1.15.4-SNAPSHOT`, build 794, tag `v1.15.4-SNAPSHOT-794`.
- Target: Paper 26.3 build 41 ALPHA, API
  `io.papermc.paper:paper-api:26.3.build.41-alpha`.
- Paper JAR SHA-256:
  `2b77166ee61886a9bc9ab33dc9e4847fa3538b36d9ba6e5f2fa7ed90973aa748`.
- Build/test JDK: Oracle `25.0.4.1+1-LTS-5`; runtime JDK: Oracle `27+35-2325`.
- Rollback directory: `servers/Paper-26.2`; new instance: `servers/Paper-26.3`.

The earlier release's runtime evidence remains in the
[JDK verification record](jdk-2026-09-15.md). It does not claim Paper 26.3 testing.

The archive includes the existing unfinished storefront work. The 26.3 release
retains that source and changes only platform/release metadata, build validation,
the corresponding contract-test expectations, and documentation relative to
the archived workspace. Its beta status and manual test requirements remain.
Both 26.2 tags and the archive branch were pushed before release metadata changed.
The archive is a separate branch, so the release branch advances once, from
793 to 794 commits. Failed build attempts reused build 794.

## Exact target and API review

Discovery began with the live [PaperMC documentation index](https://docs.papermc.io/llms.txt).
The [official 26.3 build feed](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds)
contained only ALPHA builds when inspected. Build 41 was the latest, published
2026-09-25 at 03:26:20 UTC, commit `a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa`.
The observed runtime identifies itself as `26.3-41-a15fed9 (MC: 26.3)`.
Experimental builds were explicitly authorized for this upgrade.

The exact coordinate appears in the official
[Maven metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml).
Both compile and test classpaths use `26.3.build.41-alpha`; generated `plugin.yml`
declares API `26.3`. PaperScript's default and check channels are explicitly
`ALPHA`. A fresh `paperscript verify` independently matched the downloaded
server JAR to the exact build's published SHA-256.

The [project setup](https://docs.papermc.io/paper/dev/project-setup/),
[roadmap](https://docs.papermc.io/paper/dev/roadmap/), and
[26.3 deprecations](https://jd.papermc.io/paper/26.3/deprecated-list.html)
were reviewed. The Javadoc header identified `26.3.build.41-alpha`. ShopChest
compiled with `-Xlint:deprecation` and `-Xlint:unchecked` without compiler warnings.
No compatibility changes to gameplay code, NMS, reflection, item identity,
permissions, or persistence namespaces were required by this platform upgrade.

Relevant contracts were checked in the exact-version
[ItemStack](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/ItemStack.html),
[PlayerInteractEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerInteractEvent.html),
[InventoryClickEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/inventory/InventoryClickEvent.html),
[Display](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Display.html),
[TextDisplay](https://jd.papermc.io/paper/26.3/org/bukkit/entity/TextDisplay.html), and
[BukkitScheduler](https://jd.papermc.io/paper/26.3/org/bukkit/scheduler/BukkitScheduler.html)
references. This includes separate item/block interaction cancellation,
inventory drag handling, next-tick inventory view changes, full item metadata,
and main-thread world/entity access. The official scheduler, event-listener,
PDC, data-component, and plugin descriptor guides were also consulted.

The [global](https://docs.papermc.io/paper/reference/global-configuration/) and
[world configuration](https://docs.papermc.io/paper/reference/world-configuration/)
references were checked. The generated local 26.3 configuration retains safe
defaults: piston duplication, unsafe end-portal teleportation, headless pistons,
permanent block-breaking exploits, and skipped tripwire validation are false;
oversized item-component sanitizer exclusions remain empty. No protection was
relaxed to make a test pass.

Paper's general requirements still specify Java 25 or newer. Java 27 is the
deliberately selected runtime for this test instance, not a raised bytecode
requirement. Both installed JDK version strings were verified directly.

## Build and distributable

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.1.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build --no-build-cache
```

- 248 tests across 76 classes passed; zero failures, errors, or skips.
- Release metadata, generated metadata, and documentation/drift checks passed.
- The single distributable is
  `1MB-ShopChest-v1.15.4-SNAPSHOT-794-j25-26.3.jar`.
- SHA-256: `6ee59ea26c0f8326661ba8b75e09e5553058cde0588724af15860303d6f2d6ba`.
- All 504 ShopChest classes use major version 69, minor 0 (Java 25).
  All 1,077 shaded classes use Java 25 or earlier, without preview bytecode.
- `jar --validate` passed. Generated plugin identity remains `ShopChest`, main
  class `de.epiceric.shopchest.ShopChest`, with version `1.15.4-SNAPSHOT`.
- The artifact was frozen before smoke testing. The exact same bytes are the
  deployment candidate; subsequent documentation verification does not rebuild it.

The first build attempt rejected the internal server guide at the top level of
`docs/`, where every document must be in the public import manifest. Moving it
to `docs/agents/` fixed the classification; the complete clean build then passed.
This did not consume another release number. The test JVM emitted SQLite's
existing native-access warning, without failing any test.

## Local runtime verification

`servers/Paper-26.3` was copied only after checking the original instance was
stopped. All 775 files in `servers/Paper-26.2` still matched their original
SHA-256 digests after the tests. The original instance contains ShopChest build
792, Paper build 84 plus a separately staged build 121; neither was promoted or
modified. The separately preserved tested build 793 JAR matches the earlier
verification record's checksum.

The new launcher uses Java `27+35-2325`, requires Java 27, and selects only 26.3
JARs. Copied version, download, cache, launcher marker, and lock state was moved
aside before initializing the new instance. It binds to `127.0.0.1:28673`;
disabled query/RCON endpoints reserve UDP 28674 and TCP 28675. PaperScript and
the runner use the unique session name `shopchest-paper-26.3-28673`.

The copied database/world fixture was tested on initial 26.3 startup and a
subsequent clean restart. Both runs checked readiness, ShopChest enable/version,
build/API/Java diagnostics, `/shops info`, debug commands/permissions/placeholders,
read-only audit, advertising currency status, reload, and orderly stop.

- All 11 shops loaded, with 11 TextDisplay and 11 ItemDisplay entities.
- Audits before and after reload reported 11 ready, zero known issues, and
  zero unchecked shops. ShopChest diagnostics reported zero warnings.
- CMI economy and worth advisory were active; SQLite connections were valid
  with schema 2 before and after reload.
- Both runs stopped cleanly with Hikari shutdown and completed chunk I/O for
  all three dimensions. The maintained launcher exited 0.
- SQLite integrity was `ok`; all 11 original shop rows matched after shutdown.
- No ShopChest or server ERROR/exception was observed. External messaging
  integrations remained inactive.

The first harness run expected the older shutdown text `All dimensions are
saved`. Paper 26.3 instead logs per-dimension I/O completion. Inspection showed
that shutdown had succeeded; the harness was updated to require all three
dimension completions and final region I/O completion, and the entire smoke
sequence was rerun successfully. Database integrity was checked after both runs.

Remaining third-party notices are OSHI's unknown macOS 27 codename and
LuckPerms/Commodore's reflective mutation of a Brigadier final field on Java 27.
No flag was added to suppress or weaken the runtime restriction.

## Dependency JARs

Only dependency JARs were copied from the shared 26.3 directory. Its configuration
and player data were not imported. All selected plugins enabled successfully.

| Plugin | Previous local version | Copied manifest version / file |
| --- | --- | --- |
| CMI | 9.8.9.9 | 9.8.10.1 / `CMI-9.8.10.1.jar` |
| CMILib | 1.5.9.9 | 1.6.0.0 / `CMILib1.6.0.0.jar` |
| CoreProtect | 24.0-dev1 | 25.0 / `CoreProtect-25.0-26.3.jar` |
| LuckPerms | 5.5.81 | 5.5.85 / `LuckPerms-Bukkit-5.5.85.jar` |
| WorldEdit | Inactive | 7.4.6-beta-02+2c90a77a1 / `worldedit-bukkit-7.4.6-beta-02.jar` |
| WorldGuard | Inactive | 7.0.19+2400-f395a16 / `worldguard-bukkit-7.0.19.jar` |
| PlaceholderAPI | 2.12.3 | 2.12.3 / `PlaceholderAPI-2.12.3.jar` |
| Vault | 1.7.3-CMI | 1.7.3-CMI / `Vault-1.7.4.jar` |

Other copied plugin JARs remain recoverable under
`servers/Paper-26.3/plugins-disabled/copied-paper-26.2/`. Geyser, Floodgate, and
other optional integrations were not needed for this console smoke scope.

## Deployment boundary and remaining checks

After committing and pushing this release, install the frozen artifact into
`/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3/plugins`, following the
[local server and deployment guide](../agents/test-server.md). The identified
old shared artifact is `1MB-ShopChest-v1.15.3-SNAPSHOT-792-j25-26.2.jar`.
This is a stopped-server file replacement only; the shared server must not be
started. The final task report and local deployment receipt record the result.

The inherited fixture uses `GLOBAL` discovery. Manual player trade and GUI
checks, rapid/concurrent actions, full inventories, reconnect/death/disable
failure scenarios, advertising token purchases/refunds, actual MARKETPLACE
region checks, AutoSell, and Java/Bedrock gameplay remain on the
[beta checklist](../storefront-beta-test.md). These were not replaced by the
console smoke tests, and no production rollout is claimed.

Local logs, frozen artifacts, XML/HTML reports, fixture fingerprints, dependency
manifests/checksums, downloaded official metadata, smoke driver, and deployment
receipt are retained under `logs/paper-26.3-upgrade/`. No server data, logs,
downloads, or JARs are added to Git.
