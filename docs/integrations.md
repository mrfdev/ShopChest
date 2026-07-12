# Integrations

## Required Economy Stack

Vault is a hard dependency. ShopChest also requires an economy provider registered through Vault; Vault alone is not enough. All creation charges, refunds, buys, sells, balance checks, and displayed currency formatting use that provider.

## Optional Protection and World Integrations

| Plugin | Verified ShopChest behavior |
| --- | --- |
| WorldGuard | Registers `create-shop`, `use-shop`, and `use-admin-shop` state flags and checks them during creation and trading. |
| Towny | Restricts shop creation to configured plot types according to resident, mayor, and king context. |
| PlotSquared | Registers and evaluates shop flags for supported old and newer PlotSquared API layouts. |
| BentoBox | Registers a shop flag and checks island access. |
| GriefPrevention | Checks claim permission before shop creation/use through its listener hook. |
| AreaShop | Removes shops on configured region lifecycle events; its listener is used with the WorldGuard-backed integration. |
| ASkyBlock / uSkyBlock / IslandWorld | Checks island ownership or access through the installed plugin API. |
| AuthMe | Ignores shop interactions from players who are not authenticated. |

Each hook has an `enable-*-integration` setting. Most are also listed as soft dependencies so they load before ShopChest when installed. Restart after adding, removing, updating, or toggling an integration.

`shopchest.external.bypass` lets trusted staff bypass integrated region, plot, island, or claim denials while using shops. `shopchest.create.protected` separately bypasses a cancelled creation event.

## Multiworld and Proxy Behavior

Multiverse-Core and MultiWorld are soft dependencies used to improve load ordering for persisted shops in multiple worlds. Shop records include world and block coordinates.

When `enable-vendor-bungee-messages` is enabled, ShopChest sends vendor notifications over the outgoing `BungeeCord` plugin channel. This is message forwarding, not shared shop storage. A network that needs common shops must configure a shared MySQL database and validate concurrent behavior in staging.

## Metrics and Updates

The jar includes bStats metrics and records broad settings such as database type and shop type counts. The legacy update command is present, while automatic startup update checks are disabled in this custom build.

ShopChest does not provide a PlaceholderAPI expansion and does not integrate with mcMMO. Hologram placeholders are internal to ShopChest; external plugins should not treat the fake display entities as players.
