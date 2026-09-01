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
remains visible and usable. Normal chest interactions update the display on the
next server tick. Changes made by hoppers, commands, or other plugins are also
reconciled automatically in small batches, so a stale display should correct
itself within a few seconds without moving an item by hand.

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

## Edit an Existing Shop

Change one setting at a time, then click the shop within 15 seconds:

```text
/shops edit amount 16
/shops edit buy 75
/shops edit sell 0
/shops edit holograms faceme
```

The amount is the number of items in one trade. `buy` is what a customer pays
to buy that bundle, and `sell` is what the shop pays a customer for it. Setting
a price to `0` disables that direction, but at least one direction must remain
enabled.

Editing is free and does not consume another shop slot. It keeps the same
product, owner, container, and shop type. You can edit only shops you own, and
the server applies the same item and price rules used during creation.

If a fixed hologram faces into a wall or across a double chest instead of
toward the customer aisle, run `/shops edit holograms faceme` and click the
shop while standing on the side where customers should read it. Both the text
panel and rotating item icon use that orientation. You can instead choose an
exact `north`, `south`, `east`, or `west` direction. Use
`/shops edit holograms reset` to return to the container's automatic facing.

## How Players Use It

By default, right-click buys from a shop and left-click sells to it. The server can invert those controls. Sneak while clicking to trade up to one full item stack instead of the shop's normal bundle size. A shop cannot complete a trade when the buyer, vendor, container, or inventory lacks the required money, items, or space.

The default setup charges 5 economy units to create a normal shop and allows 5 normal shops per player. Server ranks and configuration may change both values. Admin shops are not counted toward player limits and have unlimited stock and funds.

## Find Shops and Browse Storefronts

Use an exact base item name to find normal player shops that currently have at
least one complete bundle available:

```text
/shops search stone_bricks
/shops search stone bricks
/shops search minecraft:stone_bricks
```

All three examples search the same vanilla material. Search is exact rather
than fuzzy: `stone_bricks` does not also mean cracked or mossy stone bricks.
Item metadata is still respected when stock is counted, so a shop is in stock
only when its container holds its exact configured item variant and full bundle
amount.

Results show four shops per page with bundle price, unit price, available full
bundles, storefront, and coordinates. Only in-stock results are listed. A
summary also mentions matching shops that are out of stock or could not be
checked because their chunks are unloaded. ShopChest does not load chunks for a
search. Admin shops, suspended storefronts, and shops that only buy items from
customers are not search results.

If an item cannot be resolved, ShopChest may show up to three clickable exact
material suggestions that are actually present in the public shop catalogue.
Suggestions never broaden the search until you choose one.

The seller's storefront name opens their public profile. Locations are useful
directions but do not teleport ordinary players. Use the clickable
`/warp shops` link to visit the marketplace. Trusted staff with the shop-list
permission receive a separately checked teleport action.

Hover a shop's item row, price line, or location in a Storefront Profile to see
its unique shop ID. An enchanted book or potion keeps its exact Minecraft item
tooltip on the item name itself, so hover its bullet, quantity, price, or
location when you need the shop ID.

When you browse your own storefront's shop pages, each row shows its shop ID.
Eligible Customer-Buy Offers include a clickable Feature action, while current
Featured Listings include a Remove action. The grey
`/shops profile featured add <shop-id>` prompt opens this picker, and pressing
Tab after `/shops profile featured add ` suggests your eligible shop IDs.

