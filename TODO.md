# ShopChest Modernization Backlog

This backlog tracks planned work for the 1MoreBlock ShopChest fork. The current
working Paper 26.2 build remains the baseline while these changes are developed
and tested.

Last audited against the source tree, automated tests, and Paper 26.2 build 84
stable on 2026-07-28. Checked items have corresponding implementation or
verification evidence; unchecked items remain unimplemented or intentionally
shelved.

## Holograms and presentation

- [x] Replace the separate TextDisplay entity for each hologram line with one
  multiline text panel.
  - Use a single, subtle translucent background for the complete panel.
  - Set a controlled panel width and allow Minecraft to lay out wrapped lines.
  - Limit overly long renamed-item labels, append an ellipsis, and sanitize
    embedded newlines/control characters so player-created names cannot break
    the layout.
  - Keep the full item name available through shop inspection or item details.
  - Position the separate item icon relative to the complete panel so wrapped
    text cannot overlap it.
  - Keep the icon independently scaled, floating, and rotating even when the
    text panel is fixed to the front of the chest.
- [x] Fix the hologram to the front of its shop chest instead of
  rotating toward each viewer.
- [x] Replace the plain white hologram palette with configurable soft pastel hex
  colors while retaining good contrast and accessibility.
- [x] Add a configurable, slightly smaller TextDisplay font scale so holograms
  occupy less space and remain distinct when shop chests are placed next to
  each other, without sacrificing readability.
- [x] Expose bounded, live-reloading administrator controls for the complete
  text panel and floating product icon, including text opacity/shadow/alignment,
  icon height and scale, and optional bob/rotation timing.
- [x] Show an `[Out of stock]` state when an enabled normal-shop buy side cannot
  supply one complete configured purchase, while preserving any independently
  enabled sell price.
- [x] Show an enchanted book's enchantment and level in the hologram, such as
  `Fortune III`, instead of only `Enchanted Book`.
- [x] Show a potion's effect details in the hologram, including its localized
  effect name, amplifier, and duration, such as `Fire Resistance (0:30)`,
  instead of only `Potion`.
  - Preserve modern client-side translations through Adventure components.
  - Show up to seven entries, arrange two per line, and summarize overflow.
  - Use a dedicated configurable pastel detail color so metadata remains
    visually distinct from the product name.

## Trading feedback

- [x] Add configurable success and failure feedback when a player attempts a
  trade, using restrained chest-local particles and distinct sounds so the
  result is immediately visible and audible.
  - Audited the existing trade path: it previously emitted no sounds or
    particles.
  - Effects are player-local, emitted once per terminal result, and capped at
    16 particles.
- [x] Add a passive CMI `worth.yml` price-safety check, enabled by default when
  CMI and usable worth data are available.
  - Compare unit prices when a player creates or changes a shop and warn when
    its buy or sell price is unusually low or high relative to CMI worth.
  - Keep the check advisory: warn the player without blocking shop creation or
    price changes.
  - Use configurable comparison thresholds to avoid noisy warnings for normal
    market variation.
  - Clearly warn about prices that could let another player buy out the shop
    and immediately profit through `/sell`.
  - The implementation uses CMI's already-loaded `WorthManager`, performs one
    metadata-aware lookup only after a normal-shop proposal passes validation,
    and never reads or scans `Worth.yml` itself.
  - Warnings are limited to one customer-price warning and one shop-payout
    warning per proposal. Admin shops and items without positive CMI worth data
    are skipped silently.
  - Verified on Paper 26.2 build 84 stable with CMI 9.8.8.5 loading 1,532 worth
    entries; startup, `/shops reload`, diagnostics, and existing shop visuals
    remained clean.

## Supported containers

- [x] Generalize the current chest-specific storage logic into a supported
  container abstraction.
  - [x] Chest (supported by the existing chest-specific implementation).
  - [x] Trapped chest (supported by the existing chest-specific implementation).
  - [x] Barrel.
  - [x] Every dyed and undyed shulker box color.
  - [x] Every vanilla copper chest type and oxidation/waxed variant.
  - Preserve inventory-space checks, stock accounting, protection hooks,
    hologram orientation, shop lookup, and removal behavior for every supported
    container.
  - Implemented through an explicit Paper 26.2 allowlist and one container
    resolver. Storage, click handling, hopper protection, dynamic stock updates,
    explosions, protection integrations, multi-block lookup/removal, display
    centering, and horizontal orientation now share that resolver. Ender chests
    and unrelated inventory blocks remain unsupported.

