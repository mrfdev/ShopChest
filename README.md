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
| Minecraft / Paper | Paper 26.2 build 84 stable |
| Server runtime | Java 25; Java 26.0.2 compatibility smoke-tested |
| Plugin bytecode | Java 25 |
| Build toolchain | Gradle wrapper with a Java 25 toolchain |
| Required plugins | Vault and a Vault-compatible economy provider |
| Optional price advisory | CMI |
| Plugin version | 1.15.2 |

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
- Separate public Storefront Profiles with safe plain-text name, advertisement,
  description, location hint, and up to three ordered Featured Listings
- Exact base-material `/shops search` with in-stock-only results, four-row
  pagination, out-of-stock/unchecked totals, owner interleaving, and no forced
  chunk loads
- Durable AFK Shrine Token Advertising Passes with exact captured-ItemStack
  currency matching, one-use purchase confirmation, owner/global cooldowns,
  FIFO queueing, and successful-broadcast accounting
- Review-only JSON/CSV marketplace snapshot export for the dated, searchable
  player website; exports never publish themselves
- Read-only shop-health summaries for ready, out-of-stock, full, blocked,
  unavailable, and unloaded shops
- Recent purchase and sale history with earned, spent, and net totals when
  economy logging is enabled
- Staff shop lookup with authorized click-to-teleport rows
- Batched, read-only staff audits for persisted records, container state, and
  conflict/stale candidates without force-loading chunks
- Privacy-conscious support status plus command, permission, and internal
  hologram-placeholder catalogs through `/shops debug`
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
4. The shaded `1MB-ShopChest-v1.15.2-<build>-j25-26.2.jar`.

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
/shops edit amount 16
/shops edit holograms faceme
/shops limits
/shops list
/shops recent
/shops search stone_bricks
/shops profile
/shops advertise
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
| `/shops info` | Shows an introduction, creation steps, version, player-guide link, and a shortcut to shop health. | None |
| `/shops create <amount> <buy-price> <sell-price> [normal]` | Selects the held item and starts a 15-second container selection. | `shopchest.create`, or matching directional/material nodes |
| `/shops edit <amount\|buy\|sell> <value>` | Changes one trade setting after a 15-second selection of an owned shop. | The same directional/material nodes required to create the resulting shop |
| `/shops edit holograms <reset\|faceme\|north\|south\|east\|west>` | Changes the text panel and floating-icon orientation after selecting an owned shop. | The same ownership and admin-shop rules as other edits |
| `/shops limits` | Shows used slots and the effective normal-shop limit. | None |
| `/shops list [page]` | Lists owned shops with whole-list health counts. Hover rows for prices, stock, type, world, and coordinates. | None |
| `/shops recent [page]` | Shows recorded purchases, sales, shop income, spending, and net change. | `shopchest.recent` |
| `/shops search <item> [page]` | Lists four in-stock normal player shops selling the exact base material per page. | `shopchest.search` |
| `/shops profile [player\|uuid] [shops [page]]` | Shows public Storefront Profile text, summary, and paginated listings. | `shopchest.profile` |
| `/shops profile set <name\|advertisement\|description\|location> <text>` | Sets one safe plain-text storefront field. | `shopchest.profile` |
| `/shops profile featured <add\|remove> <shop-id>` | Manages up to three ordered Featured Listings; `featured clear` removes all. | `shopchest.profile` |
| `/shops advertise [pass\|status\|cancel]` | Previews or manages an exact-token Advertising Pass and durable queued request. | `shopchest.advertise` |
| `/shops inspect` | Starts a 15-second shop inspection selection. | None |
| `/shops info shop` | Compatibility alias for `/shops inspect`. | None |
| `/shops open` | Starts a 15-second selection to open a shop container. | `shopchest.openOther` for another player's shop |
| `/shops remove` | Starts a 15-second shop removal selection. | Elevated nodes apply to other players' and admin shops |

