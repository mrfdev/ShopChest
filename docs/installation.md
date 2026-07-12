# Installation and Updates

## Requirements

- Paper 26.1.2 as the supported live target, or Paper 26.2 for compatibility testing
- Java 25 to run those Paper versions
- Vault
- A Vault-compatible economy provider registered before ShopChest enables

ShopChest's own classes target Java 17 bytecode, but the supported Paper server determines the Java 25 runtime requirement.

## Fresh Installation

1. Stop the server cleanly.
2. Install Vault and the chosen economy plugin in the top-level `plugins/` directory.
3. Place `ShopChest-1.15.0-SNAPSHOT-all.jar` in `plugins/`. Remove older ShopChest jars so only one top-level jar remains.
4. Start the server and verify that ShopChest reports its version without disabling itself.
5. Review `plugins/ShopChest/config.yml` and `hologram-format.yml`.
6. Run `/shop info`, `/shop limits`, and a controlled create/buy/sell test.

ShopChest disables itself when Vault, an economy provider, database access, or a compatible server platform is unavailable.

## Updating

1. Stop the server; do not hot-swap or plugin-reload the jar.
2. Back up `plugins/ShopChest/` and, for MySQL, the configured ShopChest tables.
3. Replace the old jar with the newly built shaded jar. Keep exactly one ShopChest jar in `plugins/`.
4. Preserve `config.yml`, `hologram-format.yml`, language files, and database data.
5. Start the server, watch schema migration messages, and test `/shop info`, `/shop reload`, shop creation, holograms, and both trade directions.

Legacy database migrations create backup tables before converting old unprefixed shop and economy-log schemas. They do not migrate data between database engines.

## Build From Source

From the repository root:

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew build plugin:shadowJar
```

The output is `plugin/build/libs/ShopChest-1.15.0-SNAPSHOT-all.jar`. Local `servers/`, Gradle output, logs, and test-server jars are ignored and must not be committed.

## Compatibility Policy

The project compiles against the 26.1 API line and uses one jar for Paper 26.1.2 and 26.2. Paper 26.2 remains a compatibility-test target until promoted separately. Do not change the main target merely to test an experimental Paper build.
