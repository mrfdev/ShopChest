# Permissions

Defaults below come from `plugin.yml`. `true` means all players, and `op` means server operators by default.

| Permission | Default | Effect |
| --- | --- | --- |
| `shopchest.*` | `op` | Grants every declared ShopChest permission, including unlimited shops. |
| `shopchest.create` | `true` | Creates normal shops and grants both directional creation permissions. |
| `shopchest.create.buy` | `true` | Creates shops with buying from the shop enabled. |
| `shopchest.create.sell` | `true` | Creates shops that buy items from players. |
| `shopchest.create.admin` | `op` | Creates admin shops and includes normal creation. |
| `shopchest.create.protected` | `op` | Allows creation when a protection event cancels normal creation. |
| `shopchest.buy` | `true` | Buys products from shops. |
| `shopchest.sell` | `true` | Sells products to shops. |
| `shopchest.remove.other` | `op` | Removes other players' shops and uses `/shops removeall`. |
| `shopchest.remove.admin` | `op` | Removes admin shops. |
| `shopchest.openOther` | `op` | Opens another player's shop inventory. Use the capital `O` spelling declared by the plugin. |
| `shopchest.reload` | `op` | Uses `/shops reload`. |
| `shopchest.config` | `op` | Changes configuration through `/shops config`. |
| `shopchest.extend.other` | `op` | Extends another player's shop into a double chest. |
| `shopchest.extend.protected` | `op` | Extends a shop into a protected location. |
| `shopchest.external.bypass` | `op` | Bypasses an integrated plot, region, island, or claim denial when using a shop. |
| `shopchest.recent` | `true` | Uses `/shops recent` to view the player's own recorded transaction history. |
| `shopchest.profile` | `true` | Creates, edits, and views public Storefront Profiles, scoped shop pages, and Featured Listings. |
| `shopchest.search` | `true` | Searches in-stock normal player shops by exact base material. |
| `shopchest.advertise` | `true` | Purchases an Advertising Pass with exact captured tokens and previews, queues, checks, or cancels the player's storefront advertisement. |
| `shopchest.admin` | `op` | Parent permission for ShopChest administration commands. |
| `shopchest.admin.list` | `op` | Lists another player's registered shops and allows in-game staff to teleport to a listed shop. |
| `shopchest.admin.audit` | `op` | Runs the read-only persisted-shop maintenance audit without loading chunks. Output includes owner UUIDs, world names, and exact coordinates. |
| `shopchest.admin.debug` | `op` | Uses `/shops debug` for the copyable support report and command, permission, and internal-placeholder catalogs. |
| `shopchest.admin.storefront` | `op` | Hides, shows, suspends, unsuspends, or clears public storefront data without changing shop records. |
| `shopchest.admin.advertise` | `op` | Checks, captures, or clears the authoritative advertising currency ItemStack template. |
| `shopchest.admin.export` | `op` | Creates a player-safe marketplace JSON/CSV snapshot for staff review; it does not publish the files. |
| `shopchest.limit.*` | `op` | Removes the normal-shop limit. |

Keep `shopchest.admin.audit` restricted to trusted staff. Audit output is not a
privacy-filtered support report; review and redact it before sharing.

## Dynamic Permissions

`shopchest.limit.<number>` assigns a normal-shop limit. When several numeric limits apply, ShopChest uses the highest. A negative number or `shopchest.limit.*` means unlimited. Admin shops do not count toward the limit.

Creation permissions can be narrowed by material and legacy durability value:

- `shopchest.create.<MATERIAL>`
- `shopchest.create.<MATERIAL>.<durability>`
- `shopchest.create.buy.<MATERIAL>[.<durability>]`
- `shopchest.create.sell.<MATERIAL>[.<durability>]`

Material names use Bukkit enum names such as `DIAMOND` or `OAK_LOG`. A general permission such as `shopchest.create` overrides the need for its material-specific form.

`/shops edit` uses these same directional and material permissions for the
complete resulting shop. Editing an owned admin shop additionally requires
`shopchest.create.admin`; there is no permission to edit another player's shop.
The command never changes ownership or shop type.

No permission is required for `/shops`, `/shops info`, `/shops limits`,
`/shops list`, `/shops inspect`, or removing a player's own normal shop.
Editing is the exception described above. `shopchest.recent`,
`shopchest.profile`, `shopchest.search`, and `shopchest.advertise` are granted
to all players by default, but can be revoked independently.

`shopchest.admin.storefront` and `shopchest.admin.advertise` can change public
or security-sensitive state. Grant them only to trusted staff. Currency capture
records the complete item in the administrator's main hand; currency clear
immediately prevents new pass purchases until another genuine token is
captured. `shopchest.admin.export` can write public owner and storefront text
to review files, so its output should be inspected before it reaches the docs
site.
