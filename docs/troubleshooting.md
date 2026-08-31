# Troubleshooting

## Plugin Disables During Startup

Check the first ShopChest error in `logs/latest.log`.

- `Could not find plugin "Vault"`: install a compatible Vault jar.
- `Could not find any Vault economy dependency`: install and enable an economy provider; Vault by itself does not hold balances.
- Database connection failure: verify SQLite file access or MySQL host, port, database, credentials, and network access.
- Unsupported server/platform: use the maintained jar on Paper 26.2.
- `UnsupportedClassVersionError`: run the supported Paper server with Java 25.

## Shop Creation Does Not Finish

After `/shops create`, the player must click a supported container within 15 seconds. Confirm that:

- The player is holding the intended item.
- The amount is positive and at least one price is above zero.
- The price respects decimal, minimum, maximum, and buy-versus-sell settings.
- The item is not blacklisted or disallowed because it is damaged.
- The player has available shop slots and enough money for the creation fee.
- Every block occupied by the container has air directly above it and is not already a shop.
- A protection integration did not deny the location.

Start with `/shops debug` and copy its support report. This snapshot does
not require verbose logging and excludes credentials, filesystem paths, player
names, world names, and individual shop locations. Enable `enable-debug-log`
and restart only when the snapshot is insufficient; `debug.txt` can grow
quickly.

## Hologram or Product Name Is Wrong

Run `/shops reload` after editing `hologram-format.yml` or language files.
Vanilla names normally come from the runtime item's Paper translation key and
are localized by the Minecraft client. `lang/items-<locale>.lang` is only an
optional administrator override layer; a missing entry is expected and does
not mean the item is unsupported. Custom and renamed items keep their own
display name.

Run `/shops debug` and inspect `Item naming`. Runtime translation-key
coverage should match the total runtime item count. The report also identifies
invalid overrides, while known failure values such as `ERROR`, `unknown item`,
and `not configured` are ignored instead of reaching a hologram. A
`craftDelegate` exception indicates an outdated jar, so remove duplicate jars
and confirm the installed version with `/shops info`.

Use `/shops config set hologram-lift 0.35` to test a higher hologram position live. The default is `0.25`. Adjust in small increments, then keep the final value in `config.yml`.

Use `/shops config set hologram-text-scale 0.50` to resize loaded holograms
without restarting. The supported range is `0.50` through `1.25`; smaller
values help distinguish neighboring shops, while the default remains readily
legible.

Blue entity outlines or direction lines are normally the client's entity-hitbox debug view. Toggle Minecraft's hitbox display off before treating those lines as a ShopChest rendering fault.

## Shop Exists but Cannot Trade

- Right-click buys and left-click sells by default; `invert-mouse-buttons` swaps them.
- Creative-mode trading is blocked.
- The relevant price may be `0`, which disables that direction.
- The buyer may lack funds or inventory space.
- A normal shop may lack stock, container space, or vendor funds.
- The player may lack `shopchest.buy` or `shopchest.sell`.
- A WorldGuard, PlotSquared, BentoBox, Towny, island, or claim rule may deny use.
- When confirmation is enabled, repeat the click.

## Search or a Storefront Is Missing a Shop

Start with `/shops list` and `/shops inspect` to confirm the registered shop and
its trade direction. Public item search includes only a normal shop with a
positive customer-buy price. It intentionally excludes admin shops and shops
that only buy items from customers.

With the default `MARKETPLACE` scope, confirm that the shop is in the exact
configured world and inside the exact WorldGuard region ID. The default is
world `general`, region `shops`. Discovery fails closed if WorldGuard is absent
or the lookup fails. A staff-suspended storefront is excluded from profiles,
search, advertising, and export; hiding only its text should leave eligible
listings visible.

`/shops search` takes an exact base material, not a fuzzy name. Use a key such
as `stone_bricks` or `minecraft:stone_bricks`. A matching shop appears as a row
only when an already-loaded container has at least one complete configured
bundle of the exact ItemStack variant. Out-of-stock shops are counted below the
results. Unloaded chunks are counted as unchecked, and unavailable records are
omitted. No discovery command force-loads a chunk.

The public catalogue warms up in bounded batches after startup or reload. If
the command says it is warming, wait briefly and retry. Shop create/remove and
profile moderation request a refresh; the periodic refresh also reconciles
persisted changes.

