# Commands

The main command is created from `main-command-name` in `config.yml`; its default is `/shops`. It is registered dynamically rather than declared in `plugin.yml`.

## Player Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops` | Shows the commands available to the sender, grouped into player and permitted staff actions. | None |
| `/shops help` | Explicit alias for the same permission-aware command index. | None |
| `/shops info` | Shows a short introduction, numbered shop-creation instructions, installed version, and clickable player-guide link. | None |
| `/shops create <amount> <buy-price> <sell-price> [normal]` | Selects the held product and starts a 15-second chest selection. A `0` price disables that trade direction. | `shopchest.create`, or the applicable directional/material permissions |
| `/shops limits` | Shows used slots and the effective normal-shop limit. | None |
| `/shops list [page]` | Lists every shop owned by the player, including its item, world, and block coordinates. Shop rows do not teleport the player. | None |
| `/shops inspect` | Starts a 15-second shop inspection selection. | None |
| `/shops info shop` | Compatibility alias for `/shops inspect`. | None |
| `/shops open` | Starts a 15-second selection to open a shop chest. Owners need no extra permission. | `shopchest.openOther` for another player's shop |
| `/shops remove` | Starts a 15-second selection to remove a shop. Owners need no extra permission. | Elevated permissions apply to other players' and admin shops |

Creation arguments are the number of items per normal trade, the price paid by a buyer, and the price paid to a seller. Prices may be decimal values when enabled. The command validates configured price floors, ceilings, blacklist entries, broken-item policy, shop limit, and creation funds before asking for a chest click.

Help output and top-level tab completion use the same visibility rules. Player-only commands are omitted for console senders, and staff commands appear only when the sender has their required permission. Staff help lines include the relevant permission node.

## Administrative Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops create <amount> <buy-price> <sell-price> admin` | Creates an unlimited-stock admin shop after chest selection. | `shopchest.create.admin` |
| `/shops admin` | Shows the available ShopChest administration commands. | `shopchest.admin.list` |
| `/shops admin list <player> [page]` | Lists every normal and admin shop registered to a cached player profile. In-game staff can click a shop row to teleport onto the block above its chest; console output remains plain text. | `shopchest.admin.list` |
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
The hidden teleport action accepts only shop IDs from the most recent
authorized admin listing and checks `shopchest.admin.list` again when clicked.
