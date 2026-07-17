# ShopChest Modernization Backlog

This backlog tracks planned work for the 1MoreBlock ShopChest fork. The current
working Paper 26.2 build remains the baseline while these changes are developed
and tested.

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
- [ ] Add a passive CMI `worth.yml` price-safety check, enabled by default when
  CMI and usable worth data are available.
  - Compare unit prices when a player creates or changes a shop and warn when
    its buy or sell price is unusually low or high relative to CMI worth.
  - Keep the check advisory: warn the player without blocking shop creation or
    price changes.
  - Use configurable comparison thresholds to avoid noisy warnings for normal
    market variation.
  - Clearly warn about prices that could let another player buy out the shop
    and immediately profit through `/sell`.

## Supported containers

- [ ] Generalize the current chest-specific storage logic into a supported
  container abstraction.
  - [ ] Chest.
  - [ ] Trapped chest.
  - [ ] Barrel.
  - [ ] Every dyed and undyed shulker box color.
  - [ ] Every vanilla copper chest type and oxidation/waxed variant.
  - Preserve inventory-space checks, stock accounting, protection hooks,
    hologram orientation, shop lookup, and removal behavior for every supported
    container.

## Player commands

- [x] Add `/shops info` with a short introduction, shop-creation instructions,
  and a link to the player-facing documentation.
- [x] Improve `/shops help` so command discovery, syntax, and permissions are
  clear and consistent.
- [ ] Add `/shops recent` to show recent purchases and sales, including money
  earned or spent where the available transaction data allows it.
- [x] Add or confirm `/shops list` so a player can locate all shops they own.

## Staff and diagnostics

- [x] Add `/shops admin`, or expand the existing administration surface with
  useful maintenance commands.
- [x] Add `/shops admin list <player>` to find every shop registered to a player.
- [ ] Add `/shops admin debug` under the `shopchest.admin.debug` permission with
  actionable plugin, platform, dependency, database, and shop-state diagnostics
  suitable for support reports.

## Items and localization

- [ ] Audit the locale data for every vanilla item introduced in Minecraft
  26.1, 26.1.2, and 26.2 so supported items never render as `ERROR` or
  `unknown item`.
- [ ] Replace version-specific generated item-name lists with automatic vanilla
  item naming where possible.
  - Prefer Paper/Adventure translatable components and the item's translation
    key for standard vanilla names.
  - Preserve custom names for renamed items.
  - Retain locale overrides only where administrators intentionally customize a
    displayed name.
  - Verify that a future 26.3 upgrade recognizes new vanilla items without a
    separately generated language-file update.

## Platform modernization

- [ ] Audit and simplify the supported platform matrix around Paper 26.2 and
  newer releases.
- [ ] Investigate removing legacy Minecraft NMS modules and old-version code.
- [ ] Investigate dropping Spigot support and using modern Paper APIs directly.
- [x] Remove the built-in update checker, its command, permissions, messages,
  and default configuration. It queried the original Spigot resource rather
  than this custom fork, so its results were not authoritative.
- [x] Remove bStats metrics collection and its shaded dependency. The old
  metrics used the original plugin's project ID and provided no useful data to
  this fork.
- [ ] Replace fragile NMS/reflection paths with stable Paper APIs wherever those
  APIs can preserve ShopChest's required per-player behavior.
- [ ] Audit Paper 26.2 deprecations and add upgrade-focused tests that expose API
  breakage before moving to a future Paper release.

## Feature exploration

- [ ] Review the existing data model and integrations for additional useful,
  low-maintenance player and staff features.
- [ ] Collect and evaluate feature suggestions, prioritizing reliability,
  discoverability, server performance, and straightforward future upgrades.

## Shelved future ideas

- [ ] Explore configurable item groups such as `#stone`, `#glass`, `#wool`, or
  `#dirt`, with each group defining a set of eligible blocks or items. A shop
  could trade a group instead of one exact item, allowing the buyer to select
  from the group or receive items from the available configured range. Define
  stock accounting, pricing, selection behavior, and clear hologram/command UX
  before considering implementation.
