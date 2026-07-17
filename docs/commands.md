# Commands

The main command is created from `main-command-name` in `config.yml`; its default is `/shops`. It is registered dynamically rather than declared in `plugin.yml`.

## Player Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops` | Shows the commands available to the sender, grouped into player and permitted staff actions. | None |
| `/shops help` | Explicit alias for the same permission-aware command index. | None |
| `/shops info` | Shows a short introduction, numbered shop-creation instructions, installed version, and clickable player-guide link. | None |
| `/shops create <amount> <buy-price> <sell-price> [normal]` | Selects the held product and starts a 15-second supported-container selection. A `0` price disables that trade direction. | `shopchest.create`, or the applicable directional/material permissions |
| `/shops limits` | Shows used slots and the effective normal-shop limit. | None |
| `/shops list [page]` | Lists every shop owned by the player using compact rows. Hover a row for prices, stock, type, world, and coordinates. Shop rows do not teleport the player. | None |
| `/shops recent [page]` | Shows recent purchases and sales made by the player, plus trades completed at the player's normal shops. Each page includes money earned, spent, and net change. | `shopchest.recent` (granted by default) |
| `/shops inspect` | Starts a 15-second shop inspection selection. | None |
| `/shops info shop` | Compatibility alias for `/shops inspect`. | None |
| `/shops open` | Starts a 15-second selection to open a shop container. Owners need no extra permission. | `shopchest.openOther` for another player's shop |
| `/shops remove` | Starts a 15-second selection to remove a shop. Owners need no extra permission. | Elevated permissions apply to other players' and admin shops |

Creation arguments are the number of items per normal trade, the price paid by a buyer, and the price paid to a seller. Prices may be decimal values when enabled. The command validates configured price floors, ceilings, blacklist entries, broken-item policy, shop limit, and creation funds before asking for a supported-container click.

Help output and top-level tab completion use the same visibility rules. Player-only commands are omitted for console senders, and staff commands appear only when the sender has their required permission. Staff help lines include the relevant permission node.

## Administrative Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops create <amount> <buy-price> <sell-price> admin` | Creates an unlimited-stock admin shop after supported-container selection. | `shopchest.create.admin` |
| `/shops admin` | Shows the ShopChest administration commands permitted for the sender. | `shopchest.admin.list` or `shopchest.admin.debug` |
| `/shops admin list <player> [page]` | Lists every normal and admin shop registered to a cached player profile. In-game staff get the same detailed hover and can click a row to teleport onto the block above its container; console rows retain plain-text coordinates. | `shopchest.admin.list` |
| `/shops admin debug` | Collects a support snapshot covering the artifact target, Paper/Java runtime, platform, dependencies and hooks, database health and counts, runtime item translation-key coverage, loaded shop displays, stock state, and relevant configuration. Players can click to copy the full report; console receives plain text. | `shopchest.admin.debug` |
| `/shops removeall <player>` | Removes every normal and admin shop owned by the named player. | `shopchest.remove.other` |
| `/shops reload` | Reloads config, language, hologram format, shop visibility tasks, database connection, and shops in loaded chunks. | `shopchest.reload` |
| `/shops config set <property> <value>` | Sets a configuration value and reloads in-memory configuration. | `shopchest.config` |
| `/shops config add <property> <value>` | Adds a scalar to a configuration list. | `shopchest.config` |
| `/shops config remove <property> <value>` | Removes a scalar from a configuration list. | `shopchest.config` |

Display settings such as `/shops config set hologram-text-scale 0.50` and positioning settings such as `/shops config set hologram-lift 0.25` update currently loaded holograms immediately. Settings that affect command registration, database selection, debug-file creation, or startup-only integrations still require a clean server restart.

This custom fork does not perform remote update checks. Deploy reviewed builds
from the project repository through the normal server maintenance process.

Shop listing is database-backed, so it includes registered shops in unloaded
chunks. Results are sorted by world and coordinates and shown eight per page.
Player chat uses compact item rows with location, prices, type, and stock in a
hover tooltip. A loaded normal shop that cannot supply one complete configured
purchase is visibly marked `[Out of stock]`. Unloaded chunks are not forced to
load for this command, so their tooltip honestly reports stock as unknown until
the chunk is loaded. Admin shops report unlimited stock, and sell-only shops
report that sale stock is not applicable.
The hidden teleport action accepts only shop IDs from the most recent
authorized admin listing and checks `shopchest.admin.list` again when clicked.

The diagnostics query runs asynchronously and does not load shop chunks. Its
copyable report excludes database credentials, filesystem paths, player names,
world names, and individual shop locations. Registered and loaded totals can
legitimately differ because shops in unloaded chunks remain in the database.

Recent activity is also database-backed and shown eight transactions per page,
newest first. It distinguishes purchases and sales made by the player from
customer activity at the player's normal shops. Admin-shop activity is included
for the customer, but it does not report fictional earnings or spending for the
admin-shop owner. Each transaction uses one compact chat row; hovering it shows
the full timestamp, other party, item quantity, signed balance change,
calculated per-item price, and original shop location. New history is recorded
only while `enable-economy-log` is enabled; disabling it preserves any existing
rows and `/shops recent` warns that only previously recorded activity is
available.