## Player commands

- [x] Add `/shops info` with a short introduction, shop-creation instructions,
  and a link to the player-facing documentation.
- [x] Improve `/shops help` so command discovery, syntax, and permissions are
  clear and consistent.
- [x] Add `/shops recent` to show recent purchases and sales, including money
  earned or spent where the available transaction data allows it. The
  database-backed history distinguishes the player's own trades from customer
  activity at their normal shops, paginates eight entries at a time, and uses
  compact rows with detailed date, unit-price, and shop-location hover text.
- [x] Reformat the join-time "while you were offline" revenue summary as a
  compact Adventure message with a useful hover tooltip and a click action that
  runs `/shops recent`.
  - The action follows the configured root command, falls back cleanly for
    existing language files, and is sent only while the player remains online.
- [x] Add or confirm `/shops list` so a player can locate all shops they own.
  Player and admin views use compact rows with detailed hover text, and loaded
  shops that cannot fulfill one complete purchase are marked out of stock.

## Staff and diagnostics

- [x] Add `/shops admin`, or expand the existing administration surface with
  useful maintenance commands.
- [x] Add `/shops admin list <player>` to find every shop registered to a player.
  In-game staff retain click-to-teleport on the compact, stock-aware rows.
- [x] Add `/shops debug` under the `shopchest.admin.debug` permission with
  actionable plugin, platform, dependency, database, and shop-state diagnostics
  suitable for support reports. The database snapshot runs asynchronously,
  console receives plain text, and players can copy a full report that excludes
  credentials, file paths, player names, and world names. Staff can also inspect
  paginated command, permission, and internal hologram-placeholder catalogs;
  `/shops admin debug` remains a compatibility alias for status.

## Items and localization

- [x] Audit the locale data for every vanilla item introduced in Minecraft
  26.1, 26.1.2, and 26.2 so supported items never render as `ERROR` or
  `unknown item`. Paper 26.2 build 84 stable reports usable translation keys for all
  1,537 runtime item materials, including a zero-override server test.
- [x] Replace version-specific generated item-name lists with automatic vanilla
  item naming where possible.
  - Standard vanilla names now use the runtime ItemStack's Paper translation
    key and an Adventure translatable component in holograms.
  - Renamed items, custom display names, written-book titles, and named player
    heads retain their custom names.
  - `items-<locale>.lang` remains an optional administrator override layer;
    missing entries use runtime translation keys and known error sentinels are
    ignored.
  - Startup and `/shops debug` audit the current server's complete item
    registry, so future vanilla items are recognized without regenerating a
    language file.

## Platform modernization

- [x] Audit and simplify the supported platform matrix around Paper 26.2 and
  newer releases.
  - Paper 26.2 build 84 stable and Java 25 are the verified baseline.
  - Older Paper/Minecraft and Spigot are explicitly unsupported.
  - Newer Paper releases remain compatibility targets and must pass the
    platform contract, clean build, and test-server checks before deployment.
- [x] Remove legacy Minecraft NMS modules and old-version code.
  - Deleted the dormant Spigot version modules and the active packet/reflection
    implementation, platform loader, version parser, and pre-1.13 branches.
- [x] Drop Spigot support and use modern Paper APIs directly.
  - Build conventions now resolve Paper only and `plugin.yml` declares API
    version 26.2.
- [x] Remove the built-in update checker, its command, permissions, messages,
  and default configuration. It queried the original Spigot resource rather
  than this custom fork, so its results were not authoritative.
- [x] Remove bStats metrics collection and its shaded dependency. The old
  metrics used the original plugin's project ID and provided no useful data to
  this fork.
- [x] Replace fragile NMS/reflection paths with stable Paper APIs wherever those
  APIs can preserve ShopChest's required per-player behavior.
  - Holograms use `TextDisplay`, product icons use `ItemDisplay`, and visibility
    uses Paper's per-player `showEntity`/`hideEntity` API.
  - Display creation, inventory reads, and visibility updates now run on the
    server thread instead of the old custom updater thread.
