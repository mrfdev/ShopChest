# Commands

The main command is created from `main-command-name` in `config.yml`; its default is `/shops`. It is registered dynamically rather than declared in `plugin.yml`.

## Player Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops` | Shows the commands available to the sender, grouped into player and permitted staff actions. | None |
| `/shops help` | Explicit alias for the same permission-aware command index. | None |
| `/shops info` | Shows a short introduction, numbered shop-creation instructions, installed version, clickable shop-health shortcut, and player-guide link. | None |
| `/shops create <amount> <buy-price> <sell-price> [normal]` | Selects the held product and starts a 15-second supported-container selection. A `0` price disables that trade direction. | `shopchest.create`, or the applicable directional/material permissions |
| `/shops edit <amount\|buy\|sell> <value>` | Starts a 15-second selection and changes one setting on a shop owned by the player. A price of `0` disables that direction. | The creation permissions required by the resulting trade directions and material |
| `/shops edit holograms <reset\|faceme\|north\|south\|east\|west>` | Starts a 15-second selection and changes the fixed text panel and rotating icon orientation together. | The same ownership and admin-shop rules as other edits |
| `/shops limits` | Shows used slots and the effective normal-shop limit. | None |
| `/shops list [page]` | Lists every shop owned by the player using compact rows and a whole-list health summary. Hover a row for prices, stock, type, world, and coordinates. Shop rows do not teleport the player. | None |
| `/shops recent [page]` | Shows recent purchases and sales made by the player, plus trades completed at the player's normal shops. Each page includes money earned, spent, and net change. | `shopchest.recent` (granted by default) |
| `/shops search <item> [page]` | Searches an exact base material and lists four in-stock, normal customer-buy shops per page. | `shopchest.search` (granted by default) |
| `/shops profile [player\|uuid]` | Shows a public Storefront Profile and scoped shop/stock summary. With no target, shows the player's own profile. | `shopchest.profile` (granted by default) |
| `/shops profile <player\|uuid> shops [page]` | Browses that storefront's scoped shops, four per page. `/shops profile shops [page]` browses the sender's own listings. | `shopchest.profile` (granted by default) |
| `/shops profile set <name\|advertisement\|description\|location> <text>` | Sets one plain-text field on the player's own profile. The internal aliases `tagline` and `directions` are also accepted. | `shopchest.profile` (granted by default) |
| `/shops profile clear <field>` | Clears one field without changing shop records or the remaining profile fields. | `shopchest.profile` (granted by default) |
| `/shops profile featured <add\|remove> <shop-id>` | Adds or removes one owned, eligible customer-buy shop in the ordered Featured Listings; at most three may be selected. | `shopchest.profile` (granted by default) |
| `/shops profile featured clear` | Clears all Featured Listings. | `shopchest.profile` (granted by default) |
| `/shops advertise` | Shows Advertising Pass state and previews a pass purchase or the next storefront advertisement. | `shopchest.advertise` (granted by default) |
| `/shops advertise pass` | Previews the configured exact-token cost and creates a one-use 60-second purchase confirmation. | `shopchest.advertise` (granted by default) |
| `/shops advertise status` | Shows pass expiry, unreserved broadcasts, owner cooldown, and open queue request. | `shopchest.advertise` (granted by default) |
| `/shops advertise cancel` | Cancels the player's waiting request and returns its reserved broadcast to the pass. | `shopchest.advertise` (granted by default) |
| `/shops inspect` | Starts a 15-second shop inspection selection. | None |
| `/shops info shop` | Compatibility alias for `/shops inspect`. | None |
| `/shops open` | Starts a 15-second selection to open a shop container. Owners need no extra permission. | `shopchest.openOther` for another player's shop |
| `/shops remove` | Starts a 15-second selection to remove a shop. Owners need no extra permission. | Elevated permissions apply to other players' and admin shops |

Creation arguments are the number of items per normal trade, the price paid by a buyer, and the price paid to a seller. Prices may be decimal values when enabled. The command validates configured price floors, ceilings, blacklist entries, broken-item policy, shop limit, and creation funds before asking for a supported-container click.