## Storefront Profile Text Is Rejected

Use one line of plain text and remain within the field limit: name 32,
advertisement 80, description 180, and location 120 characters. ShopChest
rejects legacy color codes, MiniMessage tags, URLs, placeholders, newlines,
control characters, hidden formatting characters, and private-use characters.
A player also needs at least one normal shop before publishing profile text.

Use `/shops profile clear <field>` to remove one field. If players see that the
text is hidden or the storefront is unavailable, trusted staff should inspect
the moderation state and use `/shops admin storefront <player> show` or
`unsuspend` only after review.

## Advertising Purchase or Queue Problems

Run `/shops admin advertise currency status` first. If no authoritative token
is captured, every purchase fails closed. To set it up, obtain one genuine AFK
Shrine Token through the real `/afkshrine` flow, hold it in the main hand, and
run `/shops admin advertise currency capture`. Capture does not consume the
held setup item.

A pass purchase accepts only inventory storage stacks whose amount-normalized
ItemStack is similar to the complete capture. Material, display name, lore, or
custom-model data alone are not enough. Plain or renamed dyes and copied
lookalikes should be rejected. If the token format intentionally changed, run
`currency clear`, capture the new genuine token, and recheck status. Do not edit
`advertising-currency.yml` by hand.

The clickable purchase confirmation expires after 60 seconds and executes at
most once. Preview `/shops advertise pass` again after expiry. Passes do not
stack; `/shops advertise status` reports the current pass, unreserved uses,
owner cooldown, and open request.

An advertisement needs at least one Featured Listing. Find an owned shop ID in
`/shops list`, then use `/shops profile featured add <shop-id>`. The first
featured shop must be a normal customer-buy offer and have one complete exact
bundle in stock both at preview and dispatch. A later stock problem parks the
request; the owner cooldown, global cooldown, and older eligible requests can
also delay it. Use `/shops advertise cancel` to close a waiting request and
return its reserved use.

If pass persistence fails after tokens are removed, ShopChest attempts to
restore the exact removed stacks before unlocking the inventory. A warning that
manual recovery is required means the inventory changed in conflict with that
rollback. Preserve logs and the player's UUID, stop repeated purchases, and
restore only after comparing the transaction and pass records.

## Marketplace Website Snapshot Looks Stale

The website catalogue is intentionally historical. Its banner and timestamp
state when staff captured it, and it does not query the live Minecraft server.
Players should use `/shops search` for a fresher in-game check.

Staff can generate a new review pair with
`/shops admin export marketplace`. The command does not publish anything.
Review both files in `plugins/ShopChest/exports/marketplace/`, then replace the JSON and CSV
sources together and run the docs validation/build. See
[Marketplace Snapshot Export](marketplace-snapshot.md) for the full public-data
boundary and checklist.

## Database and World Problems

Keep `remove-shop-on-error: false` while recovering temporarily unavailable worlds or blocked containers; otherwise failed records may be deleted. Stop the server before moving databases. ShopChest migrates known legacy schemas but does not transfer data between SQLite and MySQL automatically.

Run `/shops admin audit` for a read-only view of malformed records, unavailable
worlds (missing or unloaded), missing or unsupported containers, blocked
display space, and conflict/stale candidates. A persisted record that is not
active in the loaded runtime is an advisory candidate for investigation, never
proof that it is safe to delete.

Invoking the audit without a page number builds a fresh, immutable report.
Pagination reuses the completed snapshot for up to 60 seconds. Only one audit
build runs globally at a time; database and non-Bukkit processing stay off the
server thread, while Bukkit inspection and report finalization are spread
across bounded batches. The audit does not load chunks, modify records, or
invoke the normal shop-loading cleanup path. Unloaded locations remain
unchecked. Rerun it after ordinary chunk loading or concurrent shop changes
have finished when a fresh view is needed.

Audit rows include staff-sensitive owner UUIDs, world names, and exact
coordinates. Review and redact them before sharing. Use `/shops debug` when a
privacy-filtered support report is needed.

For a report, run `/shops debug`, click **Copy full support report**, and
include the result with exact reproduction steps and the complete first
exception with its `Caused by` chain. Console can run the same command and gets
the full report as plain text. Never publish database passwords or verbose
debug logs without reviewing them first.
