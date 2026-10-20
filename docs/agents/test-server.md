# Maintained local test server

The maintained development instance is `servers/Paper-26.3`, cloned from the stopped
`servers/Paper-26.2` instance. Preserve the entire 26.2 directory for rollback.
Never open its worlds or databases with a newer server.

The instance targets Paper 26.3 build 207 STABLE. Java 27.0.1 at
`/Library/Java/JavaVirtualMachines/jdk-27.0.1.jdk/Contents/Home` runs its maintained
`1MB-minecraft.sh` launcher. The launcher selects only the 26.3 JAR family and
requires Java 27.0.1. Compilation and the required unit suite use JDK 25.0.5;
the plugin's bytecode remains Java 25.

The instance binds to loopback on TCP 28673. Query UDP 28674 and RCON TCP 28675
are reserved but disabled. Its session name is `shopchest-paper-26.3-28673`.
Before starting, check both TCP and UDP availability and other projects' saved
server ports. PaperScript uses this distinct session and server directory,
with both channel selectors set to `STABLE`. Stage and verify exact builds
without starting this instance:

```bash
cd servers/Paper-26.3
python3 paperscript/paperscript.py --server-dir "$PWD" download --version 26.3 --build 207 --channel STABLE
python3 paperscript/paperscript.py --server-dir "$PWD" verify
```

Keep external messaging plugins such as DiscordSRV inactive. The maintained instance
contains Vault, CMI, CMILib, LuckPerms, PlaceholderAPI, CoreProtect, WorldEdit, and
WorldGuard. The disposable stable suite copies only Vault, CMI, CMILib, LuckPerms,
WorldEdit, and WorldGuard JARs and creates fresh configuration and data.
Compatible JARs may be copied from the shared server; preserve the
project-local configuration and data. Copied plugins outside this smoke scope
are retained in `plugins-disabled/copied-paper-26.2/`.

Use a fresh disposable server for compatibility verification. Never copy worlds,
databases, player records, or credentials from the maintained or shared servers.
The repeatable isolated test instructions and results are in the
[current compatibility record](../verification/paper-cmi-java-2026-10-20.md).
Add `--copper-lifecycle` to the runner for the
[persistent copper shop regression](../verification/copper-lifecycle-2026-10-20.md),
including all eight copper variants, real chunk recovery and CMI/Vault trades
through a synthetic player boundary.
Staging a Paper JAR changes `staged_*` metadata; `current_*` and the last-launched
marker continue to describe the last actual launch. Do not start the maintained
instance as part of this migration.

Run `./gradlew clean build --no-build-cache` from the repository root with
`JAVA_HOME` and `PATH` selecting JDK 25.0.5. This includes unit tests,
platform contracts, release metadata, generated descriptors, and documentation
checks. Freeze the one shaded JAR and its SHA-256 before installing it locally.
Verify readiness, ShopChest enable, build/API/runtime diagnostics, shop audit,
database connections, advertising status, reload, and a clean `stop`. Check
database integrity and preserved shop rows after shutdown. Player trade,
display interaction, and adversarial economy checks remain manual; see the
[beta checklist](../storefront-beta-test.md).

The shared destination is
`/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3/plugins`. **Do not start,
restart, or launch that shared server.** Before replacing JARs, confirm no
server process, open world lock, or listener belongs to that instance. Take a
shared deployment lock, recheck that it is stopped, and back up only JARs whose
manifest identifies `ShopChest` / `de.epiceric.shopchest.ShopChest`. Install the
exact tested artifact with an atomic rename. Check its destination checksum,
ensure no obsolete ShopChest JAR remains active (including the update directory),
and confirm the shared server is still stopped. Never remove other `1MB-*` JARs.

See the [current compatibility record](../verification/paper-cmi-java-2026-10-20.md)
for current evidence. The [alpha upgrade record](../verification/paper-26.3-2026-09-25.md)
is retained as dated history. The [previous stable migration](../verification/paper-26.3-stable-2026-10-11.md)
is also historical; use the current record for build 207 and the updated JDKs.
