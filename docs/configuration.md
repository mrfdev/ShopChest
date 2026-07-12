# Configuration

ShopChest creates `config.yml`, `hologram-format.yml`, and a `lang/` directory under `plugins/ShopChest/`. Keep backups before changing database settings or updating the plugin.

## General and Trading

| Key | Default | Behavior |
| --- | --- | --- |
| `main-command-name` | `shop` | Dynamic root command. Requires a restart to re-register after changing. |
| `language-file` | `en_US` | Selects `messages-<locale>.lang` and `items-<locale>.lang`. |
| `shop-info-item` | `STICK` | Clicking a shop with this item shows details; an empty value disables it. |
| `confirm-shopping` | `false` | Requires a second click before a buy or sell. |
| `creative-select-item` | `true` | Lets a creator select a product from the creative inventory when no item is held. |
| `refund-shop-creation` | `false` | Refunds the current creation price when the creator removes their own shop. |
| `auto-calculate-item-amount` | `false` | Reduces a trade and price when money, stock, items, or space are insufficient. Active only when decimal prices are allowed. |
| `allow-decimals-in-price` | `true` | Accepts decimal command prices. |
| `allow-broken-items` | `false` | Allows damaged damageable products. |
| `buy-greater-or-equal-sell` | `true` | Prevents a shop's buy-from-shop price from being below its sell-to-shop payout. |
| `invert-mouse-buttons` | `false` | Swaps the default right-click buy and left-click sell actions. |
| `minimum-prices` / `maximum-prices` | Empty | Per-material unit-price boundaries. |
| `blacklist` | Empty | Bukkit materials that cannot become shop products. |

`shop-creation-price.normal` defaults to `5`; `shop-creation-price.admin` defaults to `0`. `shop-limits.default` defaults to `5`, and `-1` disables the default limit. Permission limits override it.

## Display and Messages

| Key | Default | Behavior |
| --- | --- | --- |
| `only-show-shops-in-sight` | `true` | Shows only the shop being targeted instead of all nearby shop holograms. |
| `hologram-fixed-bottom` | `true` | Anchors the bottom line so extra lines grow upward. |
| `hologram-lift` | `0.25` | Vertical hologram offset in blocks; updates loaded holograms live through `/shop config set hologram-lift <value>`. |
| `maximal-distance` | `2` | Hologram visibility radius in blocks. |
| `maximal-item-distance` | `40` | Floating product visibility radius in blocks. |
| `append-potion-level-to-item-name` | `false` | Adds a Roman-numeral potion level when the product has no custom name. |
| `enable-vendor-messages` | `true` | Notifies online vendors about trades and empty stock. |
| `enable-vendor-bungee-messages` | `false` | Publishes vendor notifications through the BungeeCord plugin channel. |

Item translations are loaded from `lang/items-<locale>.lang`. Missing entries fall back to readable names generated from the modern Bukkit material/item type rather than displaying an error.

`hologram-format.yml` defines ordered line options, conditions, colors, and internal placeholders. See [Hologram Placeholders](placeholders.md).

## Safety, Logging, and Maintenance

| Key | Default | Behavior |
| --- | --- | --- |
| `enable-economy-log` | `false` | Stores completed buy/sell transactions. |
| `cleanup-economy-log-days` | `30` | Deletes older economy logs at startup; `0` disables cleanup. |
| `enable-debug-log` | `false` | Writes verbose diagnostics to `plugins/ShopChest/debug.txt`; restart after changing. |
| `remove-shop-on-error` | `false` | Deletes a database record when its world, chest, or display space cannot be loaded. Keep disabled while diagnosing recoverable world-loading issues. |
| `enable-update-checker` | `true` | Retained for compatibility, but automatic startup checks are disabled in this custom build. |

## Protection Integrations

The `enable-*-integration` flags control WorldGuard, Towny, AuthMe, PlotSquared, uSkyBlock, ASkyBlock, BentoBox, IslandWorld, GriefPrevention, and AreaShop hooks. Integrations and custom flags are registered during startup, so restart after changing these values or the installed plugin set.

`worldguard-default-flag-values` sets defaults for `create-shop`, `use-shop`, and `use-admin-shop`. `towny-shop-plots` lists allowed plot types by resident, mayor, and king roles. `areashop-remove-shops` selects the AreaShop lifecycle events that remove shops; valid values are `DELETE`, `UNRENT`, `RESELL`, and `SELL`.

## Database

`database.type` accepts exactly `SQLite` or `MySQL`. `database.table-prefix` defaults to `shopchest_` and may contain only letters, numbers, dashes, and underscores. SQLite stores its file in the plugin data folder. MySQL requires `hostname`, `port`, `database`, `username`, and `password`; `ping-interval` defaults to 3600 seconds and `0` disables keepalive pings.

Database selection, connection details, and table prefix should be changed only while the server is stopped. ShopChest reconnects during `/shop reload`, but moving existing data between SQLite and MySQL is not an automatic migration.

## Reload Versus Restart

`/shop reload` reloads normal configuration values, language files, the hologram format, updater tasks, database connection, and shops in loaded chunks. Use a full restart for `main-command-name`, database backend changes, debug-file creation, integration registration, or plugin dependency changes. Existing holograms move immediately only when `hologram-lift` is changed through the config command.
