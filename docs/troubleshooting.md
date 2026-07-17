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

Start with `/shops admin debug` and copy its support report. This snapshot does
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

Run `/shops admin debug` and inspect `Item naming`. Runtime translation-key
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

## Database and World Problems

Keep `remove-shop-on-error: false` while recovering temporarily unavailable worlds or blocked containers; otherwise failed records may be deleted. Stop the server before moving databases. ShopChest migrates known legacy schemas but does not transfer data between SQLite and MySQL automatically.

For a report, run `/shops admin debug`, click **Copy full support report**, and
include the result with exact reproduction steps and the complete first
exception with its `Caused by` chain. Console can run the same command and gets
the full report as plain text. Never publish database passwords or verbose
debug logs without reviewing them first.