- [x] Audit Paper 26.2 deprecations and add upgrade-focused tests that expose API
  breakage before moving to a future Paper release.
  - The exact Paper 26.2 build 84 stable API compiles with Java 25 and
    `-Xlint:deprecation` without plugin-source deprecation warnings.
  - Platform contract tests assert the descriptor target and the required
    display, spawning, and per-player visibility methods.

## Security assurance

- [ ] Plugin: security scan. Run a Codex Security scan on this repository.

## Feature exploration

- [x] Review the existing data model and integrations for additional useful,
  low-maintenance player and staff features.
- [x] Collect and evaluate feature suggestions, prioritizing reliability,
  discoverability, server performance, and straightforward future upgrades.
  The initial reviewed proposal set is tracked below.

## Approved feature proposals

### Storefront discovery release

- [ ] Ship `/shops profile`, `/shops search`, and `/shops advertise` together
  as one production-ready storefront discovery release.
  - Implement and verify the three features incrementally, but do not deploy a
    partial live JAR that exposes only part of the intended player experience.
  - Use one shared public-catalogue policy for profile listings, search results,
    advertisements, stock semantics, owner identity, and location disclosure so
    the three features cannot drift apart.
  - Complete an integrated live-JAR test on the maintained Paper 26.2 and Java
    25 server with CMI and WorldGuard before release, including clean migration,
    reload, restart persistence, pagination, permissions, scoped location
    disclosure, concurrent advertising, and stale or unloaded shop paths.
- [x] Persist one storefront profile per owner UUID separately from individual
  shop records.
  - Use a dedicated profile table and migration for both SQLite and MySQL;
    profile updates must never rewrite or customize authoritative shop rows.
  - Isolate profile load, validation, and write failures so corrupt or invalid
    public text cannot prevent shops from loading or trading.
  - Retain a profile when its last normal shop is removed, but keep it dormant
    and publicly unavailable until the owner has another eligible normal shop.
- [x] Add `/shops profile [player|uuid]` for the public overview and
  `/shops profile <player|uuid> shops [page]` for individual shop listings.
  - Let an eligible owner set or clear a custom storefront `name` (32
    characters), `tagline` (80), `description` (180), and `directions` (120).
    Always show the authoritative current or cached player name alongside a
    custom storefront name.
  - Treat `tagline` as the saved promotional line reused by
    `/shops advertise`; reserve `advertisement` for the public promotion itself.
  - Render owner text as plain, sanitized text with no formatting syntax,
    newlines, control characters, URLs, placeholders, or player-authored click
    actions. Provide permission-gated staff clear, hide, and moderation actions.
  - Keep hiding owner-authored profile text separate from Storefront Suspension.
    Hidden text falls back to server-controlled identity while otherwise-valid
    Shop Listings remain searchable; a staff suspension removes the Storefront
    and its listings from profile, search, advertisement, and snapshot discovery
    without deleting or rewriting the underlying shops.
  - Show authoritative computed totals for normal shops, including customer-buy
    and customer-sell directions, confirmed out-of-stock shops, unavailable
    shops, and unloaded or otherwise unchecked shops.
  - Show four shop listings per page with product, bundle amount, prices, stock
    or capacity state, and permitted location details. Never force-load chunks
    merely to calculate or display a profile.
  - Keep normal-player rows non-clickable for teleportation. Let authorized
    staff reuse the permission-rechecked admin-list teleport action, and give
    ordinary players a server-controlled clickable `/warp shops` action.
  - Use owner UUIDs in generated browse and pagination commands so name changes
    do not break an open profile.
  - Let owners maintain up to three ordered Featured Listings with
    `/shops profile featured add <shop-id>`, `remove <shop-id>`, and `clear`.
    Store only owned shop IDs; resolve products, prices, stock, and eligibility
    from authoritative shop data whenever the profile or an advertisement is
    rendered.
  - Use the first eligible Featured Listing as the default primary advertised
    shop, with up to two additional eligible listings as supporting offers.
    Never silently replace all owner-selected listings with unrelated shops.
