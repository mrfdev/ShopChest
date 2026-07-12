# Commands

The main command is created from `main-command-name` in `config.yml`; its default is `/shop`. It is registered dynamically rather than declared in `plugin.yml`.

## Player Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shop` | Shows the commands available to the sender. | None |
| `/shop info` | Shows the plugin name, introduction, useful commands, installed version, and clickable canonical docs link. | None |
| `/shop create <amount> <buy-price> <sell-price> [normal]` | Selects the held product and starts a 15-second chest selection. A `0` price disables that trade direction. | `shopchest.create`, or the applicable directional/material permissions |
| `/shop limits` | Shows used slots and the effective normal-shop limit. | None |
| `/shop inspect` | Starts a 15-second shop inspection selection. | None |
| `/shop info shop` | Compatibility alias for `/shop inspect`. | None |
| `/shop open` | Starts a 15-second selection to open a shop chest. Owners need no extra permission. | `shopchest.openOther` for another player's shop |
| `/shop remove` | Starts a 15-second selection to remove a shop. Owners need no extra permission. | Elevated permissions apply to other players' and admin shops |

Creation arguments are the number of items per normal trade, the price paid by a buyer, and the price paid to a seller. Prices may be decimal values when enabled. The command validates configured price floors, ceilings, blacklist entries, broken-item policy, shop limit, and creation funds before asking for a chest click.

## Administrative Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shop create <amount> <buy-price> <sell-price> admin` | Creates an unlimited-stock admin shop after chest selection. | `shopchest.create.admin` |
| `/shop removeall <player>` | Removes every normal and admin shop owned by the named player. | `shopchest.remove.other` |
| `/shop reload` | Reloads config, language, hologram format, updater state, database connection, and shops in loaded chunks. | `shopchest.reload` |
| `/shop update` | Performs the legacy remote update check. | `shopchest.update` |
| `/shop config set <property> <value>` | Sets a configuration value and reloads in-memory configuration. | `shopchest.config` |
| `/shop config add <property> <value>` | Adds a scalar to a configuration list. | `shopchest.config` |
| `/shop config remove <property> <value>` | Removes a scalar from a configuration list. | `shopchest.config` |

`/shop config set hologram-lift <value>` also moves currently loaded holograms immediately. Settings that affect command registration, database selection, debug-file creation, or startup-only integrations still require a clean server restart.

The automatic startup update checker is disabled in this custom build. The manual `/shop update` command remains present for compatibility, but deployment should use the repository's reviewed releases rather than an automatic download.
