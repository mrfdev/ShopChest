# Configuration

ShopChest creates `config.yml`, `hologram-format.yml`, and a `lang/` directory under `plugins/ShopChest/`. Keep backups before changing database settings or updating the plugin.

## General and Trading

| Key | Default | Behavior |
| --- | --- | --- |
| `main-command-name` | `shops` | Dynamic root command. Requires a restart to re-register after changing. |
| `language-file` | `en_US` | Selects `messages-<locale>.lang` and `items-<locale>.lang`. |
| `shop-info-item` | `STICK` | Clicking a shop with this item shows details; an empty value disables it. |
| `confirm-shopping` | `false` | Requires a second click before a buy or sell. |
| `trade-interaction-cooldown-milliseconds` | `250` | Silently limits each player to one shop trade attempt per interval before permission, inventory, economy, or database work. Values are clamped from `0` (disabled) through `5000`. |
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

### CMI Worth Price Warnings

When CMI is installed, ShopChest can compare a proposed normal shop's per-item
prices with CMI's loaded `/sell` worth. The check is advisory: it does not
change a price or prevent the shop from being created. Admin shops and products
without a positive CMI worth are skipped silently.

| Key under `cmi-worth-price-warning` | Default | Behavior |
| --- | --- | --- |
| `enabled` | `true` | Enables the optional CMI comparison when CMI and its worth API are available. |
| `warn-resale-risk` | `true` | Warns whenever a customer could buy an item below its CMI `/sell` worth and immediately resell it for profit. |
| `low-multiplier` | `0.50` | Warns when the shop's per-item payout is below this fraction of CMI worth. Clamped from `0.01` through `1.00`. |
| `high-multiplier` | `20.00` | Warns when either per-item price is above this multiple of CMI worth. Clamped from `1.00` through `10000.00`. |

The comparison runs only after the proposed shop passes normal validation. It
uses CMI's in-memory `WorthManager`, makes one metadata-aware lookup for the
held product, and emits at most one customer-price warning and one shop-payout
warning. Shop creation then continues normally. Missing settings are added to
existing configuration files automatically, and `/shops reload` refreshes the
integration and thresholds.

### Trade Feedback

Completed and failed trade attempts use separate, container-local effects. Both the
sound and particles are sent only to the player making the attempt, so nearby
players do not receive noise or effects from unrelated shops.

| Key suffix under `trade-feedback.success` / `trade-feedback.failure` | Success default | Failure default | Behavior |
| --- | --- | --- | --- |
| `enabled` | `true` | `true` | Enables the complete outcome effect. |
| `sound` | `minecraft:entity.experience_orb.pickup` | `minecraft:block.note_block.bass` | Namespaced sound event; use `none` to disable sound. |
| `volume` | `0.45` | `0.35` | Player-local sound volume, clamped from `0` through `2`. |
| `pitch` | `1.20` | `0.70` | Sound pitch, clamped from `0.50` through `2`. |
| `particle` | `minecraft:happy_villager` | `minecraft:smoke` | Data-free namespaced particle; use `none` to disable particles. |
| `particle-count` | `4` | `3` | Particles emitted immediately above the container, clamped from `0` through `16`. |

These settings reload immediately through `/shops config set`, for example
`/shops config set trade-feedback.failure.enabled false`. Confirmation prompts
remain silent; feedback is emitted only after a terminal success or failure.
Modern display, feedback, cooldown, palette, and CMI advisory settings managed
by the built-in config migration are added to existing files automatically.

## Display and Messages

| Key | Default | Behavior |
| --- | --- | --- |
| `only-show-shops-in-sight` | `true` | Shows only the shop being targeted instead of all nearby shop holograms. |
| `hologram-fixed-bottom` | `true` | Anchors the bottom line so extra lines grow upward. |
| `hologram-lift` | `0.25` | Vertical hologram offset in blocks; updates loaded holograms live through `/shops config set hologram-lift <value>`. |
| `hologram-panel-width` | `200` | Maximum width of the unified TextDisplay panel in client font pixels. Text wraps inside the panel. Values are clamped from `40` through `1024`. |
| `hologram-text-scale` | `0.50` | Uniform size of the TextDisplay text and background. Values are clamped from `0.50` through `1.25`, and loaded holograms update immediately. |
| `hologram-background-color` | `#315B7D` | Six-digit hex color used for the unified panel background. The default is a muted, readable blue. |
| `hologram-background-opacity` | `112` | Panel background alpha from `0` (transparent) through `255` (opaque). |
| `hologram-colors.owner` | `#DCE7FF` | Global pastel color for the shop owner name. |
| `hologram-colors.quantity` | `#D8E1EA` | Global pastel color for the trade quantity and `x` marker. |
| `hologram-colors.item` | `#FFE29A` | Global pastel color for the product name. |
| `hologram-colors.details` | `#D8CCFF` | Global pastel lavender for enchantment names, levels, and potion-effect details. |
| `hologram-colors.label` | `#C7D8E5` | Global pastel color for the `Buy:` and `Sell:` labels. |
| `hologram-colors.buy-value` | `#B8EBCB` | Global pastel color for the formatted purchase price. |
| `hologram-colors.sell-value` | `#FFC9B8` | Global pastel color for the formatted sale payout. |
| `hologram-colors.separator` | `#BDD0DE` | Global pastel color for the price-row separator. |
| `hologram-colors.admin` | `#FFC2CF` | Global pastel color for the admin-shop heading. |
| `hologram-colors.unavailable` | `#FFD0D0` | Global pastel color for unavailable trade states such as `[Out of stock]`. |
| `hologram-max-item-name-length` | `48` | Maximum visible characters for literal custom or overridden item names before the panel uses `...`; `0` disables truncation. Vanilla translatable names remain client-resolved and wrap to the configured panel width. |
| `hologram-max-item-detail-entries` | `7` | Maximum enchantments and potion effects shown before a localized `+N more` summary. Values are clamped from `1` through `32`. |
| `hologram-item-details-per-line` | `2` | Enchantment and potion detail entries placed on each panel line. Values are clamped from `1` through `4`. |
| `hologram-fixed-facing` | `true` | Keeps the panel aligned with the front of its container. Set to `false` to restore center billboarding toward each viewer. |
| `maximal-distance` | `2` | Hologram visibility radius in blocks. |
| `maximal-item-distance` | `40` | Floating product visibility radius in blocks. |
| `append-potion-level-to-item-name` | `false` | Adds a Roman-numeral potion level when the product has no custom name. |
| `enable-vendor-messages` | `true` | Notifies online vendors about trades and empty stock. |
| `enable-vendor-bungee-messages` | `false` | Publishes vendor notifications through the BungeeCord plugin channel. |

