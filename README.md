# ShopChest

ShopChest is 1MoreBlock's persistent container-shop plugin for Paper. Players
attach a shop to a supported storage container, stock it with an exact item
variant, and trade through a Vault-compatible economy. Each shop has a compact
TextDisplay panel and a floating, rotating ItemDisplay showing the product.

This repository is a maintained fork of
[EpicEricEE/ShopChest](https://github.com/EpicEricEE/ShopChest), based on later
compatibility work from
[Flowsqy/ShopChest](https://github.com/Flowsqy/ShopChest). It is maintained as a
Paper-first 1MoreBlock build.

Player-facing documentation is published at
<https://docs.1moreblock.com/player-guides/custom-server-plugins/shopchest/>.

## Compatibility

| Component | Supported target |
| --- | --- |
| Minecraft / Paper | Paper 26.2 build 60 beta |
| Server runtime | Java 25 |
| Plugin bytecode | Java 25 |
| Build toolchain | Gradle wrapper with a Java 25 toolchain |
| Required plugins | Vault and a Vault-compatible economy provider |
| Optional price advisory | CMI |
| Plugin version | 1.15.1 |

The exact compile target is declared in
[`plugin/build.gradle.kts`](plugin/build.gradle.kts). The deployable jar uses
Paper APIs directly and does not contain version-specific NMS, CraftBukkit
reflection, or legacy Spigot compatibility modules.

Spigot, Paper 26.1.x, and older Minecraft releases are not supported. A newer
Paper release is considered compatible only after a clean build, contract
tests, test-server startup, and focused shop testing pass.

## Features

### Shops and trading

- Normal player shops and unlimited-stock admin shops
- Independent buy and sell prices; setting either price to `0` disables that
  trade direction
- Normal chests, trapped chests, barrels, every dyed and undyed shulker box,
  and every vanilla copper chest oxidation and waxed variant
- Exact item matching that retains custom names, enchantments, potion data, and
  other item metadata
- Complete-bundle stock checks, including an `[Out of stock]` display when a
  shop cannot supply its configured trade amount
- Configurable creation cost, refunds, price bounds, confirmation clicks,
  automatic amount calculation, and a default 250 ms interaction cooldown
- Per-player shop limits and optional material-specific creation permissions
- Player-local success and failure sounds and particles
- Optional CMI worth advisory for unusually high, low, or directly resellable
  prices; warnings never change or block the entered price

### Displays and item names

- One modern Paper `TextDisplay` panel with configurable lift, width, scale,
  background color, background opacity, orientation, and semantic pastel colors
- A separate floating and rotating Paper `ItemDisplay` product icon
- Client-localized vanilla item names from Paper/Adventure translation keys, so
  future vanilla items do not require a manually generated name list
- Custom item names remain visible; configured locale overrides remain
  available for intentional server-specific naming
- Enchantment names and levels, including stored enchanted-book enchantments
- Potion effect names, amplifiers, and durations
- Compact wrapping and truncation for long names and detail-heavy products
- Per-player display visibility with configurable distance and line-of-sight
  checks

### Management and reliability

- Player shop lists, stock state, locations, and compact hover details
- Recent purchase and sale history with earned, spent, and net totals when
  economy logging is enabled
- Staff shop lookup with authorized click-to-teleport rows
- Privacy-conscious support diagnostics through `/shops admin debug`
- SQLite by default or MySQL for shared/networked storage
- Built-in legacy schema migration and database-backed unloaded-chunk lookup
- Protection hooks for WorldGuard, Towny, PlotSquared, BentoBox,
  GriefPrevention, AreaShop, AuthMe, ASkyBlock, uSkyBlock, and IslandWorld
- Optional BungeeCord plugin-channel vendor notifications
- No bStats metrics collection and no remote update checker

Unsupported containers include ender chests, hoppers, furnaces, and other
inventory blocks not listed above.

## Installation

### Requirements

1. Paper 26.2 running on Java 25.
2. Vault.
3. A Vault-compatible economy plugin registered before ShopChest enables.
4. The shaded `1MB-ShopChest-v1.15.1-<build>-j25-26.2.jar`.

Vault alone does not provide an economy. ShopChest disables itself when Vault,
an economy provider, or its configured database is unavailable.

### Fresh installation

1. Stop the server cleanly.
2. Put Vault and the chosen economy provider in the server's top-level
   `plugins/` directory.
3. Put the shaded ShopChest jar in `plugins/`. Remove older ShopChest jars so
   exactly one top-level ShopChest jar remains.
4. Start the server and confirm that ShopChest enables without an exception.
5. Review `plugins/ShopChest/config.yml` and
   `plugins/ShopChest/hologram-format.yml`.
6. Run `/shops info`, `/shops limits`, and a controlled create, buy, and sell
   test before opening the server to players.

Do not hot-swap ShopChest or use a plugin manager to reload its jar. Use a clean
server stop and start.

### Updating

1. Stop the server.
2. Back up the complete `plugins/ShopChest/` directory. For MySQL, also back up
   the configured ShopChest tables.
3. Replace the old jar and keep exactly one ShopChest jar in `plugins/`.
4. Preserve `config.yml`, `hologram-format.yml`, language files, and database
   data.
5. Start the server, watch the migration and enable messages, then test
   `/shops info`, `/shops reload`, existing displays, shop creation, and both
   trade directions.

Modern display, feedback, cooldown, palette, and CMI advisory settings managed
by the built-in config migration are added without overwriting existing values.
Review newly added settings after an update. Database migrations can create
backup tables, but do not migrate data between SQLite and MySQL.

## Player Quick Start

1. Place a supported container with open space directly above every block it
   occupies.
2. Hold the exact item variant the shop should trade.
3. Run `/shops create <amount> <buy-price> <sell-price>`.
4. Click the container within 15 seconds.
5. Put products in the container so players can buy from it. Leave enough empty
   capacity so players can sell products to it.

Example:

```text
/shops create 8 40 20
```

This shop trades 8 held items at a time:

- A customer pays `40` to buy 8 items from the shop.
- A customer receives `20` for selling 8 items to the shop.

Set a price to `0` to disable that side:

```text
/shops create 16 100 0
/shops create 64 0 25
```

The first shop only sells bundles to customers. The second only buys bundles
from customers.

By default, right-click buys from a shop and left-click sells to it. Sneaking
while clicking trades up to one full item stack instead of one configured
bundle. Server configuration can invert or otherwise adjust these controls.

Useful player commands:

```text
/shops info
/shops limits
/shops list
/shops recent
/shops inspect
/shops open
/shops remove
```

## Commands

The main command is read from `main-command-name` in `config.yml` and defaults
to `/shops`. It is registered dynamically, so changing the command name
requires a clean server restart.

### Player commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops` | Shows the permission-aware command index. | None |
| `/shops help` | Shows the same player and permitted staff command index. | None |
| `/shops info` | Shows an introduction, creation steps, version, and player-guide link. | None |
| `/shops create <amount> <buy-price> <sell-price> [normal]` | Selects the held item and starts a 15-second container selection. | `shopchest.create`, or matching directional/material nodes |
| `/shops limits` | Shows used slots and the effective normal-shop limit. | None |
| `/shops list [page]` | Lists owned shops. Hover rows for prices, stock, type, world, and coordinates. | None |
| `/shops recent [page]` | Shows recorded purchases, sales, shop income, spending, and net change. | `shopchest.recent` |
| `/shops inspect` | Starts a 15-second shop inspection selection. | None |
| `/shops info shop` | Compatibility alias for `/shops inspect`. | None |
| `/shops open` | Starts a 15-second selection to open a shop container. | `shopchest.openOther` for another player's shop |
| `/shops remove` | Starts a 15-second shop removal selection. | Elevated nodes apply to other players' and admin shops |

### Staff commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops create <amount> <buy-price> <sell-price> admin` | Creates an unlimited-stock admin shop. | `shopchest.create.admin` |
| `/shops admin` | Shows the permitted administration commands. | `shopchest.admin.list` or `shopchest.admin.debug` |
| `/shops admin list <player> [page]` | Lists a player's shops. In-game rows can teleport authorized staff. | `shopchest.admin.list` |
| `/shops admin debug` | Generates a copyable platform, dependency, database, config, translation, and shop-state report. | `shopchest.admin.debug` |
| `/shops removeall <player>` | Removes all normal and admin shops owned by a player. | `shopchest.remove.other` |
| `/shops reload` | Reloads config, language, hologram format, database connection, visibility tasks, and loaded shops. | `shopchest.reload` |
| `/shops config set <property> <value>` | Sets a scalar configuration value. | `shopchest.config` |
| `/shops config add <property> <value>` | Adds a scalar to a configuration list. | `shopchest.config` |
| `/shops config remove <property> <value>` | Removes a scalar from a configuration list. | `shopchest.config` |

### Command examples

```text
# Sell 8 held items to customers for 40 and buy them back for 20
/shops create 8 40 20

# Customer-buy-only shop
/shops create 16 100 0

# Customer-sell-only shop
/shops create 64 0 25

# Unlimited admin shop
/shops create 1 100 0 admin

# Show another player's registered shops
/shops admin list mrfloris

# Use a four-block hologram visibility distance
/shops config set maximal-distance 4

# Set the default normal-shop limit
/shops config set shop-limits.default 10
```

Display settings such as `hologram-text-scale`, `hologram-lift`, and
`maximal-distance` update loaded displays immediately. Command registration,
database selection, debug-file creation, and startup integrations require a
clean restart.

## Permissions

Defaults come from `plugin.yml`. `true` means granted to all players and `op`
means granted to server operators by default.

| Permission | Default | Effect |
| --- | --- | --- |
| `shopchest.*` | `op` | Grants all declared ShopChest permissions, including unlimited shops. |
| `shopchest.create` | `true` | Creates normal shops and grants both directional creation nodes. |
| `shopchest.create.buy` | `true` | Creates shops that sell products to customers. |
| `shopchest.create.sell` | `true` | Creates shops that buy products from customers. |
| `shopchest.create.admin` | `op` | Creates unlimited-stock admin shops. |
| `shopchest.create.protected` | `op` | Bypasses a cancelled shop-creation protection event. |
| `shopchest.buy` | `true` | Buys products from shops. |
| `shopchest.sell` | `true` | Sells products to shops. |
| `shopchest.remove.other` | `op` | Removes another player's shops and uses `/shops removeall`. |
| `shopchest.remove.admin` | `op` | Removes admin shops. |
| `shopchest.openOther` | `op` | Opens another player's shop inventory. The capital `O` is required. |
| `shopchest.reload` | `op` | Uses `/shops reload`. |
| `shopchest.config` | `op` | Changes configuration through `/shops config`. |
| `shopchest.extend.other` | `op` | Extends another player's shop into a double chest. |
| `shopchest.extend.protected` | `op` | Extends a shop into a protected location. |
| `shopchest.external.bypass` | `op` | Bypasses an integrated claim, region, plot, or island denial. |
| `shopchest.recent` | `true` | Views the player's recorded transaction history. |
| `shopchest.admin` | `op` | Parent permission for ShopChest administration. |
| `shopchest.admin.list` | `op` | Lists another player's shops and teleports to an authorized listed shop. |
| `shopchest.admin.debug` | `op` | Generates the ShopChest support report. |
| `shopchest.limit.*` | `op` | Removes the normal-shop limit. |

No permission is required for `/shops`, `/shops help`, `/shops info`,
`/shops limits`, `/shops list`, `/shops inspect`, or managing a player's own
normal shops.

### Dynamic shop limits

`shopchest.limit.<number>` assigns a normal-shop limit. When several numeric
limits apply, the highest value wins. A negative limit or
`shopchest.limit.*` means unlimited. Admin shops do not count toward the limit.

LuckPerms examples:

```text
/lp group vip permission set shopchest.limit.20 true
/lp group admin permission set shopchest.limit.* true
```

The configuration fallback is `shop-limits.default`, which defaults to `5`.
Players can see their effective value with `/shops limits`.

### Material-specific creation

Servers that revoke the broad creation nodes can grant exact materials:

```text
shopchest.create.<MATERIAL>
shopchest.create.<MATERIAL>.<durability>
shopchest.create.buy.<MATERIAL>[.<durability>]
shopchest.create.sell.<MATERIAL>[.<durability>]
```

Material names use Bukkit enum names such as `DIAMOND` and `OAK_LOG`. The broad
`shopchest.create` node overrides material-specific requirements.

## Hologram Placeholders

These placeholders belong only to
`plugins/ShopChest/hologram-format.yml`. ShopChest does not register a
PlaceholderAPI expansion.

| Placeholder | Output |
| --- | --- |
| `%VENDOR%` | Current shop-owner name. |
| `%AMOUNT%` | Number of products in one configured trade. |
| `%ITEMNAME%` | Localized vanilla name or preserved custom item name. |
| `%ITEM-DETAILS%` | Combined multiline enchantment and potion details. |
| `%ENCHANTMENT%` | Multiline enchantment names and levels, including enchanted-book entries. |
| `%POTION-EFFECT%` | Multiline potion effects with amplifier and duration where applicable. |
| `%BUY-PRICE%` | Customer purchase price, or `[Out of stock]` when a full bundle is unavailable. |
| `%SELL-PRICE%` | Amount a customer receives for selling a bundle. |
| `%STOCK%` | Matching products currently stored in a normal shop container. |
| `%MAX-STACK%` | Product maximum stack size. |
| `%CHEST-SPACE%` | Number of matching products that can still fit in the shop container. |
| `%DURABILITY%` | Legacy durability value stored for the product. |
| `%COLOR-OWNER%` | Global owner color. |
| `%COLOR-QUANTITY%` | Global quantity color. |
| `%COLOR-ITEM%` | Global product-name color. |
| `%COLOR-LABEL%` | Global price-label color. |
| `%COLOR-BUY-VALUE%` | Global customer-buy price color. |
| `%COLOR-SELL-VALUE%` | Global customer-sell value color. |
| `%COLOR-SEPARATOR%` | Global separator color. |
| `%COLOR-ADMIN%` | Global admin-shop value color. |
| `%COLOR-UNAVAILABLE%` | Global unavailable/out-of-stock color. |
| `%COLOR-RESET%` | Clears the active color and text decoration. |

`%ITEM-DETAILS%`, `%ENCHANTMENT%`, and `%POTION-EFFECT%` are Adventure
components so Minecraft can preserve client-side translations. The legacy name
`%CHEST-SPACE%` applies to every supported shop container.

Format lines support ordered conditions and numeric braced expressions. See
[the placeholder reference](docs/placeholders.md) for requirements,
calculations, and layout examples. Run `/shops reload` after editing
`hologram-format.yml`.

## Build From Source

### Prerequisites

- Git
- A Java 25 JDK
- Network access for the Gradle wrapper and Maven dependencies

The repository includes the Gradle wrapper; a separate Gradle installation is
not required.

```bash
git clone https://github.com/mrfdev/ShopChest.git
cd ShopChest

export JAVA_HOME=/path/to/jdk-25
java -version
./gradlew --version
./gradlew clean build :plugin:shadowJar
```

The build runs the test suite and writes the deployable shaded jar to:

```text
plugin/build/libs/1MB-ShopChest-v1.15.1-<build>-j25-26.2.jar
```

`<build>` is the zero-padded Git commit count. Deploy the `1MB-ShopChest-...jar`,
not an unshaded intermediate artifact. Test reports are available under
`plugin/build/reports/tests/test/`.

Local `servers/`, Gradle output, logs, and test-server jars are ignored and must
not be committed.

## Configuration and Operations

Primary files:

| File | Purpose |
| --- | --- |
| `plugins/ShopChest/config.yml` | Commands, prices, limits, controls, displays, database, integrations, and feedback. |
| `plugins/ShopChest/hologram-format.yml` | TextDisplay lines, placeholders, conditions, and calculations. |
| `plugins/ShopChest/lang/` | Messages and intentional display-name overrides. |
| `plugins/ShopChest/shopchest.db` | Default SQLite data store. |

Useful settings:

```text
/shops config set maximal-distance 4
/shops config set hologram-lift 0.25
/shops config set hologram-text-scale 0.50
/shops config set hologram-background-color #315B7D
/shops config set hologram-background-opacity 112
/shops config set hologram-max-item-name-length 40
/shops config set shop-limits.default 10
```

The bundled display defaults use a `0.50` text scale, `#315B7D` background,
and opacity `112`. Color settings are global server presentation choices;
players cannot customize individual shop colors.

`/shops recent` reads database-backed history. New rows are recorded only while
`enable-economy-log` is enabled; disabling it preserves existing history.

`/shops list` and the admin listing do not force-load chunks. Loaded normal
shops report current stock, unloaded shops report unknown stock, and admin
shops report unlimited stock.

## Data Safety and Diagnostics

- Back up the entire plugin directory or MySQL tables before every update.
- Do not delete or replace the database while registered shops still exist in
  the worlds.
- Keep only one ShopChest jar in the top-level `plugins/` directory.
- Use `/shops admin debug` for support reports. It excludes database
  credentials, filesystem paths, player names, world names, and individual
  shop coordinates.
- A clean restart is required after changing the main command, database engine,
  debug-file creation, or startup-only integrations.
- Test high-risk updates with existing shops, unloaded chunks, full inventories,
  insufficient stock, insufficient container space, and rapid interaction.

## Integrations

Vault and an economy provider are mandatory. Optional integrations are
documented in [docs/integrations.md](docs/integrations.md), including CMI worth
advisories and supported protection, world, authentication, and proxy hooks.

ShopChest does not integrate with mcMMO and does not expose PlaceholderAPI
placeholders. Its display entities are Paper display entities, not players.

## Documentation

- [Player guide](docs/player-guide.md)
- [Installation and updates](docs/installation.md)
- [Commands](docs/commands.md)
- [Permissions](docs/permissions.md)
- [Hologram placeholders](docs/placeholders.md)
- [Configuration](docs/configuration.md)
- [Integrations](docs/integrations.md)
- [Troubleshooting](docs/troubleshooting.md)

The source documentation in this repository is imported into the central
1MoreBlock documentation site. This repository does not build or force-push
that public site.

## License, Credits, and Support

The project is distributed under the license in [LICENSE.txt](LICENSE.txt).
Credit remains with the original ShopChest authors and compatibility forks
linked above.

Report reproducible source issues at
<https://github.com/mrfdev/ShopChest/issues>. Include the Paper build,
ShopChest artifact name, relevant configuration, reproduction steps, and full
exception or `/shops admin debug` report where applicable.
