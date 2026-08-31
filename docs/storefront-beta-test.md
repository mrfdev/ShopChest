# Storefront Beta Test Checklist

Use a private test server and disposable shop/token data. Test with at least two
ordinary players plus one staff account so permission boundaries and queue
ordering are visible.

## 1. Staff Setup

1. Confirm WorldGuard contains region `shops` in world `general`, or change the
   two `storefront-discovery` names to the test fixtures.
2. Run `/shops reload` and wait for the public catalogue ready log line.
3. Run `/shops admin advertise currency status`. It should say no template is
   captured on a clean setup.
4. Obtain one genuine token through the real `/afkshrine` trade, hold it in the
   main hand, and run `/shops admin advertise currency capture`.
5. Run `/shops admin advertise currency status` again. Keep the setup token;
   capture should not consume it.

Create these normal test shops inside the marketplace region:

- an in-stock `STONE_BRICKS` customer-buy shop;
- an out-of-stock `STONE_BRICKS` customer-buy shop;
- a `STONE_BRICKS` shop that only buys from customers;
- at least five in-stock `STONE_BRICKS` shops across two owners for pagination
  and owner interleaving;
- one admin shop selling `STONE_BRICKS`;
- one normal shop just outside the marketplace region.

Use `/shops list` to record each shop's displayed `#ID`.

## 2. Profile and Moderation

As an ordinary shop owner, run:

```text
/shops profile
/shops profile set name JahLion's Gear
/shops profile set advertisement Need protection? I sell OP armor and weapons
/shops profile set description Armor, weapons, and adventure gear
/shops profile set location At /warp shops, look for the lion head on the left
/shops profile
/shops profile shops 1
```

Verify the fields, shop counts, Customer-Buy stock, Customer-Sell capacity,
four-row page size, coordinates,
and clickable `/warp shops` link. Confirm that the coordinates themselves do
not teleport the ordinary player. View the same profile by player name and
UUID from the second account.

Run each invalid text case and confirm it is rejected without changing the
previous value:

```text
/shops profile set description &aGreen shop
/shops profile set description <green>Green shop</green>
/shops profile set description https://example.com
/shops profile set description %player_name%
```

Also test an empty value, a line break pasted into chat, and text one character
over each documented limit. Then clear and restore one field:

```text
/shops profile clear description
/shops profile set description Armor, weapons, and adventure gear
```

Feature the recorded IDs in order:

```text
/shops profile featured add <in-stock-shop-id>
/shops profile featured add <second-in-stock-shop-id>
/shops profile featured add <third-in-stock-shop-id>
/shops profile featured add <fourth-shop-id>
```

The first three should succeed and retain order; the fourth should be rejected.
Confirm that another owner's ID, an admin-shop ID, and a customer-sell-only ID
are rejected. Exercise `featured remove` and `featured clear`, then restore at
least one in-stock primary listing.

As staff, test moderation:

```text
/shops admin storefront JahLion hide
/shops admin storefront JahLion show
/shops admin storefront JahLion suspend
/shops admin storefront JahLion unsuspend
/shops admin storefront JahLion clear
```

`hide` should suppress player-written text but preserve eligible listings.
`suspend` should remove the storefront from profile, search, advertising, and
export. `clear` should remove text without deleting shop or Featured Listing
records and should retain the current moderation flags.

## 3. Exact Item Search

Run these equivalent searches:

```text
/shops search stone_bricks
/shops search stone bricks
/shops search minecraft:stone_bricks
/shops search stone_bricks 2
```

Verify exact material resolution, stable four-row pagination, bundle and unit
prices, full-bundle availability, storefront links, and owner interleaving.
The summary should mention the out-of-stock shop. It must not list the admin
shop, customer-sell-only shop, suspended owner, or outside-region shop.

Unload a candidate shop chunk and search again after the previous snapshot
expires. It should contribute to the unchecked count without loading the chunk.
Remove the complete configured bundle from one loaded shop and confirm it moves
from the result rows to the out-of-stock count. Stock an item with the same
material but different metadata and verify that it does not satisfy the exact
configured variant.

Misspell a material, such as `/shops search stone_briks`, and confirm no fuzzy
results appear. At most three clickable suggestions may be offered, and every
suggestion must be an exact material currently sold by a public Customer-Buy
Offer.

