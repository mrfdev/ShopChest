# Paper, CMI, CMILib and Java compatibility, 2026-10-20

ShopChest `1.15.4-SNAPSHOT` build **797** targets Paper **26.3 build 207 STABLE**,
compiled with Oracle **25.0.5** for Java 25 and tested on Oracle **27.0.1**.
Automated compatibility checks passed. On 2026-10-20 the user also reported
that the ingame inspection was in order, completing the requested manual
compatibility check. The broader [Storefront beta checks](../storefront-beta-test.md)
remain separately tracked. Build 797 is the tested upgrade candidate; no live
deployment has been performed.

## Target verification

Discovery began at the live [PaperMC documentation index](https://docs.papermc.io/llms.txt).
The [exact Fill record](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/207)
identifies build 207 as STABLE, published `2026-10-20T12:13:00Z`, commit
`bc68acd74104584b13aac76261f64fbe65358876`. PaperScript independently verified the
staged server against a fresh official checksum response:

```text
2df3b846f70716907d380915cc026667a9cadedf99b1ec680b0c5ff0e6132526
```

The [exact API module](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.3.build.207-stable/paper-api-26.3.build.207-stable.module)
declares Java 25 and Adventure 5.2.0. Hosted 26.3 Javadocs identified build 195
at review time, so API verification used the official
[207 sources and their Javadocs](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.3.build.207-stable/paper-api-26.3.build.207-stable-sources.jar).
Neither Paper's documentation index nor full documentation is vendored.

Relevant intervening changes include locale-aware component serialization (183),
decorated-pot serialization (189), JDK 27 UID startup handling (193), synchronous
container opening (195), `PrepareHopperTransferEvent` (199), and inventory stack
size validation (205). The new hopper preparation event precedes the existing
move event; cancellation still protects the source inventory.

Reviewed the official [roadmap](https://docs.papermc.io/paper/dev/roadmap/),
[display APIs](https://docs.papermc.io/paper/dev/display-entities/),
[scheduler](https://docs.papermc.io/paper/dev/scheduler/),
[lifecycle](https://docs.papermc.io/paper/dev/how-do-plugins-work/), and
[world](https://docs.papermc.io/paper/reference/world-configuration/) and
[global](https://docs.papermc.io/paper/reference/global-configuration/) settings.
Keep `hopper.disable-move-event=false` and the default exploit protections.
No NMS workaround or relaxed security setting was needed.

Oracle's [25.0.5](https://www.oracle.com/java/technologies/javase/25-0-5-relnotes.html)
and [27.0.1](https://www.oracle.com/java/technologies/javase/27-0-1-relnotes.html)
release notes confirm today's updates. Installed runtimes report
`25.0.5+9-LTS-148` and `27.0.1+9-62`. Release validation now checks the actual
Gradle, compiler and selected test JVM update against the exact properties in
`gradle.properties`; bytecode remains Java 25, classfile major **69**.

## CMI and CMILib review

Tested the supplied downloaded JARs, verified by their plugin descriptors:

| Dependency | Version | SHA-256 |
| --- | --- | --- |
| CMI | 9.8.11.0 | `8ae121712972521413ab82b6b15274e93a14122e60d9f59d747faa1b045350e8` |
| CMILib | 1.6.1.0 | `4f625972e1cf81ae40eca5dedf6bd39180c7a193b4888226fe99ccf8fd9794b7` |

ShopChest creates its own Paper `TextDisplay` and `ItemDisplay` entities and sends
Adventure components directly. It does not call CMI's hologram renderer or
CMILib's RawMessage API. All eight CMI method descriptors used by ShopChest's
worth and UUID-keyed local owner-name integrations remain compatible. Local
owner lookup still reads cached users without creating or externally resolving
unknown identities. No ShopChest production Java changes were required.

The CMI change is real for **CMI-managed** holograms: `ICON:` selects a floating
icon, while `SICON:` selects a static icon. Review existing CMI layouts that
previously relied on `ICON:` appearing static. ShopChest's item animation remains
controlled by its own configuration.

The new CMILib backend was verified as `PaperRawMessage` with its default
`LegacyRawMessage: false`. The isolated fixture checks the actual serialized
components for text, run-command, suggestion, URL, text hover, item hover, and
CMI rawmsg tag parsing. Legacy fallback was not enabled.

Other tested JARs remained Vault **1.7.3-CMI**, LuckPerms **5.5.85**, WorldEdit
**7.4.6-beta-02+2c90a77a1**, and WorldGuard **7.0.19+2400-f395a16**. The official
[LuckPerms comparison](https://github.com/LuckPerms/LuckPerms/compare/25f223317a9ec2b6e73369126b630eca07d79506...5fc3d583c412854e050b1e4ba8aa5036d8daf982)
shows 5.5.87 adds NeoForge and Javadoc changes, with no relevant Bukkit or Java 27
fix; no additional plugin update was needed for this scope.

## Automated results

| Check | Result |
| --- | --- |
| Clean build on Oracle 25.0.5 | 315 tests passed; zero failures, errors or skips |
| Full unit suite on Oracle 27.0.1, compiled with 25.0.5 | 315 tests passed; zero failures, errors or skips |
| Release metadata, descriptors, docs and links | Passed |
| First start, ShopChest reload, restart and clean shutdown | Passed |
| Actual hopper and hopper-minecart protection | 1,691 assertions per healthy run, 28 materials and 10 double-container variants |
| TextDisplay/ItemDisplay recreation | Passed |
| Nested items, foreign PDC, component persistence and full inventories | Passed |
| Separate synthetic Java/Bedrock-shaped UUID ownership and profiles | Passed through reload and restart |
| SQL callbacks, schema 2, audit, SQLite integrity | Passed; two ready shops, zero known audit issues |
| New CMI economy registration, worth and cached owner APIs | Passed |
| New CMILib Paper message backend | Passed, including click/hover payload assertions |
| Saved CMI ICON/SICON holograms and CMI/CMILib config reloads | Passed |
| Missing database and missing economy provider | Both fail closed; clean shutdown, no asynchronous lifecycle violation |

The fixture is fresh, loopback-only, with RCON/query disabled. It never imports
worlds, databases, player data or credentials. Synthetic UUID coverage does not
establish authenticated Floodgate login support. No client was automated.
MySQL, AutoSell, AFKShrine, Geyser/Floodgate and other optional integrations were
not present; their remaining live-stack checks must use an isolated environment.

The additional [persistent copper lifecycle regression](copper-lifecycle-2026-10-20.md)
covers one shop across all eight copper states, native chunk unload/reload,
cold boot and trading through a synthetic player boundary. It uses the same
frozen release artifact and a separate fresh server.

Non-fatal upstream notices remain: OSHI does not recognize macOS 27's codename;
LuckPerms/Commodore reports reflective final-field mutation on Java 27; unit tests
report Mockito/final-field and SQLite native-access warnings. No warning
suppression or security override was introduced. The tested JVMs complete both
normal and failure-path shutdowns.

## Repeatable isolated verification

Use only fresh disposable worlds and plugin data. Stage Paper 207 as described
in the [test server guide](../agents/test-server.md), then:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.5.jdk/Contents/Home
export JAVA27_HOME=/Library/Java/JavaVirtualMachines/jdk-27.0.1.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build :plugin:paperSmokeJars --no-build-cache
./gradlew :plugin:verifyReleaseMetadata :plugin:test -PshopchestTestJavaVersion=27 --rerun-tasks
python3 scripts/verification/run-paper-stable-smoke.py \
  --paper-jar servers/Paper-26.3/Paper-26.3-207.jar \
  --dependencies servers/Paper-26.3/plugins \
  --java-home "$JAVA27_HOME"
```

The runner copies JARs only, verifies the pinned Paper checksum and exact runtime
update, and creates its own isolated server. It verifies saved CMI holograms
before recreating them on restart, then tests both dependency reload commands.
Its fixtures are separate artifacts and must never be installed on a shared or
production server.

## Frozen candidate

```text
plugin/build/libs/1MB-ShopChest-v1.15.4-SNAPSHOT-797-j25-26.3.jar
SHA-256: c30802cc06132f2a33b49dff80f0bf58bcebb7b81c04055342b6ea6235d5bc85
```

No fixture classes are present in this release JAR. Build 797 is reserved for
this candidate and is reused for retries. Logs, XML test reports, official
metadata, dependency hashes, runtime receipts and staging evidence are retained
under the ignored `logs/paper-cmi-java-2026-10-20/` tree. Original downloaded JARs
are unchanged; decompiler output is isolated under ignored `.scratch/`.

## Local staging and client inspection

The maintained `servers/Paper-26.3` instance remains stopped. Paper 207 is staged,
its launcher selects Oracle 27.0.1, and the exact frozen ShopChest JAR plus the
new CMI/CMILib JARs are installed. Replacement used a deployment lock, process,
port and world-lock checks, descriptor identity checks, atomic renames and
verified checksums. Prior plugin JARs remain in
`plugins-disabled/compatibility-797-20261020/`; PaperScript retained its previous
Paper JARs. The 26.2 archive, shared test server and live server were untouched.
No access to `m4pro-mini` was needed.

The final automated suite passed in `servers/paper-26.3-stable-micd3wrs`; all four
runs exited zero. That same disposable instance was then prepared for human
inspection on **127.0.0.1:28683**, with online authentication,
whitelist enforcement, and RCON/query disabled. Only the locally verified
`mrfloris` UUID was added to its whitelist and operator list; no player NBT,
inventory, economy balance or existing plugin database was imported.

After the user's successful inspection, this disposable server was **cleanly
stopped on 2026-10-20** at their request. Its recorded PID, working directory and
loopback listener were checked before sending `stop` to the
`shopchest-compat-797` tmux session. ShopChest/database shutdown and world I/O
completion were confirmed, the process exited, and port 28683 closed. The
world and evidence remain available locally; the live server was untouched.

Spawn faces a lit platform at approximately `(6, 100, -2)`. The two synthetic
ShopChest shops are at `(4, 100, 4)` and `(8, 100, 4)`. A persistent Storefront
Display on an Ender Chest is at `(12, 100, 4)`. The fixture confirmed its actual
text, item icon and interaction entity exist before reporting `SHOWCASE_READY`.
CMI floating/static examples are at `(4.5, 103, 9.5)` and `(8.5, 103, 9.5)`.

1. Join with Minecraft 26.3 and inspect text, color, item icons, animation and
   spacing from multiple angles and distances. Compare CMI's floating `ICON`
   with its static `SICON`; compare both with ShopChest's own displays.
2. Run `/cmicompat visual`. Hover the text/item samples, use the suggested search,
   and click each command sample. Successful sample clicks print `Click received`
   in chat and `CMI_COMPAT_CLIENT_CLICK` with the player's server UUID in the log.
   The run-command sample invokes ShopChest's real message helper, and the other
   samples pass through CMILib's new backend and CMI rawmsg markup parser.
3. Click the ShopChest Storefront Display and use `/shops list`, its clickable
   locations, `/shops search shulker_box`, and profile navigation. Inspect actual
   shop item tooltips. Repeat with another client language to cover Paper's
   translation change.
4. While standing still, run `/cmi reload`; the saved CMI examples should reappear
   without requiring movement. `/cmicompat verifyvisual` checks their loaded
   records, but only the player can confirm client visibility. Test `/cmil reload`
   and `/shops reload`, then relog and repeat the samples.
5. Complete ordinary-player trade, permission, inventory and adversarial checks
   in the beta checklist before approving the candidate for live deployment.
   Current operator access is for visual/admin inspection, not evidence of
   ordinary-player permission behavior.

Server-side inspection also passed after switching to online mode, including
CMI/CMILib reload, idempotent showcase preparation and `save-all flush`.

**Manual compatibility check passed, user-reported on 2026-10-20.** After the
ingame inspection handoff, the user reported: "it all seems to be in order".
This records acceptance of the requested visual and interaction inspection.
No individual checklist results were supplied, so it does not claim completion
of every ordinary-player, adversarial, language or optional-integration case.
The frozen build 797 artifact remains unchanged.