- [ ] Add an optional persistent Storefront Display through
  `/shops profile display create`, separate from advertisement broadcasts.
  - Prompt an eligible owner to hit an Ender Chest, then use it only as the
    display anchor for a special storefront hologram assembled from the profile
    and live shop data, never as a tradable inventory.
  - Keep the display visually consistent with existing shop holograms and the
    current color theme. Use bounded smaller text, automatic wrapping, and a
    clean ellipsis for overlong profile text so the result remains readable.
  - Give Storefront Displays their own ownership, placement, removal, limit,
    persistence, and moderation rules; do not put them in the advertisement
    queue or apply broadcast cooldowns to them.
- [x] Add a configurable marketplace location scope for public profile and
  search results.
  - Default exact world/XYZ disclosure to shops inside world `general` and the
    WorldGuard region `shops`, corresponding to the `/warp shops` marketplace.
  - Add a `global` mode that allows exact locations for all otherwise-public
    shop listings when staff intentionally choose broader discovery.
  - Outside the configured scope, normal players may still see public product,
    price, stock-state, owner, and storefront information, but not exact
    coordinates. Fail closed on coordinates if the world, region, or WorldGuard
    integration cannot be resolved.
- [x] Add `/shops search <item> [page]` so players can find shops currently
  selling a requested product, for example `/shops search stone_bricks`.
  - Make v1 an exact base-material search resolved against the runtime Minecraft
    item registry. Accept case-insensitive canonical keys with spaces or
    underscores and an optional `minecraft:` prefix; never derive matches from
    profile text, custom item names, lore, enchantments, book contents, PDC, or
    other item metadata.
  - Resolve the complete joined query before interpreting a final positive
    integer as a page, so numbered material names remain valid. Generate
    pagination commands with the canonical one-token material key and offer only
    a bounded set of clickable material suggestions for unresolved input rather
    than silently broadening it into fuzzy results.
  - Include only authoritative normal shops with an enabled Customer-Buy Offer
    whose Storefront is not suspended. Exclude admin shops and shops that have
    only a Customer-Sell Offer; a bidirectional shop remains eligible through
    its Customer-Buy Offer.
  - Match candidate shops by base material, but calculate each candidate's stock
    only from items that match its exact configured product. Treat a shop as in
    stock only when its loaded, valid container can complete at least one full
    configured bundle, even when partial auto-calculated trades are enabled.
  - Distinguish confirmed in-stock, confirmed out-of-stock, unchecked, and
    unavailable states. Unloaded chunks and unloaded double-chest partners are
    unchecked, never out of stock; malformed, conflicting, missing, or inactive
    shops are unavailable and omitted from public totals.
  - Present a precise summary such as `7 shops across 5 storefronts are in stock
    now; 4 more are out of stock; 2 more could not be checked.` Put only the
    confirmed in-stock Shop Listings on result pages.
  - Show four physical Shop Listings per page. Interleave owners so duplicate
    listings from one Storefront do not displace distinct matching Storefronts;
    after each distinct owner has one row, fill remaining slots from the
    remaining listings using deterministic tie-breakers. Never boost
    advertisements, Featured Listings, or `/shops top` leaders in organic search.
  - Show the actual sanitized product name, configured bundle, total and
    per-item price, owner and Storefront, complete bundles currently available,
    and location details permitted by the Marketplace Location Scope.
  - Keep normal-player coordinates informational and non-clickable. Make only
    the Storefront link run `/shops profile <owner-uuid>` and show a clickable
    `/warp shops` action only when it truthfully helps reach matching marketplace
    listings. Authorized staff may receive a separately permission-rechecked
    shop-ID teleport action that revalidates the current shop and destination.
  - Reuse one short-lived immutable candidate and ordering snapshot across
    pagination so stock changes do not arbitrarily reshuffle an open result set.
    Revalidate the current page's shops before rendering, omit rows that are no
    longer ready with an explanation, and build a fresh snapshot for a new search.
  - Build a bounded material index from authoritative product data instead of
    decoding every stored ItemStack for every command. Keep database work
    asynchronous, isolate malformed rows, inspect Bukkit inventories only on the
    server thread in bounded batches, rate-limit repeated player searches, and
    never force-load chunks merely to answer a search.
  - Test material normalization and numbered names, metadata-heavy products,
    exact-product stock, one full bundle and one item below, partial-trade mode,
    duplicate owner listings, every availability state, changing stock during
    pagination, location scopes, suspension, ordinary-player click safety, and
    permission removal before a staff teleport click.
