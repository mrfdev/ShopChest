# Hologram Placeholders

ShopChest does not register a PlaceholderAPI expansion. The placeholders below belong only to `plugins/ShopChest/hologram-format.yml` and are replaced while ShopChest builds its own shop holograms.

| Placeholder | Output |
| --- | --- |
| `%VENDOR%` | Shop owner's current name. |
| `%AMOUNT%` | Number of products in one configured trade. |
| `%ITEMNAME%` | Localized or custom product name. |
| `%BUY-PRICE%` | Vault-formatted price a player pays to buy. |
| `%SELL-PRICE%` | Vault-formatted amount a player receives for selling. |
| `%STOCK%` | Matching product items currently in a normal shop chest. |
| `%MAX-STACK%` | Product's maximum stack size. |
| `%CHEST-SPACE%` | Number of matching product items that can still fit in the chest. |
| `%DURABILITY%` | Legacy durability value stored for the product. |

The format file also lists `%ENCHANTMENT%`, `%POTION-EFFECT%`, `%MUSIC-TITLE%`, `%BANNER-PATTERN-NAME%`, and `%GENERATION%`. Their providers are not currently connected in the runtime formatter, so public layouts should not depend on them.

## Conditions and Calculations

Each line contains ordered options. The first option whose requirements pass is displayed. Verified requirement names are `VENDOR`, `AMOUNT`, `ITEM_TYPE`, `ITEM_NAME`, `HAS_ENCHANTMENT`, `BUY_PRICE`, `SELL_PRICE`, `HAS_POTION_EFFECT`, `IS_MUSIC_DISC`, `IS_POTION_EXTENDED`, `IS_WRITTEN_BOOK`, `IS_BANNER_PATTERN`, `ADMIN_SHOP`, `NORMAL_SHOP`, `IN_STOCK`, `MAX_STACK`, `CHEST_SPACE`, and `DURABILITY`.

Conditions support boolean logic and comparisons, for example `NORMAL_SHOP`, `BUY_PRICE > 0`, or `(IN_STOCK > 0) || ADMIN_SHOP`. Braced expressions can calculate numeric output from placeholders, such as `{%STOCK% / 64}`. Options are evaluated from top to bottom.

Run `/shop reload` after editing `hologram-format.yml`. Invalid conditions or calculations are reported in the server log and should be corrected before relying on the display.