### Item Name Localization

Vanilla product names are resolved automatically from the runtime
`ItemStack.translationKey()`. Holograms send an Adventure translatable
component to the client, allowing Minecraft or the active resource pack to
localize the name. ShopChest also supplies a readable fallback for plain-text
contexts and clients that do not know a future key. A new vanilla item therefore
does not require a generated language-file update.

`lang/items-<locale>.lang` is an optional administrator override file. New
installations receive an empty, commented template. Existing files remain
compatible and may use a full translation key, namespaced item key, simple key,
or Bukkit material name:

```properties
block.minecraft.oak_log=Timber
minecraft:oak_log=Timber
oak_log=Timber
OAK_LOG=Timber
```

Custom item names take precedence over these overrides. Blank values and known
failure sentinels such as `ERROR`, `unknown item`, and `not configured` are
ignored rather than displayed. Startup logs and `/shops admin debug` report
runtime translation-key coverage, loaded override count, and invalid override
count.

`hologram-format.yml` defines ordered line options, conditions, colors, and internal placeholders. All active lines are rendered in one TextDisplay panel, so wrapped custom item names cannot overlap the owner or price lines. Enchanted items show their enchantments and levels; potions show all base and custom effects with amplifiers and non-instant durations. Details retain client-side Minecraft translations, use the global item/separator colors, wrap at two entries per line by default, and show at most seven entries plus an overflow summary. Normal shops that cannot supply one complete configured purchase replace the buy price with the localized `[Out of stock]` state while retaining an independently enabled sell price; admin shops remain unlimited. The `hologram-colors` palette is server-wide and cannot be overridden by players. Missing modern display keys are added to existing configuration files automatically, and invalid values fall back to the documented default with a console warning. See [Hologram Placeholders](placeholders.md).

## Safety, Logging, and Maintenance

| Key | Default | Behavior |
| --- | --- | --- |
| `enable-economy-log` | `false` | Stores completed buy/sell transactions for `/shops recent` and vendor revenue reporting. Disabling it stops new history without deleting existing rows. |
| `cleanup-economy-log-days` | `30` | Deletes older economy logs at startup; `0` disables cleanup. This also controls how far back `/shops recent` can report. |
| `enable-debug-log` | `false` | Writes verbose diagnostics to `plugins/ShopChest/debug.txt`; restart after changing. |
| `remove-shop-on-error` | `false` | Deletes a database record when its world, container, or display space cannot be loaded. Keep disabled while diagnosing recoverable world-loading issues. |

The former `enable-update-checker` key is obsolete. Existing configuration
files may retain it harmlessly, but new files omit it and ShopChest never makes
remote update requests.

## Protection Integrations

The `enable-*-integration` flags control WorldGuard, Towny, AuthMe, PlotSquared, uSkyBlock, ASkyBlock, BentoBox, IslandWorld, GriefPrevention, and AreaShop hooks. Integrations and custom flags are registered during startup, so restart after changing these values or the installed plugin set.

`worldguard-default-flag-values` sets defaults for `create-shop`, `use-shop`, and `use-admin-shop`. `towny-shop-plots` lists allowed plot types by resident, mayor, and king roles. `areashop-remove-shops` selects the AreaShop lifecycle events that remove shops; valid values are `DELETE`, `UNRENT`, `RESELL`, and `SELL`.

## Database

`database.type` accepts exactly `SQLite` or `MySQL`. `database.table-prefix` defaults to `shopchest_` and may contain only letters, numbers, dashes, and underscores. SQLite stores its file in the plugin data folder. MySQL requires `hostname`, `port`, `database`, `username`, and `password`; `ping-interval` defaults to 3600 seconds and `0` disables keepalive pings.

Database selection, connection details, and table prefix should be changed only while the server is stopped. ShopChest reconnects during `/shops reload`, but moving existing data between SQLite and MySQL is not an automatic migration.

## Reload Versus Restart

`/shops reload` reloads normal configuration values, language files, the hologram format, CMI worth-price thresholds, shop visibility tasks, database connection, and shops in loaded chunks. Use a full restart for `main-command-name`, database backend changes, debug-file creation, integration registration, or plugin dependency changes. Display and positioning settings changed through `/shops config set` refresh loaded holograms immediately, including any `hologram-colors.*` value.

`/shops admin debug` is independent of `enable-debug-log`. It collects a
bounded, privacy-conscious runtime snapshot on demand, while the debug log is a
verbose file intended only for deeper temporary troubleshooting.
