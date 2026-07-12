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
| `shopchest.remove.other` | `op` | Removes other players' shops and uses `/shop removeall`. |
| `shopchest.remove.admin` | `op` | Removes admin shops. |
| `shopchest.openOther` | `op` | Opens another player's shop inventory. Use the capital `O` spelling declared by the plugin. |
| `shopchest.notification.update` | `op` | Receives legacy update notifications when an update is marked available. |
| `shopchest.reload` | `op` | Uses `/shop reload`. |
| `shopchest.update` | `op` | Uses `/shop update`. |
| `shopchest.config` | `op` | Changes configuration through `/shop config`. |
| `shopchest.extend.other` | `op` | Extends another player's shop into a double chest. |
| `shopchest.extend.protected` | `op` | Extends a shop into a protected location. |
| `shopchest.external.bypass` | `op` | Bypasses an integrated plot, region, island, or claim denial when using a shop. |
| `shopchest.limit.*` | `op` | Removes the normal-shop limit. |

## Dynamic Permissions

`shopchest.limit.<number>` assigns a normal-shop limit. When several numeric limits apply, ShopChest uses the highest. A negative number or `shopchest.limit.*` means unlimited. Admin shops do not count toward the limit.

Creation permissions can be narrowed by material and legacy durability value:

- `shopchest.create.<MATERIAL>`
- `shopchest.create.<MATERIAL>.<durability>`
- `shopchest.create.buy.<MATERIAL>[.<durability>]`
- `shopchest.create.sell.<MATERIAL>[.<durability>]`

Material names use Bukkit enum names such as `DIAMOND` or `OAK_LOG`. A general permission such as `shopchest.create` overrides the need for its material-specific form.

No permission is required for `/shop`, `/shop info`, `/shop limits`, `/shop inspect`, or managing a player's own normal shop after it has been created.
