package de.epiceric.shopchest.language;

import net.md_5.bungee.api.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MessageRegistryLoader {

    private final Map<String, String> storedMessages;

    public MessageRegistryLoader(@NotNull Map<String, String> storedMessages) {
        this.storedMessages = storedMessages;
    }

    @NotNull
    public String[] getMessages() {
        final String[] messages = new String[Message.values().length];
        // Add ShopChest Messages
        registerAll(messages);
        return messages;
    }

    private void registerAll(@NotNull String[] messages) {
        register(messages, Message.SHOP_CREATED, "message.shop-created", "&6You were withdrawn &c%CREATION-PRICE% &6to create this shop.");
        register(messages, Message.ADMIN_SHOP_CREATED, "message.admin-shop-created", "&6You were withdrawn &c%CREATION-PRICE% &6to create this admin shop.");
        register(messages, Message.CHEST_ALREADY_SHOP, "message.chest-already-shop", "&cChest already shop.");
        register(messages, Message.CHEST_BLOCKED, "message.chest-blocked", "&cThere must not be a block above the chest.");
        register(messages, Message.DOUBLE_CHEST_BLOCKED, "message.double-chest-blocked", "&cThere must not be a block above the chest.");
        register(messages, Message.SHOP_REMOVED, "message.shop-removed", "&6Shop removed.");
        register(messages, Message.SHOP_REMOVED_REFUND, "message.shop-removed-refund", "&6Shop removed. You were refunded &c%CREATION-PRICE%&6.");
        register(messages, Message.ALL_SHOPS_REMOVED, "message.all-shops-removed", "&6Removed all (&c%AMOUNT%&6) shop/s of &c%VENDOR%&6.");
        register(messages, Message.CHEST_NO_SHOP, "message.chest-no-shop", "&cChest is not a shop.");
        register(messages, Message.SHOP_CREATE_NOT_ENOUGH_MONEY, "message.shop-create-not-enough-money", "&cNot enough money. You need &6%CREATION-PRICE% &cto create a shop.");
        register(messages, Message.SHOP_INFO_VENDOR, "message.shopInfo.vendor", "&6Vendor: &e%VENDOR%");
        register(messages, Message.SHOP_INFO_PRODUCT, "message.shopInfo.product", "&6Product: &e%AMOUNT% x %ITEMNAME%");
        register(messages, Message.SHOP_INFO_STOCK, "message.shopInfo.stock", "&6In Stock: &e%STOCK%");
        register(messages, Message.SHOP_INFO_CHEST_SPACE, "message.shopInfo.chest-space", "&6Space in chest: &e%CHEST-SPACE%");
        register(messages, Message.SHOP_INFO_PRICE, "message.shopInfo.price", "&6Price: Buy: &e%BUY-PRICE%&6 Sell: &e%SELL-PRICE%");
        register(messages, Message.SHOP_INFO_DISABLED, "message.shopInfo.disabled", "&7Disabled");
        register(messages, Message.SHOP_INFO_NORMAL, "message.shopInfo.is-normal", "&6Type: &eNormal");
        register(messages, Message.SHOP_INFO_ADMIN, "message.shopInfo.is-admin", "&6Type: &eAdmin");
        register(messages, Message.BUY_SELL_DISABLED, "message.buy-and-sell-disabled", "&cYou can't create a shop with buying and selling disabled.");
        register(messages, Message.BUY_SUCCESS, "message.buy-success", "&aYou bought &6%AMOUNT% x %ITEMNAME%&a for &6%BUY-PRICE%&a from &6%VENDOR%&a.");
        register(messages, Message.BUY_SUCCESS_ADMIN, "message.buy-success-admin", "&aYou bought &6%AMOUNT% x %ITEMNAME%&a for &6%BUY-PRICE%&a.");
        register(messages, Message.SELL_SUCCESS, "message.sell-success", "&aYou sold &6%AMOUNT% x %ITEMNAME%&a for &6%SELL-PRICE%&a to &6%VENDOR%&a.");
        register(messages, Message.SELL_SUCCESS_ADMIN, "message.sell-success-admin", "&aYou sold &6%AMOUNT% x %ITEMNAME%&a for &6%SELL-PRICE%&a.");
        register(messages, Message.SOMEONE_BOUGHT, "message.someone-bought", "&6%PLAYER% &abought &6%AMOUNT% x %ITEMNAME%&a for &6%BUY-PRICE%&a from your shop.");
        register(messages, Message.SOMEONE_SOLD, "message.someone-sold", "&6%PLAYER% &asold &6%AMOUNT% x %ITEMNAME%&a for &6%SELL-PRICE%&a to your shop.");
        register(messages, Message.REVENUE_WHILE_OFFLINE, "message.revenue-while-offline", "&6While you were offline, your shops have made a revenue of &c%REVENUE%&6.");
        register(messages, Message.NOT_ENOUGH_INVENTORY_SPACE, "message.not-enough-inventory-space", "&cNot enough space in inventory.");
        register(messages, Message.CHEST_NOT_ENOUGH_INVENTORY_SPACE, "message.chest-not-enough-inventory-space", "&cShop is full.");
        register(messages, Message.NOT_ENOUGH_MONEY, "message.not-enough-money", "&cNot enough money.");
        register(messages, Message.NOT_ENOUGH_ITEMS, "message.not-enough-items", "&cNot enough items.");
        register(messages, Message.VENDOR_NOT_ENOUGH_MONEY, "message.vendor-not-enough-money", "&cVendor has not enough money.");
        register(messages, Message.OUT_OF_STOCK, "message.out-of-stock", "&cShop out of stock.");
        register(messages, Message.VENDOR_OUT_OF_STOCK, "message.vendor-out-of-stock", "&cYour shop that sells &6%AMOUNT% x %ITEMNAME% &cis out of stock.");
        register(messages, Message.ERROR_OCCURRED, "message.error-occurred", "&cAn error occurred: %ERROR%");
        register(messages, Message.AMOUNT_PRICE_NOT_NUMBER, "message.amount-and-price-not-number", "&cAmount and price must be a number.");
        register(messages, Message.AMOUNT_IS_ZERO, "message.amount-is-zero", "&cAmount must be greater than 0.");
        register(messages, Message.PRICES_CONTAIN_DECIMALS, "message.prices-contain-decimals", "&cPrices must not contain decimals.");
        register(messages, Message.NO_ITEM_IN_HAND, "message.no-item-in-hand", "&cNo item in hand");
        register(messages, Message.CLICK_CHEST_CREATE, "message.click-chest-to-create-shop", "&aClick a chest within 15 seconds to create a shop.");
        register(messages, Message.CLICK_CHEST_REMOVE, "message.click-chest-to-remove-shop", "&aClick a shop within 15 seconds to remove it.");
        register(messages, Message.CLICK_CHEST_INFO, "message.click-chest-for-info", "&aClick a shop within 15 seconds to retrieve information.");
        register(messages, Message.CLICK_CHEST_OPEN, "message.click-chest-to-open-shop", "&aClick a shop within 15 seconds to open it.");
        register(messages, Message.CLICK_TO_CONFIRM, "message.click-to-confirm", "&aClick again to confirm.");
        register(messages, Message.OPENED_SHOP, "message.opened-shop", "&aYou opened %VENDOR%'s shop.");
        register(messages, Message.CANNOT_BREAK_SHOP, "message.cannot-break-shop", "&cYou can't break a shop.");
        register(messages, Message.CANNOT_SELL_BROKEN_ITEM, "message.cannot-sell-broken-item", "&cYou can't sell a broken item.");
        register(messages, Message.BUY_PRICE_TOO_LOW, "message.buy-price-too-low", "&cThe buy price must be higher than %MIN-PRICE%.");
        register(messages, Message.SELL_PRICE_TOO_LOW, "message.sell-price-too-low", "&cThe sell price must be higher than %MIN-PRICE%.");
        register(messages, Message.BUY_PRICE_TOO_HIGH, "message.buy-price-too-high", "&cThe buy price must be lower than %MAX-PRICE%.");
        register(messages, Message.SELL_PRICE_TOO_HIGH, "message.sell-price-too-high", "&cThe sell price must be lower than %MAX-PRICE%.");
        register(messages, Message.BUYING_DISABLED, "message.buying-disabled", "&cBuying is disabled at this shop.");
        register(messages, Message.SELLING_DISABLED, "message.selling-disabled", "&cSelling is disabled at this shop.");
        register(messages, Message.RELOADED_SHOPS, "message.reloaded-shops", "&aSuccessfully reloaded %AMOUNT% shop/s.");
        register(messages, Message.SHOP_LIMIT_REACHED, "message.shop-limit-reached", "&cYou reached your limit of &6%LIMIT% &cshop/s.");
        register(messages, Message.OCCUPIED_SHOP_SLOTS, "message.occupied-shop-slots", "&6You have &c%AMOUNT%/%LIMIT% &6shop slot/s occupied.");
        register(messages, Message.CANNOT_SELL_ITEM, "message.cannot-sell-item", "&cYou cannot create a shop with this item.");
        register(messages, Message.USE_IN_CREATIVE, "message.use-in-creative", "&cYou cannot use a shop in creative mode.");
        register(messages, Message.SELECT_ITEM, "message.select-item", "&aOpen your inventory, and drop an item to select it.");
        register(messages, Message.ITEM_SELECTED, "message.item-selected", "&aItem has been selected: &6%ITEMNAME%");
        register(messages, Message.CREATION_CANCELLED, "message.creation-cancelled", "&cShop creation has been cancelled.");
        register(messages, Message.UPDATE_AVAILABLE, "message.update.update-available", "&6&lVersion &c%VERSION% &6of &cShopChest &6is available &chere.");
        register(messages, Message.UPDATE_CLICK_TO_DOWNLOAD, "message.update.click-to-download", "Click to download");
        register(messages, Message.UPDATE_NO_UPDATE, "message.update.no-update", "&6&lNo new update available.");
        register(messages, Message.UPDATE_CHECKING, "message.update.checking", "&6&lChecking for updates...");
        register(messages, Message.UPDATE_ERROR, "message.update.error", "&c&lError while checking for updates.");
        register(messages, Message.NO_PERMISSION_CREATE, "message.noPermission.create", "&cYou don't have permission to create a shop.");
        register(messages, Message.NO_PERMISSION_CREATE_ADMIN, "message.noPermission.create-admin", "&cYou don't have permission to create an admin shop.");
        register(messages, Message.NO_PERMISSION_CREATE_PROTECTED, "message.noPermission.create-protected", "&cYou don't have permission to create a shop on a protected chest.");
        register(messages, Message.NO_PERMISSION_OPEN_OTHERS, "message.noPermission.open-others", "&cYou don't have permission to open this chest.");
        register(messages, Message.NO_PERMISSION_BUY, "message.noPermission.buy", "&cYou don't have permission to buy something.");
        register(messages, Message.NO_PERMISSION_SELL, "message.noPermission.sell", "&cYou don't have permission to sell something.");
        register(messages, Message.NO_PERMISSION_BUY_HERE, "message.noPermission.buy-here", "&cYou don't have permission to buy something here.");
        register(messages, Message.NO_PERMISSION_SELL_HERE, "message.noPermission.sell-here", "&cYou don't have permission to sell something here.");
        register(messages, Message.NO_PERMISSION_REMOVE_OTHERS, "message.noPermission.remove-others", "&cYou don't have permission to remove this shop.");
        register(messages, Message.NO_PERMISSION_REMOVE_ADMIN, "message.noPermission.remove-admin", "&cYou don't have permission to remove an admin shop.");
        register(messages, Message.NO_PERMISSION_RELOAD, "message.noPermission.reload", "&cYou don't have permission to reload the shops.");
        register(messages, Message.NO_PERMISSION_UPDATE, "message.noPermission.update", "&cYou don't have permission to check for updates.");
        register(messages, Message.NO_PERMISSION_CONFIG, "message.noPermission.config", "&cYou don't have permission to change configuration values.");
        register(messages, Message.NO_PERMISSION_EXTEND_OTHERS, "message.noPermission.extend-others", "&cYou don't have permission to extend this chest.");
        register(messages, Message.NO_PERMISSION_EXTEND_PROTECTED, "message.noPermission.extend-protected", "&cYou don't have permission to extend this chest to here.");
        register(messages, Message.COMMAND_DESC_HEADER, "message.commandDescription.header", "&6==== &c/%COMMAND% &6Help");
        register(messages, Message.COMMAND_DESC_FOOTER, "message.commandDescription.footer", "&6==== End");
        register(messages, Message.COMMAND_DESC_CREATE, "message.commandDescription.create", "&a/%COMMAND% create <amount> <buy-price> <sell-price> - Create a shop.");
        register(messages, Message.COMMAND_DESC_CREATE_ADMIN, "message.commandDescription.create-admin", "&a/%COMMAND% create <amount> <buy-price> <sell-price> [admin] - Create a shop.");
        register(messages, Message.COMMAND_DESC_REMOVE, "message.commandDescription.remove", "&a/%COMMAND% remove - Remove a shop.");
        register(messages, Message.COMMAND_DESC_INFO, "message.commandDescription.info-plugin", "&a/%COMMAND% info - View ShopChest information and documentation.");
        register(messages, Message.COMMAND_DESC_INSPECT, "message.commandDescription.inspect", "&a/%COMMAND% inspect - Retrieve information about a shop.");
        register(messages, Message.COMMAND_DESC_REMOVEALL, "message.commandDescription.removeall", "&a/%COMMAND% removeall - Remove all shops of a player.");
        register(messages, Message.COMMAND_DESC_RELOAD, "message.commandDescription.reload", "&a/%COMMAND% reload - Reload shops.");
        register(messages, Message.COMMAND_DESC_UPDATE, "message.commandDescription.update", "&a/%COMMAND% update - Check for Updates.");
        register(messages, Message.COMMAND_DESC_LIMITS, "message.commandDescription.limits", "&a/%COMMAND% limits - View shop limits.");
        register(messages, Message.COMMAND_DESC_OPEN, "message.commandDescription.open", "&a/%COMMAND% open - Open a shop.");
        register(messages, Message.COMMAND_DESC_CONFIG, "message.commandDescription.config", "&a/%COMMAND% config <set|add|remove> <property> <value> - Change configuration values.");
        register(messages, Message.CHANGED_CONFIG_SET, "message.config.set", "&6Changed &a%PROPERTY% &6to &a%VALUE%&6.");
        register(messages, Message.CHANGED_CONFIG_REMOVED, "message.config.removed", "&6Removed &a%VALUE% &6from &a%PROPERTY%&6.");
        register(messages, Message.CHANGED_CONFIG_ADDED, "message.config.added", "&6Added &a%VALUE% &6to &a%PROPERTY%&6.");
    }

    private void register(@NotNull String[] messages, @NotNull Message message, @NotNull String path, @Nullable String defaultValue) {
        final String rawValue = storedMessages.getOrDefault(path, defaultValue);
        messages[message.ordinal()] = ChatColor.translateAlternateColorCodes('&', rawValue);
    }

}
