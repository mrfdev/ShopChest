# Commands

The main command is created from `main-command-name` in `config.yml`; its default is `/shops`. It is registered dynamically rather than declared in `plugin.yml`.

## Player Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops` | Shows the commands available to the sender. | None |
| `/shops info` | Shows the plugin name, introduction, useful commands, installed version, and clickable canonical docs link. | None |
| `/shops create <amount> <buy-price> <sell-price> [normal]` | Selects the held product and starts a 15-second chest selection. A `0` price disables that trade direction. | `shopchest.create`, or the applicable directional/material permissions |
| `/shops limits` | Shows used slots and the effective normal-shop limit. | None |
| `/shops inspect` | Starts a 15-second shop inspection selection. | None |
| `/shops info shop` | Compatibility alias for `/shops inspect`. | None |
| `/shops open` | Starts a 15-second selection to open a shop chest. Owners need no extra permission. | `shopchest.openOther` for another player's shop |
| `/shops remove` | Starts a 15-second selection to remove a shop. Owners need no extra permission. | Elevated permissions apply to other players' and admin shops |

Creation arguments are the number of items per normal trade, the price paid by a buyer, and the price paid to a seller. Prices may be decimal values when enabled. The command validates configured price floors, ceilings, blacklist entries, broken-item policy, shop limit, and creation funds before asking for a chest click.

## Administrative Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops create <amount> <buy-price> <sell-price> admin` | Creates an unlimited-stock admin shop after chest selection. | `shopchest.create.admin` |
| `/shops removeall <player>` | Removes every normal and admin shop owned by the named player. | `shopchest.remove.other` |
| `/shops reload` | Reloads config, language, hologram format, updater state, database connection, and shops in loaded chunks. | `shopchest.reload` |
| `/shops update` | Performs the legacy remote update check. | `shopchest.update` |
| `/shops config set <property> <value>` | Sets a configuration value and reloads in-memory configuration. | `shopchest.config` |
| `/shops config add <property> <value>` | Adds a scalar to a configuration list. | `shopchest.config` |
| `/shops config remove <property> <value>` | Removes a scalar from a configuration list. | `shopchest.config` |

`/shops config set hologram-lift <value>` also moves currently loaded holograms immediately. Settings that affect command registration, database selection, debug-file creation, or startup-only integrations still require a clean server restart.

The automatic startup update checker is disabled in this custom build. The manual `/shops update` command remains present for compatibility, but deployment should use the repository's reviewed releases rather than an automatic download.