Editing applies the same amount, price, decimal, direction, material-permission,
blacklist, broken-item, configured price-bound, buy-versus-sell, and optional
CMI worth checks to the complete resulting shop. It persists all three terms in
one database update before changing the live shop or hologram. A failed update
leaves the previous terms active. Editing does not charge a creation fee,
consume another shop slot, or change the product, owner, container, or shop
type. Players can edit only their own shops; editing an owned admin shop also
requires `shopchest.create.admin`.

For display orientation, `faceme` selects the side where the player is standing
when the shop is clicked. `north`, `south`, `east`, and `west` set an exact
fixed direction. `reset` removes the shop override and resumes the container's
automatic facing. The override is stored on every block in the shop container,
so double-chest settings survive restarts without changing the chest blocks.

Help output and top-level tab completion use the same visibility rules. Player-only commands are omitted for console senders, and staff commands appear only when the sender has their required permission. Staff help lines include the relevant permission node.

## Public Discovery and Profiles

`/shops search` accepts a canonical vanilla base item with spaces, underscores,
or an optional `minecraft:` prefix. For example, `stone bricks`,
`stone_bricks`, and `minecraft:stone_bricks` resolve to the same material. The
parser tries the complete item name before treating a final positive number as
a page, so numbered material names remain usable.

An unresolved item stays unresolved. ShopChest may offer up to three clickable
exact material suggestions, drawn only from Customer-Buy Offers in the current
public catalogue; selecting one starts a new exact search.

Search does not perform fuzzy matching. Its candidates are scoped, normal
shops with a positive customer-buy price. Admin shops and shops that only buy
items from customers are excluded. Stock uses the complete configured bundle
and exact configured ItemStack variant. Only in-stock rows are displayed, while
the header separately counts out-of-stock and unchecked candidates. Unavailable
records are omitted. Results are owner-interleaved so one large storefront does
not fill every consecutive position.

Locations are informational for ordinary players. Marketplace results include
a clickable `/warp shops` call to action. Only a player with
`shopchest.admin.list` receives a location teleport action, and the command
rechecks the permission and recently listed target before teleporting.

Storefront profile text lives separately from shop records. The public fields
and limits are:

| Player field | Internal alias | Maximum characters |
| --- | --- | --- |
| `name` | None | 32 |
| `advertisement` | `tagline` | 80 |
| `description` | None | 180 |
| `location` | `directions` | 120 |

Text is trimmed and repeated spaces are collapsed. Legacy color codes,
MiniMessage tags, URLs, placeholders, newlines, control characters, hidden
formatting, and private-use characters are rejected. Initial publication
requires an eligible scoped normal shop. A retained profile remains editable
after its last eligible shop is removed, but stays dormant until another one
exists.

The profile overview includes total scoped listings, Customer-Buy stock, and
Customer-Sell capacity counts. Customer-Sell Offers report whether the
container can accept at least one complete exact configured bundle, is full,
unchecked, or unavailable.
The shop browser sorts the owner's valid Featured Listings first and shows four
rows per page. `/shops list` exposes each owned shop's `#ID` for the featured
commands. The first selected listing is the primary advertised product; up to
two later selections support it.

## Advertising Pass and Queue

The dashboard's clickable confirmations run hidden nonce-bearing forms of
`/shops advertise pass confirm <nonce>` and
`/shops advertise confirm <nonce>`. They are intentionally not ordinary manual
workflow commands: each nonce belongs to one player, is accepted once, and
expires after 60 seconds.

With defaults, a non-stacking pass costs 5 exact captured AFK Shrine Tokens,
lasts 7 days, and allows 3 successful broadcasts. A player may keep one open
request. The first broadcast is immediately owner-eligible; later broadcasts
wait 24 hours after that owner's previous broadcast. The durable FIFO queue,
hard-capped at 100 open requests, also enforces a 30-minute global interval. A
request that cannot run immediately waits instead of creating chat spam.

Preview and dispatch both require an eligible first Featured Listing. It must
have at least one complete exact bundle in stock. Dispatch revalidates the live
profile, featured shops, scope, shop state, and primary stock. Temporary stock
failure parks the request for a later retry. Invalid, cancelled, or expired
requests release their reservation; only an atomically committed successful
broadcast increments the pass usage. The request is durably claimed before
chat, title, and sound are emitted so a crash cannot duplicate an ad. In the
narrow opposite failure window, a committed use can be lost before players see
the message; external Minecraft effects cannot join the database transaction.

