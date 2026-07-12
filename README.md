# ShopChest

ShopChest is 1MoreBlock's chest-shop plugin. Players can attach a persistent shop to a chest, display its product and prices in a hologram, and trade through a Vault-compatible economy.

This repository is a maintained fork of [EpicEricEE/ShopChest](https://github.com/EpicEricEE/ShopChest), based on later compatibility work from [Flowsqy/ShopChest](https://github.com/Flowsqy/ShopChest). The custom build focuses on the current 1MoreBlock Paper targets.

## Compatibility

| Component | Verified target |
| --- | --- |
| Minecraft / Paper | 26.1.2 supported; 26.2 compatibility tested |
| Server runtime | Java 25 for the supported Paper versions |
| Plugin bytecode | Java 17 |
| Build toolchain | Gradle wrapper with a Java 21+ toolchain; Java 25 is supported |
| Required plugins | Vault and a Vault economy provider |

The same shaded plugin jar is intended for Paper 26.1.2 and 26.2. Older Minecraft releases are outside this fork's supported scope.

## Features

- Player and unlimited-stock admin chest shops
- Separate buy and sell prices, with either direction disabled by setting its price to `0`
- Floating product item and configurable hologram text
- Per-player shop limits and per-material creation permissions
- Optional click confirmation, partial transaction calculation, creation costs, and refunds
- SQLite or MySQL persistence with built-in legacy schema migration
- Protection hooks for WorldGuard, Towny, PlotSquared, BentoBox, GriefPrevention, AreaShop, AuthMe, ASkyBlock, uSkyBlock, and IslandWorld
- Optional cross-server vendor notifications through the BungeeCord plugin channel

## Quick Start

1. Install Vault, a Vault-compatible economy plugin, and `ShopChest-1.15.0-SNAPSHOT-all.jar`.
2. Start Paper once, then review `plugins/ShopChest/config.yml` and `hologram-format.yml`.
3. Hold the item to sell, run `/shop create <amount> <buy-price> <sell-price>`, and click a chest within 15 seconds.
4. Run `/shop info` for the installed version, starting commands, and the canonical player guide.

See the [player guide](docs/player-guide.md) for normal use and [installation](docs/installation.md) for the complete server setup.

## Documentation

- [Commands](docs/commands.md)
- [Permissions](docs/permissions.md)
- [Hologram placeholders](docs/placeholders.md)
- [Configuration](docs/configuration.md)
- [Installation and updates](docs/installation.md)
- [Integrations](docs/integrations.md)
- [Troubleshooting](docs/troubleshooting.md)

Canonical public documentation: <https://docs.1moreblock.com/custom-server-plugins/shopchest/>

## Build

The normal build is:

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew build plugin:shadowJar
```

The deployable jar is written to `plugin/build/libs/ShopChest-1.15.0-SNAPSHOT-all.jar`. Gradle includes the Paper NMS module automatically. If a local Spigot NMS aggregate has been prepared, the build also includes it, but that aggregate is not required for the supported Paper targets.

## Persistence and Updates

Shops, optional economy logs, player logout times, and schema metadata are stored in SQLite by default. MySQL is also supported. Back up the complete `plugins/ShopChest/` directory or the configured MySQL database before updating. Do not replace or delete the database while shops exist in the world.

The plugin owns only its source documentation. The central `1MB-Plugins-Docs` repository imports and publishes it; this repository does not build or force-push the public documentation site.

## License and Support

The project is distributed under the license in [LICENSE.txt](LICENSE.txt). Report reproducible source issues at <https://github.com/mrfdev/ShopChest/issues> and include the Paper build, ShopChest version, relevant configuration, and full exception when applicable.
