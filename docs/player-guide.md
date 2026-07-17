# ShopChest Player Guide

## Introduction

ShopChest lets players turn supported storage containers into shops. A shop can sell items to other players, buy items from them, or do both. The floating item shows the product, while the hologram shows the owner, quantity, active prices, and useful enchantment or potion details.

## Quick Start

1. Place a supported container with open space directly above it.
2. Hold the exact item you want the shop to trade.
3. Run `/shops create <amount> <buy-price> <sell-price>`.
4. Click the container within 15 seconds.
5. Put stock in the container when players should be able to buy from you. Leave room in it when players should be able to sell to you.

Supported containers are normal and trapped chests, barrels, undyed and dyed
shulker boxes, and every copper chest oxidation and waxed variant available in
Paper 26.2. Ender chests, hoppers, furnaces, and other inventory blocks are not
shop containers.

When an enabled buy side cannot supply the full configured purchase amount, its
hologram price changes to `[Out of stock]`. A separately enabled sell side
remains visible and usable.

Enchanted books and other enchanted products list their enchantments and
levels. Potions list their localized effects, amplifiers, and durations. Dense
items are kept readable with two details per line and a short overflow summary.
Standard vanilla item names use Minecraft's client-side language, including
items introduced by newer supported server releases. Renamed items retain the
name chosen by the player.

Example: `/shops create 8 40 20` creates a shop that trades 8 held items at a time. Other players pay 40 to buy that bundle and receive 20 when selling that bundle to the shop.

Set either price to `0` to disable that direction. For example, `/shops create 16 100 0` only sells items to players.

When the server uses CMI, ShopChest may show a brief price-check warning before
you choose the container. It can point out a direct `/sell` resale opportunity or a
price far outside the server's configured worth. The warning is informational:
your entered price is unchanged and you can continue creating the shop.

## How Players Use It

By default, right-click buys from a shop and left-click sells to it. The server can invert those controls. Sneak while clicking to trade up to one full item stack instead of the shop's normal bundle size. A shop cannot complete a trade when the buyer, vendor, container, or inventory lacks the required money, items, or space.

The default setup charges 5 economy units to create a normal shop and allows 5 normal shops per player. Server ranks and configuration may change both values. Admin shops are not counted toward player limits and have unlimited stock and funds.

## Commands

- `/shops` or `/shops help` - Show the player commands available to you. Permitted staff actions appear in a separate section.
- `/shops info` - Show a short introduction, numbered creation instructions, the installed version, and a clickable link to this guide.
- `/shops create <amount> <buy-price> <sell-price>` - Prepare a shop using the held item, then click a supported container within 15 seconds.
- `/shops limits` - Show used and available normal-shop slots.
- `/shops list [page]` - List every shop you created. Hover a compact row for its prices, stock, type, world, and coordinates. Loaded shops that cannot fill one complete purchase are marked `[Out of stock]`; the locations are informational and do not teleport you.
- `/shops recent [page]` - Review recent purchases and sales, including trades at your normal shops and the money you earned or spent. Hover a compact trade row for its date, per-item price, and shop location. History is available only for trades recorded by the server.
- `/shops inspect` - Enter inspection mode, then click a shop within 15 seconds. `/shops info shop` is a compatibility alias.
- `/shops open` - Enter open mode, then click one of your shops within 15 seconds.
- `/shops remove` - Enter removal mode, then click one of your shops within 15 seconds.

The server may configure a different main command, but `/shops` is the default.

## Permissions and Rank Requirements

Shop creation, buying, and selling are available to everyone by default. Opening or removing another player's shop, creating admin shops, bypassing protection rules, and administrative commands require elevated permissions. Rank-specific shop limits can be assigned by the server.

## Costs, Limits, and Cooldowns

- Normal shop creation costs 5 by default. The actual charge is shown by the server's economy formatting.
- The default limit is 5 normal shops. `/shops limits` shows the value that applies to you.
- The click step after create, inspect, open, or remove expires after 15 seconds.
- There is no recurring shop fee or reward in ShopChest.
- Creation refunds are disabled by default. When enabled, only the creator receives the configured current creation price after removing their own shop.
- A second click may be required for a purchase or sale when confirmation is enabled.

## Important Notes

- Every block occupied by the shop container must have air directly above it so ShopChest can display the product and hologram.
- Shop products retain their item metadata. Hold the exact item variant you intend to trade.
- Keep sale stock in the container and leave enough empty capacity for purchases from players.
- Protection plugins may prevent creation or trading in a region, plot, island, or claim.
- Creative-mode trading is blocked. Creative players can still use the configured item-selection workflow when creating a shop.
- Holding the configured information item, a stick by default, and clicking a shop shows its details.

## Related Features

ShopChest uses the server's Vault economy and can respect region, plot, island, claim, and authentication plugins configured by staff. Those integrations do not add player placeholders or separate player commands.

## Technical Documentation

For command syntax, permissions, configuration, and troubleshooting, see the
[ShopChest technical documentation](https://github.com/mrfdev/1MB-Plugins-Docs/tree/main/project-docs/shopchest/).