### Staff commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops create <amount> <buy-price> <sell-price> admin` | Creates an unlimited-stock admin shop. | `shopchest.create.admin` |
| `/shops admin` | Shows the permitted administration commands. | Any `shopchest.admin.*` action permission |
| `/shops admin list <player> [page]` | Lists a player's shops. In-game rows can teleport authorized staff. | `shopchest.admin.list` |
| `/shops admin audit [player\|all] [page]` | Runs a paginated dry-run maintenance audit over all shops or one player UUID or cached name. | `shopchest.admin.audit` |
| `/shops admin storefront <player> <hide\|show\|suspend\|unsuspend\|clear>` | Moderates storefront text or public discovery independently from shop records. | `shopchest.admin.storefront` |
| `/shops admin advertise currency <status\|capture\|clear>` | Manages the authoritative AFK Shrine Token ItemStack template; no template means purchases fail closed. | `shopchest.admin.advertise` |
| `/shops admin export marketplace` | Writes a review-only public marketplace JSON/CSV snapshot without publishing it. | `shopchest.admin.export` |
| `/shops debug [status]` | Generates a copyable platform, dependency, database, config, translation, and shop-state report. | `shopchest.admin.debug` |
| `/shops debug <commands\|permissions\|placeholders> [page]` | Lists documented runtime commands, declared and dynamic permissions, or internal hologram placeholders. | `shopchest.admin.debug` |
| `/shops admin debug` | Compatibility alias for `/shops debug status`. | `shopchest.admin.debug` |
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

# Find live in-stock sellers of an exact base material
/shops search stone_bricks

# Set and preview a public Storefront Profile
/shops profile set name JahLion's special gear shop!
/shops profile set advertisement Need protection? I sell OP armor and weapons
/shops profile

# Choose the first advertised product, then preview the ad dashboard
/shops profile featured add 123
/shops advertise

# Change one setting on an owned shop, then click it within 15 seconds
/shops edit amount 16
/shops edit buy 75
/shops edit sell 0

# Face both displays toward the side where you stand when clicking the shop
/shops edit holograms faceme

# Use an exact direction, or return to the container's automatic front
/shops edit holograms west
/shops edit holograms reset

# Unlimited admin shop
/shops create 1 100 0 admin

# Show another player's registered shops
/shops admin list mrfloris

# Audit every persisted shop, or only one player UUID or cached name
/shops admin audit
/shops admin audit mrfloris

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
| `shopchest.profile` | `true` | Creates, edits, and views public storefront profiles and Featured Listings. |
| `shopchest.search` | `true` | Searches scoped in-stock player shops by exact base material. |
| `shopchest.advertise` | `true` | Purchases a pass and previews, queues, checks, or cancels a storefront advertisement. |
| `shopchest.admin` | `op` | Parent permission for ShopChest administration. |
| `shopchest.admin.list` | `op` | Lists another player's shops and teleports to an authorized listed shop. |
| `shopchest.admin.audit` | `op` | Runs a read-only maintenance audit without loading shop chunks. Output includes owner UUIDs, world names, and exact coordinates. |
| `shopchest.admin.debug` | `op` | Uses `/shops debug` for support status and metadata catalogs. |
| `shopchest.admin.storefront` | `op` | Moderates public storefront text and suspension state. |
| `shopchest.admin.advertise` | `op` | Captures, checks, or clears the exact advertising currency template. |
| `shopchest.admin.export` | `op` | Creates review-only public marketplace snapshot files. |
| `shopchest.limit.*` | `op` | Removes the normal-shop limit. |

No permission is required for `/shops`, `/shops help`, `/shops info`,
`/shops limits`, `/shops list`, `/shops inspect`, or removing a player's own
normal shops. Editing uses the creation permissions described below.

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

`/shops edit` reuses these creation permissions for the shop's resulting buy
and sell directions. It does not charge another creation fee or consume a shop
slot. Players can edit only shops they own, and editing an admin shop also
requires `shopchest.create.admin`. The product, owner, container, and normal or
admin type cannot be changed through this command.

Display orientation is stored on every physical container block, including
both halves of a double chest, and survives restarts. `faceme` resolves to the
side where the player stands when selecting the shop. The explicit compass
options are useful for tightly arranged market stalls, while `reset` resumes
the container's automatic orientation.

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
- A Java 25 JDK; release builds use JDK 25.0.4
- Network access for the Gradle wrapper and Maven dependencies

The repository includes the Gradle wrapper; a separate Gradle installation is
not required.

```bash
git clone https://github.com/mrfdev/ShopChest.git
cd ShopChest

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.jdk/Contents/Home
java -version
./gradlew --version
./gradlew clean build
```

The build runs the test suite and writes the deployable shaded jar to:

```text
plugin/build/libs/1MB-ShopChest-v1.15.2-<build>-j25-26.2.jar
```

