# Installation and Updates

## Requirements

- Paper 26.2 build 84 stable as the supported and verified live target
- Java 25 to run Paper 26.2; Java 26.0.2 is compatibility smoke-tested
- Vault
- A Vault-compatible economy provider registered before ShopChest enables
- CMI is optional; when present, it enables the configurable worth-price advisory

ShopChest's own classes target Java 25 bytecode for the supported Paper server.

## Fresh Installation

1. Stop the server cleanly.
2. Install Vault and the chosen economy plugin in the top-level `plugins/` directory.
3. Place the generated `1MB-ShopChest-v1.15.2-<build>-j25-26.2.jar` in `plugins/`. Remove older ShopChest jars so only one top-level jar remains.
4. Start the server and verify that ShopChest reports its version without disabling itself.
5. Review `plugins/ShopChest/config.yml` and `hologram-format.yml`.
6. Run `/shops info`, `/shops limits`, and a controlled create/buy/sell test.

ShopChest disables itself when Vault, an economy provider, or database access
is unavailable.

## Updating

1. Stop the server; do not hot-swap or plugin-reload the jar.
2. Back up `plugins/ShopChest/` and, for MySQL, the configured ShopChest tables.
3. Replace the old jar with the newly built shaded jar. Keep exactly one ShopChest jar in `plugins/`.
4. Preserve `config.yml`, `hologram-format.yml`, language files, and database data.
5. Start the server, watch schema migration messages, and test `/shops info`, `/shops reload`, shop creation, holograms, and both trade directions.

Legacy database migrations create backup tables before converting old unprefixed shop and economy-log schemas. They do not migrate data between database engines.

## Build From Source

From the repository root:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.jdk/Contents/Home \
  ./gradlew clean build
```

The output is `plugin/build/libs/1MB-ShopChest-v1.15.2-<build>-j25-26.2.jar`,
where `<build>` is the shared release build in `gradle.properties`. Verification
requires it to match the Git commit count or the single pending release
increment. The unshaded intermediate jar is disabled, leaving one deployable
artifact. Local `servers/`, Gradle output, logs, and test-server jars are ignored
and must not be committed.

## Compatibility Policy

The project compiles against the exact stable Paper API coordinate
`io.papermc.paper:paper-api:26.2.build.84-stable` and uses Paper APIs directly.
It does not support Spigot or older Paper/Minecraft releases. A newer Paper
release is considered compatible only after the platform contract tests, clean
build, and test-server smoke checks pass; there is no version-specific NMS gate
that would reject it preemptively.
