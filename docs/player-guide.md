# ShopChest Player Guide

## Introduction

ShopChest lets players turn ordinary chests into shops. A shop can sell items to other players, buy items from them, or do both. The floating item shows the product, while the hologram shows the owner, quantity, and active prices.

## Quick Start

1. Place a chest with open space directly above it.
2. Hold the exact item you want the shop to trade.
3. Run `/shop create <amount> <buy-price> <sell-price>`.
4. Click the chest within 15 seconds.
5. Put stock in the chest when players should be able to buy from you. Leave room in it when players should be able to sell to you.

Example: `/shop create 8 40 20` creates a shop that trades 8 held items at a time. Other players pay 40 to buy that bundle and receive 20 when selling that bundle to the shop.

Set either price to `0` to disable that direction. For example, `/shop create 16 100 0` only sells items to players.

## How Players Use It

By default, right-click buys from a shop and left-click sells to it. The server can invert those controls. Sneak while clicking to trade up to one full item stack instead of the shop's normal bundle size. A shop cannot complete a trade when the buyer, vendor, chest, or inventory lacks the required money, items, or space.

The default setup charges 5 economy units to create a normal shop and allows 5 normal shops per player. Server ranks and configuration may change both values. Admin shops are not counted toward player limits and have unlimited stock and funds.

## Commands

- `/shop` - Show command help.
- `/shop info` - Show the ShopChest name, installed version, starting commands, and a clickable link to this guide.
- `/shop create <amount> <buy-price> <sell-price>` - Prepare a shop using the held item, then click a chest within 15 seconds.
- `/shop limits` - Show used and available normal-shop slots.
- `/shop inspect` - Enter inspection mode, then click a shop within 15 seconds. `/shop info shop` is a compatibility alias.
- `/shop open` - Enter open mode, then click one of your shops within 15 seconds.
- `/shop remove` - Enter removal mode, then click one of your shops within 15 seconds.

The server may configure a different main command, but `/shop` is the default.

## Permissions and Rank Requirements

Shop creation, buying, and selling are available to everyone by default. Opening or removing another player's shop, creating admin shops, bypassing protection rules, and administrative commands require elevated permissions. Rank-specific shop limits can be assigned by the server.

## Costs, Limits, and Cooldowns

- Normal shop creation costs 5 by default. The actual charge is shown by the server's economy formatting.
- The default limit is 5 normal shops. `/shop limits` shows the value that applies to you.
- The click step after create, inspect, open, or remove expires after 15 seconds.
- There is no recurring shop fee or reward in ShopChest.
- Creation refunds are disabled by default. When enabled, only the creator receives the configured current creation price after removing their own shop.
- A second click may be required for a purchase or sale when confirmation is enabled.

## Important Notes

- The chest must have air directly above it so ShopChest can display the product and hologram.
- Shop products retain their item metadata. Hold the exact item variant you intend to trade.
- Keep sale stock in the chest and leave enough empty capacity for purchases from players.
- Protection plugins may prevent creation or trading in a region, plot, island, or claim.
- Creative-mode trading is blocked. Creative players can still use the configured item-selection workflow when creating a shop.
- Holding the configured information item, a stick by default, and clicking a shop shows its details.

## Related Features

ShopChest uses the server's Vault economy and can respect region, plot, island, claim, and authentication plugins configured by staff. Those integrations do not add player placeholders or separate player commands.

## Technical Documentation

For command syntax, permissions, configuration, and troubleshooting, see the [technical overview](../README.md) or visit <https://docs.1moreblock.com/custom-server-plugins/shopchest/>.