- [x] Add AFK Shrine Token item-currency support for purchasing a weekly
  Advertising Pass.
  - Treat the physical AFK Shrine Token earned through AFKShrine trades as
    distinct from AFKShrine's pending and claimed virtual token balances.
  - Add an admin-only setup and status path under
    `/shops admin advertise currency` that captures one genuine held token,
    clones the complete `ItemStack`, normalizes its amount to `1`, and persists
    it as ShopChest's authoritative advertising-currency template.
  - Do not identify tokens from `LIGHT_BLUE_DYE`, display name, lore, custom
    model data, or a guessed PDC key. Normalize a candidate's amount to `1` and
    accept it only when `candidate.isSimilar(template)` succeeds. Fail closed
    when the template is missing, invalid, or cannot be deserialized.
  - Revalidate the captured template and all five matching items immediately
    before purchase. Consume only exact matching stacks from the agreed player
    inventory scope on the server thread, using an inventory snapshot and
    compare-before-apply step so concurrent changes cannot alter the payment.
  - Assign every pass purchase a durable idempotency key and transaction state
    so repeated clicks, command replay, reconnects, reloads, or restarts cannot
    create two passes or charge twice.
  - Persist the exact removed stacks in durable recovery escrow before changing
    the inventory. Finalize their consumption only after the Advertising Pass
    is durably delivered; otherwise restore those exact items, or retain an
    explicit pending refund when immediate restoration is impossible.
  - Test a genuine captured token split across stacks and reject a plain or
    renamed light-blue dye, copied name or lore, and candidates with missing,
    additional, or changed PDC/data components or other item metadata. Also test
    double confirmation, inventory changes, logout, full-inventory recovery,
    template replacement, and crashes at every transaction boundary.
- [x] Sell one non-stackable seven-day Advertising Pass for five exact physical
  AFK Shrine Token items.
  - Grant three successful advertisement broadcasts during the rolling
    seven-day pass window, with at least 24 hours between an owner's completed
    broadcasts. Do not let passes overlap or accumulate unused allowances.
  - Let one queued Advertisement Request reserve one remaining broadcast
    allowance. Cancellation or failed broadcast delivery returns that allowance
    to the same still-valid pass; it does not refund the already-delivered pass
    purchase.
  - Keep a request submitted before pass expiry valid until its own bounded
    queue expiry, but reject new requests after the pass expires.
- [x] Add `/shops advertise` so an eligible shop owner can publish a polished
  public advertisement assembled from their storefront profile and live shops.
  - Make `/shops advertise` a non-mutating dashboard and preview. Add explicit
    confirmation, status, and pre-dispatch cancellation actions; show the pass
    expiry, remaining allowances, exact token cost when a pass is needed,
    personal eligibility, queue state, and earliest possible broadcast time.
  - Highlight one primary Featured Listing in the title and subtitle and show
    up to two supporting eligible listings in chat. Resolve the storefront name,
    tagline, products, bundle sizes, prices, and stock fresh at dispatch time.
  - Direct players to the storefront profile and to `/warp shops` only when the
    advertised location policy makes that destination truthful.
  - Present the advertisement to online players with a sound, title and
    subtitle, plus an in-game chat message.
  - Require an active storefront profile and at least one confirmed available
    normal-shop offer. Omit stale, unavailable, unchecked, or out-of-stock
    offers from live advertisement details.
  - Maintain a bounded durable FIFO queue with at most one open request per
    owner. Allow queuing during personal or global cooldowns, but reject a full
    queue, an exhausted pass, or a second open request before reserving an
    allowance.
  - Apply a configurable global interval between broadcasts. Park and skip
    transiently unchecked or out-of-stock requests so they cannot block ready
    owners; expire unresolved requests after a bounded period and return their
    reserved pass allowance.
  - Revalidate ownership, permissions, moderation state, Featured Listings,
    customer-buy direction, stock, container availability, location policy, and
    audience immediately before dispatch. Permanent invalidity cancels the
    request and releases its pass allowance.
  - Claim queue work, enforce global and per-owner timing, and record successful
    broadcasts atomically so concurrent commands and shared MySQL servers cannot
    duplicate a public advertisement. Preserve at-most-once broadcast behavior
    across reloads and restarts.