The default advertisement presents a title, subtitle, player-local sound, and
chat line to online players. It links to `/shops profile <owner-uuid>` and
`/warp shops`. Public text prefers the `advertisement` field, falls back to the
description, then uses a neutral stock message.

## Administrative Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/shops create <amount> <buy-price> <sell-price> admin` | Creates an unlimited-stock admin shop after supported-container selection. | `shopchest.create.admin` |
| `/shops admin` | Shows the ShopChest administration commands permitted for the sender. | Any permitted `shopchest.admin.*` action |
| `/shops admin list <player> [page]` | Lists every normal and admin shop registered to a player UUID or cached name. In-game staff get the same detailed hover and can click a row to teleport onto the block above its container; console rows retain plain-text coordinates. | `shopchest.admin.list` |
| `/shops admin audit [player\|all] [page]` | Runs a paginated, read-only maintenance audit across all persisted shops or one player UUID or cached name. | `shopchest.admin.audit` |
| `/shops admin storefront <player> <hide\|show\|suspend\|unsuspend\|clear>` | Moderates public profile text or removes a storefront from discovery without changing the player's shop records. | `shopchest.admin.storefront` |
| `/shops admin advertise currency status` | Reports whether an authoritative advertising token ItemStack has been captured. | `shopchest.admin.advertise` |
| `/shops admin advertise currency capture` | Captures the genuine token held in the administrator's main hand and persists its complete amount-normalized ItemStack template. | `shopchest.admin.advertise` |
| `/shops admin advertise currency clear` | Deletes the captured template and immediately returns advertising purchases to the fail-closed state. | `shopchest.admin.advertise` |
| `/shops admin export marketplace` | Creates a review-only JSON and CSV snapshot for the searchable website page. It does not publish either file. | `shopchest.admin.export` |
| `/shops debug [status]` | Collects a support snapshot covering the artifact target, Paper/Java runtime, platform, dependencies and hooks, database health and counts, runtime item translation-key coverage, loaded shop displays, stock state, and relevant configuration. Players can click to copy the full report; console receives plain text. | `shopchest.admin.debug` |
| `/shops debug commands [page]` | Lists ShopChest commands with descriptions and applicable permissions. | `shopchest.admin.debug` |
| `/shops debug permissions [page]` | Lists declared permissions, defaults, descriptions, and dynamic permission patterns. | `shopchest.admin.debug` |
| `/shops debug placeholders [page]` | Lists internal `hologram-format.yml` placeholders and clearly identifies that they are not PlaceholderAPI tokens. | `shopchest.admin.debug` |
| `/shops admin debug` | Compatibility alias for `/shops debug status`. | `shopchest.admin.debug` |
| `/shops removeall <player>` | Removes every normal and admin shop owned by the named player. | `shopchest.remove.other` |
| `/shops reload` | Reloads config, language, hologram format, shop visibility tasks, database connection, and shops in loaded chunks. | `shopchest.reload` |
| `/shops config set <property> <value>` | Sets a configuration value and reloads in-memory configuration. | `shopchest.config` |
| `/shops config add <property> <value>` | Adds a scalar to a configuration list. | `shopchest.config` |
| `/shops config remove <property> <value>` | Removes a scalar from a configuration list. | `shopchest.config` |

Display settings such as `/shops config set hologram-text-scale 0.50`,
positioning settings such as `/shops config set hologram-lift 0.25`, and icon
settings such as `/shops config set floating-icon-scale 0.45` update currently
loaded entities immediately. Boolean settings and
`hologram-text-alignment` provide value tab completion. Settings that affect
command registration, database selection, debug-file creation, or startup-only
integrations still require a clean server restart.

This custom fork does not perform remote update checks. Deploy reviewed builds
from the project repository through the normal server maintenance process.

`hide` suppresses a profile's player-written text while its otherwise eligible
shop listings remain discoverable. `show` restores that text. `suspend`
removes the complete storefront from profiles, search, advertising, and public
catalogue exports; `unsuspend` restores normal eligibility. `clear` removes the
stored text fields while preserving the moderation flags and underlying shops.

Advertising currency capture never guesses token identity from material,
display name, lore, custom-model data, or a PDC key. It clones one genuine item
from the administrator's main hand, normalizes the saved amount to 1, and
persists the complete Bukkit ItemStack. The held item is not consumed by setup.
At purchase time each candidate is amount-normalized and must pass
`ItemStack.isSimilar(template)`. Plain or renamed dyes, copied name/lore, and
items with missing, additional, or changed persistent data/components are
rejected. Purchases fail closed whenever the template cannot be loaded.

