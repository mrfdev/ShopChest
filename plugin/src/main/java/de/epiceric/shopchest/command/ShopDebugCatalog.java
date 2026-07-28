package de.epiceric.shopchest.command;

import java.util.List;

final class ShopDebugCatalog {

    private ShopDebugCatalog() {
    }

    static List<CommandEntry> commands(String command) {
        final String root = "/" + command;
        return List.of(
                new CommandEntry(root, "Shows the permission-aware ShopChest command index.", ""),
                new CommandEntry(root + " help", "Shows the same command index explicitly.", ""),
                new CommandEntry(root + " info", "Shows setup instructions, version, and the player guide.", ""),
                new CommandEntry(root + " create <amount> <buy-price> <sell-price> [normal|admin]",
                        "Creates a shop after a 15-second supported-container selection.",
                        "shopchest.create; admin shops also require shopchest.create.admin"),
                new CommandEntry(root + " edit <amount|buy|sell> <value>",
                        "Changes one trade term after selecting an owned shop.",
                        "Resulting shop must pass the applicable shopchest.create permissions"),
                new CommandEntry(root + " edit holograms <reset|faceme|north|south|east|west>",
                        "Changes one owned shop's text-panel and icon orientation.",
                        "Resulting shop must pass the applicable shopchest.create permissions"),
                new CommandEntry(root + " limits", "Shows used and available normal-shop slots.", ""),
                new CommandEntry(root + " list [page]", "Lists every shop owned by the sender.", ""),
                new CommandEntry(root + " recent [page]",
                        "Shows recorded purchases, sales, earnings, spending, and net change.",
                        "shopchest.recent"),
                new CommandEntry(root + " inspect", "Starts a shop inspection selection.", ""),
                new CommandEntry(root + " open", "Starts a selection to open a shop container.",
                        "shopchest.openOther when the sender is not the owner"),
                new CommandEntry(root + " remove", "Starts a selection to remove a shop.",
                        "Elevated permissions apply to other players' and admin shops"),
                new CommandEntry(root + " admin list <player> [page]",
                        "Lists another player's shops with authorized staff teleport actions.",
                        "shopchest.admin.list"),
                new CommandEntry(root + " debug [status|commands|permissions|placeholders] [page]",
                        "Shows support status or paginated ShopChest metadata.",
                        "shopchest.admin.debug"),
                new CommandEntry(root + " admin debug",
                        "Compatibility alias for the debug status report.",
                        "shopchest.admin.debug"),
                new CommandEntry(root + " removeall <player>",
                        "Removes all normal and admin shops owned by a player.",
                        "shopchest.remove.other"),
                new CommandEntry(root + " reload",
                        "Reloads configuration, language, format, database, and loaded shops.",
                        "shopchest.reload"),
                new CommandEntry(root + " config <set|add|remove> <property> <value>",
                        "Changes global ShopChest configuration values.",
                        "shopchest.config"));
    }

    static List<PermissionEntry> dynamicPermissions() {
        return List.of(
                new PermissionEntry(
                        "shopchest.limit.<number>",
                        "dynamic",
                        "Sets the normal-shop limit; the highest matching numeric value wins."),
                new PermissionEntry(
                        "shopchest.create.<MATERIAL>[.<durability>]",
                        "dynamic",
                        "Allows normal shop creation for one material and optional legacy durability."),
                new PermissionEntry(
                        "shopchest.create.buy.<MATERIAL>[.<durability>]",
                        "dynamic",
                        "Allows customer-buy shop creation for one material variant."),
                new PermissionEntry(
                        "shopchest.create.sell.<MATERIAL>[.<durability>]",
                        "dynamic",
                        "Allows customer-sell shop creation for one material variant."));
    }

    static List<PlaceholderEntry> placeholders() {
        return List.of(
                new PlaceholderEntry("%VENDOR%", "Shop owner's current name."),
                new PlaceholderEntry("%AMOUNT%", "Number of products in one configured trade."),
                new PlaceholderEntry("%ITEMNAME%", "Localized, custom, or overridden product name."),
                new PlaceholderEntry("%ITEM-DETAILS%", "Combined enchantment and potion details."),
                new PlaceholderEntry("%ENCHANTMENT%", "Stored or direct enchantment names and levels."),
                new PlaceholderEntry("%POTION-EFFECT%", "Potion effects with amplifier and duration."),
                new PlaceholderEntry("%BUY-PRICE%", "Formatted customer price or the out-of-stock state."),
                new PlaceholderEntry("%SELL-PRICE%", "Formatted payout for selling to the shop."),
                new PlaceholderEntry("%STOCK%", "Matching product amount in a normal shop container."),
                new PlaceholderEntry("%MAX-STACK%", "Product's maximum stack size."),
                new PlaceholderEntry("%CHEST-SPACE%", "Matching product amount that can still fit."),
                new PlaceholderEntry("%DURABILITY%", "Legacy durability value stored for the product."),
                new PlaceholderEntry("%COLOR-OWNER%", "Configured owner text color."),
                new PlaceholderEntry("%COLOR-QUANTITY%", "Configured quantity text color."),
                new PlaceholderEntry("%COLOR-ITEM%", "Configured product-name text color."),
                new PlaceholderEntry("%COLOR-LABEL%", "Configured Buy/Sell label color."),
                new PlaceholderEntry("%COLOR-BUY-VALUE%", "Configured customer-price color."),
                new PlaceholderEntry("%COLOR-SELL-VALUE%", "Configured shop-payout color."),
                new PlaceholderEntry("%COLOR-SEPARATOR%", "Configured separator text color."),
                new PlaceholderEntry("%COLOR-ADMIN%", "Configured admin-shop heading color."),
                new PlaceholderEntry("%COLOR-UNAVAILABLE%", "Configured unavailable-state color."),
                new PlaceholderEntry("%COLOR-RESET%", "Clears active color and text decoration."),
                new PlaceholderEntry("%MUSIC-TITLE%", "Reserved; no runtime provider is connected."),
                new PlaceholderEntry("%BANNER-PATTERN-NAME%", "Reserved; no runtime provider is connected."),
                new PlaceholderEntry("%GENERATION%", "Reserved; no runtime provider is connected."));
    }

    record CommandEntry(String usage, String description, String permission) {
    }

    record PermissionEntry(String node, String defaultValue, String description) {
    }

    record PlaceholderEntry(String token, String description) {
    }
}
