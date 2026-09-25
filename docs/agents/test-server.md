# Maintained local test server

The active test instance is `servers/Paper-26.3`, cloned from the stopped
`servers/Paper-26.2` instance. Preserve the entire 26.2 directory for rollback.
Never open its worlds or databases with a newer server.

The new instance targets Paper 26.3 build 41 ALPHA. Java 27 at
`/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home` runs its maintained
`1MB-minecraft.sh` launcher. The launcher selects only the 26.3 JAR family and
requires Java 27. Compilation and the required unit suite use JDK 25.0.4.1;
the plugin's bytecode remains Java 25.

The instance binds to loopback on TCP 28673. Query UDP 28674 and RCON TCP 28675
are reserved but disabled. Its session name is `shopchest-paper-26.3-28673`.
Before starting, check both TCP and UDP availability and other projects' saved
server ports. PaperScript uses this distinct session and server directory,
with both channel selectors set to `ALPHA`. Download exact builds explicitly:

```bash
cd servers/Paper-26.3
python3 paperscript/paperscript.py --server-dir "$PWD" download --version 26.3 --build 41 --channel ALPHA
python3 paperscript/paperscript.py --server-dir "$PWD" verify
./1MB-minecraft.sh
```

Keep external messaging plugins such as DiscordSRV inactive. The smoke instance
uses Vault, CMI, CMILib, LuckPerms, PlaceholderAPI, CoreProtect, WorldEdit, and
WorldGuard. Compatible JARs may be copied from the shared server; preserve the
project-local configuration and data. Copied plugins outside this smoke scope
are retained in `plugins-disabled/copied-paper-26.2/`.

Run `./gradlew clean build --no-build-cache` from the repository root with
`JAVA_HOME` and `PATH` selecting JDK 25.0.4.1. This includes unit tests,
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

See the [upgrade record](../verification/paper-26.3-2026-09-25.md) for exact evidence.