The pass purchase is revalidated immediately before mutation. Only exact
matching stacks from storage inventory are removed under a short inventory
lock. The delivery uses a one-use transaction identity to prevent duplicate
execution. If database delivery fails, the exact removal plan is rolled back;
an exceptional conflicting inventory recovery is logged for manual staff
resolution instead of silently duplicating or substituting tokens.

`/shops admin export marketplace` writes
`plugins/ShopChest/exports/marketplace/marketplace-snapshot.json` and
`marketplace-snapshot.csv` atomically. The export is marketplace-only even when
in-game discovery is configured as `GLOBAL`. It contains an explicit date
banner and a small player-safe allowlist, but no UUIDs, shop IDs, exact
coordinates, balances, or inventory data. See
[Marketplace Snapshot Export](marketplace-snapshot.md) before publication.

Shop listing is database-backed, so it includes registered shops in unloaded
chunks. Results are sorted by world and coordinates and shown eight per page.
Player chat uses compact item rows with location, prices, type, and stock in a
hover tooltip. A loaded normal shop that cannot supply one complete configured
purchase is visibly marked `[Out of stock]`; one that cannot accept a complete
configured sell bundle is marked `[Full]`. Obstructed display space is marked
`[Blocked]`. A world that is unavailable because it is missing or not loaded,
or a loaded location without its supported container, is marked
`[Unavailable]`.

The health line is calculated across every returned shop, not only the current
page. Ready means the shop was inspected in an already-loaded chunk and has no
known stock, capacity, display-space, or availability problem. Reason counts
can overlap, but the attention count includes an affected shop only once.
Unloaded chunks are never forced to load, so their shops are unchecked rather
than ready or broken. A cross-chunk double container is also left unchecked
unless both halves are loaded, preventing a misleading count from a partial
inventory. Admin shops report unlimited stock and capacity, and sell-only shops
report that sale stock is not applicable. The command performs no persistence
or repair action.

The administrator audit builds a complete, immutable report snapshot from an
asynchronous, explicit-column `SELECT` over the shop table. Invoking the command
without a page number refreshes that snapshot. Page requests reuse the completed
snapshot for up to 60 seconds, keeping counts and rows stable while staff move
between pages.

Only one audit build can be in flight globally, preventing overlapping scans on
the live server. The database query and non-Bukkit preprocessing run off the
server thread. Bukkit-backed product decoding, world/container inspection, and
report finalization are bounded across server ticks, with at most 25 records
processed per phase per tick while validating raw IDs, owner UUIDs, shop types,
intrinsic trade terms, and encoded products independently. One malformed row
therefore becomes one finding and does not abort the remaining report. Physical
inspection checks world and height bounds first, then uses only already-loaded
chunks. Unloaded anchor chunks and cross-chunk double containers with an
unloaded half are marked unchecked, never broken or safe to remove.

Known physical findings distinguish a world that is unavailable because it is
missing or unloaded, missing blocks, unsupported or incomplete containers, and
obstructed display space. Conflict/stale candidates include duplicate stored
locations, multiple rows resolving to one physical container, a different
loaded shop occupying that location, or a persisted record that is not active
in the loaded runtime. These are advisory maintenance leads, never proof that
any particular row is safe to delete. Reason totals can overlap; known-issue,
unchecked, and review-row counts remain deduplicated by record.

`/shops admin audit` and `/shops admin audit all` inspect every owner. A cached
name or player UUID scopes the displayed result to one owner, and a following
page number selects a page. Navigation uses the explicit `all` selector for
global reports. The command never repairs or removes a shop, writes the
database, changes schema, loads a chunk, mutates a block or inventory, writes
PDC, or changes configuration.

Audit rows expose staff-sensitive owner UUIDs, world names, and exact
coordinates. Review and redact them before sharing. Use `/shops debug` for a
privacy-filtered support report.

The hidden teleport action accepts only shop IDs from the most recent
authorized admin listing and checks `shopchest.admin.list` again when clicked.

The `/shops debug` status diagnostics query runs asynchronously and does not load shop chunks. Its
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