- [x] Add a searchable ShopChest Marketplace Snapshot to the player-facing
  guide on `docs.1moreblock.com`, similar to the existing `/buy` price
  catalogue, and link it from the ShopChest guide.
  - Place it under the ShopChest guide as a clearly dated player-shop directory
    and distinguish it from both the static `/buy` catalogue and live in-game
    `/shops search` results.
  - Include only non-suspended, customer-buy-enabled normal Shop Listings found
    inside world `general` and WorldGuard region `shops` when captured. Keep all
    eligible in-stock, out-of-stock, and unchecked listings in the snapshot and
    provide an availability filter rather than making temporarily empty
    Storefronts disappear. Exclude unavailable, malformed, conflicting, missing,
    and inactive listings from the public snapshot.
  - Provide separate item and owner search modes. Item search uses canonical
    base-material names with spaces and underscores treated equivalently; owner
    search uses the authoritative player name or public Storefront name, but not
    taglines, descriptions, item lore, or other owner-authored keywords.
  - Show one row or card per physical Shop Listing with its owner and Storefront,
    sanitized item name, base material, bundle amount, captured bundle and unit
    price, and `in stock when captured`, `out of stock when captured`, or
    `unchecked when captured` rather than presenting historical stock as live.
  - State the exact capture date and time prominently, explain that owners,
    listings, prices, directions, and stock may have changed, and direct visitors
    to `/shops search` for current availability and `/warp shops` to visit.
  - Publish only player-safe fields. Use sanitized Storefront directions or a
    server-controlled stall label instead of raw world/XYZ coordinates, and
    exclude UUIDs, internal shop IDs and database fields, serialized ItemStacks,
    PDC/data components, book contents, private metadata, and staff actions.
  - Add a staff-only repeatable catalogue export that applies the same public
    catalogue and suspension policies, inspects only already-loaded inventories,
    and writes versioned sanitized JSON and CSV with capture time, source version,
    and aggregate counts. Write rejected-row details only to a separate staff-only
    report. Require review and an explicit docs update; never publish
    automatically from the live server.
  - Add a ShopChest-specific or generalized safe catalogue component instead of
    reusing `/buy`-specific Worth and menu fields. Render imported strings only
    as text, neutralize spreadsheet formulas in CSV, support client-side filters
    and pagination, and verify the generated page, assets, stale-data warning,
    and guide link in the docs validation and production build.
- [x] Add `/shops edit` so a shop owner can safely update the configured trade
  amount, customer buy price, or customer sell price without removing and
  recreating the shop.
  - Reuse creation price bounds, directional and material permissions, CMI
    worth advisories, ownership checks, and exact product metadata.
  - Persist the complete validated update before refreshing the loaded
    hologram, stock state, and player-facing shop information.
  - Do not charge another shop-creation fee or silently change the shop item,
    owner, container, or normal/admin type.
  - Implemented as a 15-second owner-only shop selector with field-specific
    `amount`, `buy`, and `sell` updates. The complete proposed terms are
    validated and persisted atomically before the loaded shop and hologram are
    refreshed on the server thread.
  - Also supports `holograms reset|faceme|north|south|east|west` for a persisted
    per-shop text-panel and floating-icon orientation. Double-chest overrides
    are stored on both physical container blocks and cleared when the shop is
    removed.
