# Marketplace Snapshot Export

ShopChest can produce a reviewable website snapshot of the player marketplace:

```text
/shops admin export marketplace
```

The command requires `shopchest.admin.export`. It inspects the current public
catalogue without force-loading chunks, writes both JSON and CSV under
`plugins/ShopChest/exports/marketplace/`, and reports the resulting files to the sender.
Generating an export never publishes it. Staff must review the files and copy
the approved snapshot into the documentation source before deploying the
website.

## What the Snapshot Means

The website is a dated directory, not a live stock promise. Its banner and
capture timestamp must remain visible. A useful banner is:

```text
In August 2026, this is what we found to be sold at /warp shops. Prices and stock may have changed.
```

Players should use `/shops search <item>` in game for a fresher stock check.
The website search may match an owner name, storefront name, canonical material,
or item name from the captured file.

Only eligible, normal shops that sell items to customers in the configured
marketplace world and WorldGuard region are exported. Admin shops, shops that
only buy items from customers, suspended storefronts, malformed records, and
locations outside `/warp shops` are excluded. `GLOBAL` discovery mode does not
broaden the public website export.

The export records whether each listing was `IN_STOCK`, `OUT_OF_STOCK`, or
`UNCHECKED` at capture time. An unloaded chunk is `UNCHECKED`; the export does
not load it just to obtain a more favorable answer.

## Public Data Boundary

The JSON and CSV contain only the website allowlist:

- captured time, display timezone, ShopChest source version, banner, and
  marketplace label;
- aggregate candidate, published, availability, unavailable, and invalid-row
  counts;
- owner name, optional storefront name, and optional player-written directions;
- canonical material, visible item name, and an optional variant summary;
- configured bundle amount, customer price, and calculated unit price;
- availability at capture and a public location label.

Owner UUIDs, shop IDs, database row numbers, exact coordinates, inventory
contents, balances, token data, moderation state, and private database fields
are not exported. The JSON encoder escapes text for safe embedding, and the CSV
neutralizes spreadsheet formula prefixes.

The artifact schema is currently version `2`, recorded as `schemaVersion` in
JSON and `schema_version` in CSV. The CSV repeats the same aggregate counts on
each listing row; an empty export contains one metadata-only row so
its capture details and zero-listing outcome are still reviewable.

## Review and Publication Workflow

1. Run `/shops admin export marketplace` on the live server at the intended
   capture time.
2. Open both generated files and confirm the banner, timestamp, listing count,
   owner names, directions, prices, and availability labels are suitable for
   public release.
3. Investigate unexpected omissions in game. Do not change an `UNCHECKED` row
   to `IN_STOCK` by hand.
4. Replace `docs/catalogue/marketplace-snapshot.json` and
   `docs/catalogue/marketplace-snapshot.csv` with the reviewed pair.
5. Run the documentation repository's validation and build tasks.
6. Deploy the docs site. Keep the dated banner prominent and retain the in-game
   `/shops search` recommendation.

The two files should always come from the same export. Do not publish only one
side of the pair or combine files captured at different times.