Repeat a search immediately to confirm rate limiting. Navigate an existing
result's next/previous links and confirm it reuses a stable snapshot. As staff
with `shopchest.admin.list`, verify a listed coordinate is clickable and the
target/permission are revalidated. Remove the permission before clicking a
cached row and confirm teleport is denied.

Temporarily set `storefront-discovery.location-scope` to `GLOBAL`, reload, and
confirm the outside-region normal shop becomes discoverable but still does not
give ordinary players a teleport action. Restore `MARKETPLACE` afterward.

## 4. Exact Advertising Currency

Prepare these inventory fixtures beside genuine tokens:

- plain `LIGHT_BLUE_DYE`;
- a dye renamed to the token's visible name;
- an item with copied name and lore;
- token-like items with missing, additional, and changed PDC/components.

Run `/shops advertise pass`. Its preview should count only genuine stacks that
pass complete amount-normalized `ItemStack.isSimilar` matching. Confirm that a
stack spread over multiple storage slots is counted and that armor/offhand
slots are not treated as payment storage.

Click the generated confirmation once. Verify exactly the configured number of
genuine tokens is removed and no lookalike changes. Click the same confirmation
again and confirm it cannot execute twice. Try an expired confirmation, too few
tokens, two rapid confirmations, and an already active pass. None may create a
second pass or consume extra items.

In a controlled failure-injection test, fail pass persistence after exact stack
removal. Confirm the exact removed items, including their metadata/components,
return and the player can retry only after recovery. Also exercise a full or
concurrently changed inventory and verify ShopChest logs an explicit manual
recovery condition instead of substituting or duplicating items.

Run `/shops admin advertise currency clear` and confirm the template file is
removed, status reports fail-closed state, and purchases consume nothing.
Recapture the genuine token before continuing.

## 5. Advertisement Preview and Queue

With an active pass and restored Featured Listings, run:

```text
/shops advertise
/shops advertise status
```

Verify the preview uses the first Featured Listing as primary and includes only
in-stock support listings. Remove the primary bundle and confirm queueing is
refused. Restock it, open a new preview, and confirm the request. Confirm there
is at most one open request and that status shows its eligibility.

Queue eligible requests from two owners. For a shorter test, temporarily set
`advertising.global-cooldown-minutes` to `1`, reload, and restore `30` after the
test. Verify FIFO eligible order and at least one minute between title/chat/sound
broadcasts. A successful message should link both the seller profile and
`/warp shops`, and decrement exactly one use.

After one owner broadcasts, confirm a new request respects the configured owner
cooldown. Cancel a waiting request with `/shops advertise cancel`; its reserved
use should return. Make a queued primary temporarily out of stock and confirm
it waits rather than spends a use. Restore stock and verify a later poll can
broadcast it. Exercise request expiry on disposable data and verify the
reservation returns.

## 6. Website Snapshot Export

Run:

```text
/shops admin export marketplace
```

Verify the JSON and CSV pair appears under
`plugins/ShopChest/exports/marketplace/`. Confirm both carry the same capture
time, display timezone, source version, schema version, aggregate candidate and
availability counts, banner, and listing data. Check that
only marketplace-region normal customer-buy shops are present, including their
captured in-stock/out-of-stock/unchecked state.

Search the files for UUIDs, exact coordinates, shop IDs, database row numbers,
inventory contents, balances, token data, and private moderation data. None
should be present. Open the CSV with a formula-prefixed public fixture and
confirm it is neutralized. Confirm JSON public text is escaped rather than
interpreted as markup.

Finally, review and copy the pair into `docs/catalogue/`, run the docs validation
and build, and open the website page. Search by owner name, storefront name,
item name, and material. The capture banner must stay prominent and explain
that prices and stock may have changed.

## 7. Restore Beta Defaults

Before handing the test server back, restore:

```text
/shops config set storefront-discovery.location-scope MARKETPLACE
/shops config set advertising.global-cooldown-minutes 30
/shops reload
```

Run currency status, profile, search, advertise status, and one final export.
Record any intentionally queued request so another tester does not mistake it
for a duplicate.