- [x] Add a compact shop-health summary showing how many owned shops are out of
  stock, full, unavailable, or otherwise need attention.
  - Prefer enhancing `/shops list` and `/shops info` over adding noisy recurring
    messages.
  - `/shops list` now summarizes the owner's complete result set rather than
    only the visible page, with a deduplicated attention total and separate
    ready, out-of-stock, full, blocked, unavailable, and unchecked counts. A
    shop can have multiple attention reasons; distant unloaded shops remain
    unchecked rather than being reported as broken.
  - Full means a loaded normal shop cannot accept one complete configured sell
    bundle. Unavailable is reserved for a world that is unavailable because it
    is missing or not loaded, or a loaded location without its supported
    container. Cross-chunk double containers are checked only while both halves
    are already loaded. A loaded shop is blocked when the display space above
    its supported container is obstructed.
  - The database `SELECT` remains asynchronous; plain database rows are
    hydrated and already-loaded inventories are inspected on the server thread.
    The report never force-loads a chunk and adds no database column, migration,
    stored record, PDC value, or configuration setting. List rows identify
    out-of-stock, full, blocked, and unavailable shops, and `/shops info` links
    players directly to the list.
- [x] Add `/shops admin audit [player]` as a dry-run maintenance report for
  unavailable worlds (missing or unloaded), missing or unsupported containers,
  blocked display space, invalid products, and stale database records.
  - Implemented as `/shops admin audit [player|all] [page]` with a dedicated
    `shopchest.admin.audit` permission, whole-scope counts, and paginated review
    rows. Malformed rows are isolated instead of aborting the report.
  - Each run completes an immutable report snapshot. Invoking the command
    without a page refreshes it; pagination reuses the completed snapshot for
    up to 60 seconds so results cannot drift between pages.
  - Only one audit build can be in flight globally. The explicit-column
    database `SELECT` and non-Bukkit preprocessing run off the server thread.
    Product decoding, already-loaded world/container inspection, and report
    finalization run in bounded server-thread batches of at most 25 records per
    phase per tick. Unloaded chunks and partially loaded cross-chunk double
    containers remain unchecked.
  - Stored-coordinate conflicts, physical-container conflicts, records
    shadowed by a different loaded shop, and persisted records not active in
    the loaded runtime are advisory conflict/stale candidates. The audit never
    chooses a winner or claims that a record is safe to delete.
  - Adds no database write, schema migration, stored field, PDC value, or
    configuration setting and performs no chunk load, repair, or removal.
  - Rows contain staff-sensitive owner UUIDs, world names, and exact
    coordinates and must be reviewed or redacted before they are shared.
- [ ] Add owner statistics for purchases, sales, earnings, and spending over a
  bounded configurable period, building on the existing economy log and its
  indexes.
- [ ] Add recent seller leaderboards through `/shops top` and external
  placeholders suitable for a CMI hologram at `/warp shops`, building on the
  owner-statistics aggregation.
  - Define transparent recent ranking measures, such as completed customer
    purchases, items or bundles sold, and owner earnings, rather than relying
    on an ambiguous all-time `top seller` score.
  - Support bounded configurable periods such as today, 7 days, and 30 days so
    the leaderboard highlights currently active sellers.
  - Let `/shops top [period] [page]` show ranked owners with useful shop or
    product context and a clickable `/warp shops` action.
  - Expose documented PlaceholderAPI/CMI-compatible values for a configurable
    number of ranks, including each seller's name, rank, selected metric, and
    period, with stable fallback text when a rank has no result.
  - Count only finalized successful trades at normal player shops. Exclude
    failed, cancelled, refunded, admin-shop, and owner self-trade activity so
    rankings cannot be trivially inflated.
  - Aggregate asynchronously and serve placeholders from a bounded refreshed
    cache so frequent CMI hologram updates never perform database work or force
    chunks to load.
- [ ] Improve staff maintenance with filters for owner, world, product, shop
  state, and age, followed by explicit confirmed actions for selected invalid
  shops.
  - Keep database scans asynchronous and avoid bulk chunk loading.

## Shelved future ideas

- [ ] Explore a stable public API and Paper events for shop creation, editing,
  removal, successful trades, and failed trades.
  - Audit status: intentionally shelved until a concrete integration needs a
    public contract that can be maintained across future releases.
- [ ] Explore configurable item groups such as `#stone`, `#glass`, `#wool`, or
  `#dirt`, with each group defining a set of eligible blocks or items. A shop
  could trade a group instead of one exact item, allowing the buyer to select
  from the group or receive items from the available configured range. Define
  stock accounting, pricing, selection behavior, and clear hologram/command UX
  before considering implementation.
  - Audit status: intentionally shelved; no implementation exists.