The [searchable marketplace snapshot](https://docs.1moreblock.com/player-guides/custom-server-plugins/shopchest/marketplace-snapshot/)
can also be searched by owner or item. It clearly shows when its data was
captured and may be older than the live server. Use the in-game search for a
fresher stock check.

## Create a Public Storefront Profile

A Storefront Profile describes the seller, while the underlying shop records
continue to manage products, prices, stock, and locations. Create at least one
normal shop, then set any of these optional plain-text fields:

```text
/shops profile set name JahLion's special gear shop!
/shops profile set advertisement Need protection? I sell OP armor and weapons
/shops profile set description Overpowered suits, weapons, and adventure gear
/shops profile set location At /warp shops, look for the lion head on the left
```

`name` is limited to 32 characters, `advertisement` to 80, `description` to
180, and `location` to 120. Formatting codes, MiniMessage tags, links,
placeholders, line breaks, control characters, and hidden formatting characters
are rejected. Staff can moderate public text without changing the player's
shops.

Preview your own or another seller's profile, then browse its four-shops-per-page
listing:

```text
/shops profile
/shops profile JahLion
/shops profile JahLion shops 2
/shops profile 00000000-0000-0000-0000-000000000000
```

The overview summarizes how many public shops sell to players and buy from
players. Customer-Buy Offers show stock; Customer-Sell Offers show whether the
container has room for at least one complete exact bundle, is full, unchecked,
or unavailable. Shop pages show each direction separately with its price and
current stock or capacity state.

Choose up to three ordered Featured Listings for advertising. `/shops list`
shows each owned shop's `#ID`:

```text
/shops profile featured add 123
/shops profile featured remove 123
/shops profile featured clear
```

The first Featured Listing is the primary advertised product; the next two can
support it. Only your eligible normal shops that sell to customers can be
featured. Remove a field without affecting the others with, for example,
`/shops profile clear description`.

## Advertise Your Storefront

Storefront advertising uses AFK Shrine Tokens earned through `/afkshrine`
trades. With the default beta settings, one Advertising Pass costs 5 exact
tokens, lasts 7 days, and includes 3 successful public broadcasts. Passes do
not stack, a seller can broadcast at most once every 24 hours, and the whole
server waits at least 30 minutes between advertisements.

Start with `/shops advertise`. The command shows the pass or queue state and
clickable previews. A purchase preview never charges automatically: its
one-use confirmation expires after 60 seconds. Once a pass is active, the same
dashboard previews an advertisement built from the profile and Featured
Listings. Confirming that preview either broadcasts when eligible or creates
one durable queued request.

```text
/shops advertise
/shops advertise pass
/shops advertise status
/shops advertise cancel
```

The primary Featured Listing must have one complete exact bundle in stock when
you queue the advertisement and is checked again before broadcast. A temporarily
out-of-stock primary listing waits in the queue; an invalid or expired request
closes without spending its reserved broadcast. Cancelling a waiting request
returns the reservation to the pass. Only a successful broadcast consumes one
of the three uses.

An advertisement sends online players a title, subtitle, chat message,
configured sound, clickable storefront profile, and `/warp shops` link. Its
text uses the storefront advertisement line first, then the description, then
a neutral fallback.

Only items that exactly match the administrator-captured AFK Shrine Token count
toward a purchase. Plain or renamed dyes and copied name/lore lookalikes are not
accepted. If pass delivery fails after tokens are removed, ShopChest restores
the exact removed items before allowing another purchase attempt.

## Commands

- `/shops` or `/shops help` - Show the player commands available to you. Permitted staff actions appear in a separate section.
- `/shops info` - Show a short introduction, numbered creation instructions, the installed version, and clickable links to your shop health and this guide.
- `/shops create <amount> <buy-price> <sell-price>` - Prepare a shop using the held item, then click a supported container within 15 seconds.
- `/shops edit <amount|buy|sell> <value>` - Change one setting, then click one of your shops within 15 seconds.
- `/shops edit holograms <reset|faceme|north|south|east|west>` - Reorient both shop displays without rotating or recreating the container.
- `/shops limits` - Show used and available normal-shop slots.
- `/shops list [page]` - List every shop you created and summarize how many are ready, need attention, are out of stock, full, blocked, unavailable, or unchecked. Hover a compact row for its prices, stock, type, world, and coordinates. Known problem shops are marked `[Out of stock]`, `[Full]`, `[Blocked]`, or `[Unavailable]`; distant unloaded shops remain unchecked rather than being treated as broken. The locations are informational and do not teleport you.
- `/shops recent [page]` - Review recent purchases and sales, including trades at your normal shops and the money you earned or spent. Hover a compact trade row for its date, per-item price, and shop location. History is available only for trades recorded by the server.
- `/shops search <item> [page]` - Find in-stock normal player shops selling an exact base material, four per page.
- `/shops profile [player|uuid] [shops [page]]` - View a seller's Storefront Profile and scoped public shops.
- `/shops profile set <name|advertisement|description|location> <text>` - Set one public profile field.
- `/shops profile clear <field>` - Clear one public profile field.
- `/shops profile featured <add|remove> <shop-id>` - Manage up to three ordered Featured Listings; use `featured clear` to remove all.
- `/shops advertise` - Open the Advertising Pass, preview, and queue dashboard.
- `/shops advertise status` - Show the active pass, remaining broadcasts, cooldown, and open request.
- `/shops advertise cancel` - Cancel a waiting request and return its reserved broadcast.

When recorded trades changed your shop balance while you were offline, joining
shows a compact revenue summary. Hover **View recent trades** for its action and
click it to run `/shops recent`.
- `/shops inspect` - Inspect the shop you are looking at immediately. If none is in sight, click one within 15 seconds. Shop owners and staff can also see its unique shop ID. `/shops info shop` is a compatibility alias.
- `/shops open` - Enter open mode, then click one of your shops within 15 seconds.
- `/shops remove` - Enter removal mode, then click one of your shops within 15 seconds.

The server may configure a different main command, but `/shops` is the default.

## Permissions and Rank Requirements

Shop creation, buying, and selling are available to everyone by default. Opening or removing another player's shop, creating admin shops, bypassing protection rules, and administrative commands require elevated permissions. Rank-specific shop limits can be assigned by the server.

## Costs, Limits, and Cooldowns

- Normal shop creation costs 5 by default. The actual charge is shown by the server's economy formatting.
- The default limit is 5 normal shops. `/shops limits` shows the value that applies to you.
- The fallback click step after create, inspect, open, or remove expires after 15 seconds.
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
