# ShopChest Modernization Backlog

This backlog tracks planned work for the 1MoreBlock ShopChest fork. The current
working Paper 26.2 build remains the baseline while these changes are developed
and tested.

Last audited against the source tree, automated tests, and Paper 26.2 build 60
beta on 2026-07-17. Checked items have corresponding implementation or
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
  - Verified on Paper 26.2 build 60 with CMI 9.8.8.5 loading 1,532 worth
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
- [x] Add or confirm `/shops list` so a player can locate all shops they own.
  Player and admin views use compact rows with detailed hover text, and loaded
  shops that cannot fulfill one complete purchase are marked out of stock.

## Staff and diagnostics

- [x] Add `/shops admin`, or expand the existing administration surface with
  useful maintenance commands.
- [x] Add `/shops admin list <player>` to find every shop registered to a player.
  In-game staff retain click-to-teleport on the compact, stock-aware rows.
- [x] Add `/shops admin debug` under the `shopchest.admin.debug` permission with
  actionable plugin, platform, dependency, database, and shop-state diagnostics
  suitable for support reports. The database snapshot runs asynchronously,
  console receives plain text, and players can copy a full report that excludes
  credentials, file paths, player names, and world names.

## Items and localization

- [x] Audit the locale data for every vanilla item introduced in Minecraft
  26.1, 26.1.2, and 26.2 so supported items never render as `ERROR` or
  `unknown item`. Paper 26.2 build 60 reports usable translation keys for all
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
  - Startup and `/shops admin debug` audit the current server's complete item
    registry, so future vanilla items are recognized without regenerating a
    language file.

## Platform modernization

- [x] Audit and simplify the supported platform matrix around Paper 26.2 and
  newer releases.
  - Paper 26.2 build 60 beta and Java 25 are the verified baseline.
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
  - The exact Paper 26.2 build 60 API compiles with Java 25 and
    `-Xlint:deprecation` without plugin-source deprecation warnings.
  - Platform contract tests assert the descriptor target and the required
    display, spawning, and per-player visibility methods.

## Feature exploration

- [ ] Review the existing data model and integrations for additional useful,
  low-maintenance player and staff features.
- [ ] Collect and evaluate feature suggestions, prioritizing reliability,
  discoverability, server performance, and straightforward future upgrades.
  - Audit status: no separate, reviewed proposal set has been produced yet.

## Shelved future ideas

- [ ] Explore configurable item groups such as `#stone`, `#glass`, `#wool`, or
  `#dirt`, with each group defining a set of eligible blocks or items. A shop
  could trade a group instead of one exact item, allowing the buyer to select
  from the group or receive items from the available configured range. Define
  stock accounting, pricing, selection behavior, and clear hologram/command UX
  before considering implementation.
  - Audit status: intentionally shelved; no implementation exists.
