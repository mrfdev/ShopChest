# Troubleshooting

## Plugin Disables During Startup

Check the first ShopChest error in `logs/latest.log`.

- `Could not find plugin "Vault"`: install a compatible Vault jar.
- `Could not find any Vault economy dependency`: install and enable an economy provider; Vault by itself does not hold balances.
- Database connection failure: verify SQLite file access or MySQL host, port, database, credentials, and network access.
- Unsupported server/platform: use the maintained jar on Paper 26.1.2 or the current 26.2 compatibility-test build.
- `UnsupportedClassVersionError`: run the supported Paper server with Java 25.

## Shop Creation Does Not Finish

After `/shop create`, the player must click a chest within 15 seconds. Confirm that:

- The player is holding the intended item.
- The amount is positive and at least one price is above zero.
- The price respects decimal, minimum, maximum, and buy-versus-sell settings.
- The item is not blacklisted or disallowed because it is damaged.
- The player has available shop slots and enough money for the creation fee.
- The chest has air directly above it and is not already a shop.
- A protection integration did not deny the location.

Enable `enable-debug-log` and restart only while collecting diagnostics; `debug.txt` can grow quickly.

## Hologram or Product Name Is Wrong

Run `/shop reload` after editing `hologram-format.yml` or language files. Item names are read from `lang/items-<locale>.lang`; missing modern item translations fall back to a readable generated name. An `ERROR` name or a `craftDelegate` translation exception indicates an outdated jar, so replace duplicate jars and confirm the installed version with `/shop info`.

Use `/shop config set hologram-lift 0.35` to test a higher hologram position live. The default is `0.25`. Adjust in small increments, then keep the final value in `config.yml`.

Blue entity outlines or direction lines are normally the client's entity-hitbox debug view. Toggle Minecraft's hitbox display off before treating those lines as a ShopChest rendering fault.

## Shop Exists but Cannot Trade

- Right-click buys and left-click sells by default; `invert-mouse-buttons` swaps them.
- Creative-mode trading is blocked.
- The relevant price may be `0`, which disables that direction.
- The buyer may lack funds or inventory space.
- A normal shop may lack stock, chest space, or vendor funds.
- The player may lack `shopchest.buy` or `shopchest.sell`.
- A WorldGuard, PlotSquared, BentoBox, Towny, island, or claim rule may deny use.
- When confirmation is enabled, repeat the click.

## Database and World Problems

Keep `remove-shop-on-error: false` while recovering temporarily unavailable worlds or blocked chests; otherwise failed records may be deleted. Stop the server before moving databases. ShopChest migrates known legacy schemas but does not transfer data between SQLite and MySQL automatically.

For a report, include the ShopChest version from `/shop info`, Paper version/build, Java version, exact reproduction steps, relevant non-secret configuration, and the complete first exception with its `Caused by` chain. Never publish database passwords.
