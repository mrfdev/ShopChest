# Hologram Placeholders

ShopChest does not register a PlaceholderAPI expansion. The placeholders below belong only to `plugins/ShopChest/hologram-format.yml` and are replaced while ShopChest builds its own shop holograms.

| Placeholder | Output |
| --- | --- |
| `%VENDOR%` | Shop owner's current name. |
| `%AMOUNT%` | Number of products in one configured trade. |
| `%ITEMNAME%` | Localized or custom product name. |
| `%ITEM-DETAILS%` | Multiline combined enchantment and potion details using `hologram-colors.details`. The bundled layout uses this placeholder. |
| `%ENCHANTMENT%` | Multiline enchantment names and levels, including stored enchanted-book entries, using `hologram-colors.details`. |
| `%POTION-EFFECT%` | Multiline base and custom potion effects with amplifier and duration where applicable, using `hologram-colors.details`. |
| `%BUY-PRICE%` | Vault-formatted price a player pays to buy, or the localized `[Out of stock]` state when an enabled normal shop cannot supply the configured amount. |
| `%SELL-PRICE%` | Vault-formatted amount a player receives for selling. |
| `%STOCK%` | Matching product items currently in a normal shop container. |
| `%MAX-STACK%` | Product's maximum stack size. |
| `%CHEST-SPACE%` | Number of matching product items that can still fit in the shop container. |
| `%DURABILITY%` | Legacy durability value stored for the product. |
| `%COLOR-OWNER%` | Global `hologram-colors.owner` RGB color. |
| `%COLOR-QUANTITY%` | Global `hologram-colors.quantity` RGB color. |
| `%COLOR-ITEM%` | Global `hologram-colors.item` RGB color. |
| `%COLOR-LABEL%` | Global `hologram-colors.label` RGB color. |
| `%COLOR-BUY-VALUE%` | Global `hologram-colors.buy-value` RGB color. |
| `%COLOR-SELL-VALUE%` | Global `hologram-colors.sell-value` RGB color. |
| `%COLOR-SEPARATOR%` | Global `hologram-colors.separator` RGB color. |
| `%COLOR-ADMIN%` | Global `hologram-colors.admin` RGB color. |
| `%COLOR-UNAVAILABLE%` | Global `hologram-colors.unavailable` RGB color. |
| `%COLOR-RESET%` | Clears the active color and text decoration. |

`%ITEM-DETAILS%`, `%ENCHANTMENT%`, and `%POTION-EFFECT%` are Adventure components rather than flattened legacy strings. This preserves Minecraft's client-side translations. Use them directly in a format line; they are not supported inside numeric braced calculations. `%MUSIC-TITLE%`, `%BANNER-PATTERN-NAME%`, and `%GENERATION%` remain reserved and are not connected to runtime providers.

## Conditions and Calculations

Each line contains ordered options. The first option whose requirements pass is displayed. Verified requirement names are `VENDOR`, `AMOUNT`, `ITEM_TYPE`, `ITEM_NAME`, `HAS_ENCHANTMENT`, `HAS_ITEM_DETAILS`, `BUY_PRICE`, `SELL_PRICE`, `HAS_POTION_EFFECT`, `IS_MUSIC_DISC`, `IS_POTION_EXTENDED`, `IS_WRITTEN_BOOK`, `IS_BANNER_PATTERN`, `ADMIN_SHOP`, `NORMAL_SHOP`, `IN_STOCK`, `OUT_OF_STOCK`, `MAX_STACK`, `CHEST_SPACE`, and `DURABILITY`.

Conditions support boolean logic and comparisons, for example `NORMAL_SHOP`, `BUY_PRICE > 0`, or `(IN_STOCK > 0) || ADMIN_SHOP`. Braced expressions can calculate numeric output from placeholders, such as `{%STOCK% / 64}`. Options are evaluated from top to bottom.

The bundled layout adds `%ITEM-DETAILS%` between the product and price rows. It presents prices as `Buy: <price> | Sell: <price>`, keeping labels and values visually distinct. When a normal shop has fewer matching items than its configured transaction amount, this automatically becomes `Buy: [Out of stock] | Sell: <price>`, or only `Buy: [Out of stock]` when selling back is disabled. `OUT_OF_STOCK` is a dynamic boolean requirement for custom option layouts. The semantic color placeholders are resolved from the global server configuration; they are not PlaceholderAPI values and players cannot customize them.

Run `/shops reload` after editing `hologram-format.yml`. Invalid conditions or calculations are reported in the server log and should be corrected before relying on the display. Installations still using an exact bundled three-line layout receive the new item-details row in memory without overwriting the file. Exact old bundled white formats also receive the semantic palette in memory. Custom format strings are never migrated automatically.