`<build>` is the shared zero-padded release build from `gradle.properties`,
normally aligned with the Git commit count. Release verification permits only
the current commit count or its single pending release increment. The build
disables the ambiguous unshaded jar and assembles only the deployable
`1MB-ShopChest-...jar`. Test reports are available under
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
| `plugins/ShopChest/advertising-currency.yml` | Complete administrator-captured AFK Shrine Token ItemStack template. |
| `plugins/ShopChest/exports/marketplace/` | Manually reviewed marketplace snapshot JSON/CSV output. |

Useful settings:

```text
/shops config set maximal-distance 4
/shops config set hologram-lift 0.25
/shops config set hologram-text-scale 0.50
/shops config set hologram-background-color #315B7D
/shops config set hologram-background-opacity 112
/shops config set hologram-text-opacity 255
/shops config set hologram-text-shadowed false
/shops config set hologram-text-see-through false
/shops config set hologram-text-alignment CENTER
/shops config set hologram-max-item-name-length 40
/shops config set floating-icon-height 1.21
/shops config set floating-icon-scale 0.45
/shops config set floating-icon-bobbing-enabled true
/shops config set floating-icon-bob-amplitude 0.06
/shops config set floating-icon-bob-period-seconds 3.14
/shops config set floating-icon-rotation-enabled true
/shops config set floating-icon-rotation-period-seconds 6.28
/shops config set shop-limits.default 10
```

The bundled display defaults use a `0.50` text scale, `#315B7D` background,
and opacity `112`. Color settings are global server presentation choices;
players cannot customize individual shop colors. All display commands above
save to `config.yml` and update loaded displays immediately. Animation periods
are measured in seconds; increasing a period slows that animation.

`/shops recent` reads database-backed history. New rows are recorded only while
`enable-economy-log` is enabled; disabling it preserves existing history.

`/shops list` and the admin listing do not force-load chunks. Their health line
covers the complete owner result, not only the current page. A loaded normal
shop is out of stock or full when it cannot supply or accept one complete
configured bundle. A loaded shop whose display space is obstructed is blocked.
A world that is unavailable because it is missing or not currently loaded, or
a loaded location without its supported container, makes the shop unavailable.
Unloaded shop chunks, including cross-chunk double containers whose other half
is not loaded, remain unchecked instead of being reported as broken. Reason
counts may overlap, while the attention count includes each affected shop once.
Admin shops have unlimited stock and capacity but can still be blocked or
unavailable.

`/shops admin audit [player|all] [page]` builds a complete, immutable report
snapshot from the shop table and reports malformed owner/type/terms/product
data, unavailable worlds (missing or unloaded), missing containers,
unsupported or incomplete containers, blocked display space, and persisted
record conflicts. Invoking the command without a page number refreshes the
snapshot. Pagination reuses the completed snapshot for up to 60 seconds so
pages cannot drift while staff review them.

Only one audit build can be in flight globally, preventing competing scans on
the live server. The database `SELECT` and non-Bukkit preprocessing run off the
server thread; Bukkit-backed product decoding, already-loaded world/container
inspection, and report finalization are bounded across server ticks, with at
most 25 records processed per phase per tick. The audit checks only chunks that
are already loaded, leaving other records explicitly unchecked. Reason counts
can overlap, while the known issue and review-row totals deduplicate each
record.

Conflict candidates mean multiple records point at one stored or resolved
container, a different loaded shop occupies the location, or a persisted
record is not active in the loaded runtime. These conflict/stale candidates
require manual review and are never proof that a record is safe to delete. The
audit performs no repair, database write, schema migration, chunk load, block
or inventory mutation, PDC update, or configuration change. Use `all`
explicitly when navigating an unfiltered report; the optional player accepts
an online player, UUID, or locally cached name, like `/shops admin list`.

Audit rows contain staff-sensitive owner UUIDs, world names, and exact
coordinates. Review and redact the output before sharing it; unlike
`/shops debug`, the audit is not a privacy-filtered support report.

## Data Safety and Diagnostics

- Back up the entire plugin directory or MySQL tables before every update.
- Do not delete or replace the database while registered shops still exist in
  the worlds.
- Keep only one ShopChest jar in the top-level `plugins/` directory.
- Use `/shops debug` for support reports. It excludes database
  credentials, filesystem paths, player names, world names, and individual
  shop coordinates.
- Use `/shops admin audit` before manual database maintenance. Treat its
  conflict results as investigation leads, not automatic deletion advice.
- Audit output contains owner UUIDs, world names, and exact coordinates. Review
  and redact it before sharing.
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
- [Marketplace snapshot export](docs/marketplace-snapshot.md)
- [Storefront beta test checklist](docs/storefront-beta-test.md)
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
exception or `/shops debug` report where applicable.
