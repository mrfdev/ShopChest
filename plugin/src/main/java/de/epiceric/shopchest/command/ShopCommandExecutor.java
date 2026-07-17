package de.epiceric.shopchest.command;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.diagnostics.ShopChestSupportReport;
import de.epiceric.shopchest.event.*;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.MessageRegistry;
import de.epiceric.shopchest.language.Replacement;
import de.epiceric.shopchest.display.HologramTextFormatter;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.Shop.ShopType;
import de.epiceric.shopchest.shop.ShopProduct;
import de.epiceric.shopchest.sql.DatabaseDiagnostics;
import de.epiceric.shopchest.sql.RecentTransaction;
import de.epiceric.shopchest.sql.RecentTransactionPage;
import de.epiceric.shopchest.utils.*;
import de.epiceric.shopchest.utils.ClickType.CreateClickType;
import de.epiceric.shopchest.utils.ClickType.SelectClickType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

class ShopCommandExecutor implements CommandExecutor {

    private static final String DOCS_URL =
            "https://docs.1moreblock.com/player-guides/custom-server-plugins/shopchest/";
    private static final int SHOP_LIST_PAGE_SIZE = 8;
    private static final int SHOP_LIST_ITEM_NAME_LENGTH = 36;
    private static final int RECENT_PAGE_SIZE = 8;
    private static final int RECENT_ITEM_NAME_LENGTH = 32;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final ShopChest plugin;
    private final ShopUtils shopUtils;
    private final Map<UUID, Map<Integer, Location>> adminTeleportTargets = new ConcurrentHashMap<>();
    private static final Enchantment UNBREAKING_ENCHANT = Enchantment.UNBREAKING;

    ShopCommandExecutor(ShopChest plugin) {
        this.plugin = plugin;
        this.shopUtils = plugin.getShopUtils();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        List<ShopSubCommand> subCommands = plugin.getShopCommand().getSubCommands();

        if (args.length > 0) {
            String _subCommand = args[0];
            ShopSubCommand subCommand = null;

            for (ShopSubCommand shopSubCommand : subCommands) {
                if (shopSubCommand.getName().equalsIgnoreCase(_subCommand)) {
                    subCommand = shopSubCommand;
                    break;
                }
            }

            if (subCommand == null) {
                return false;
            }

            if (subCommand.getName().equalsIgnoreCase("reload")) {
                if (sender.hasPermission(Permissions.RELOAD)) {
                    reload(sender);
                } else {
                    sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_RELOAD));
                }
            } else if (subCommand.getName().equalsIgnoreCase("config")) {
                if (sender.hasPermission(Permissions.CONFIG)) {
                    return args.length >= 4 && changeConfig(sender, args);
                } else {
                    sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_CONFIG));
                }
            } else if (subCommand.getName().equalsIgnoreCase("removeall")) {
                if (sender.hasPermission(Permissions.REMOVE_OTHER)) {
                    if (args.length >= 2) {
                        removeAll(sender, args);
                    } else {
                        return false;
                    }
                } else {
                    sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_REMOVE_OTHERS));
                }
            } else if (subCommand.getName().equalsIgnoreCase("admin")) {
                return handleAdminCommand(sender, args);
            } else if (subCommand.getName().equalsIgnoreCase("info")) {
                if (args.length >= 2 && args[1].equalsIgnoreCase("shop")) {
                    if (sender instanceof Player) {
                        info((Player) sender);
                    } else {
                        sender.sendMessage(Component.text("Only players can inspect a shop.", NamedTextColor.RED));
                    }
                } else {
                    sendPluginInfo(sender);
                }
            } else if (subCommand.getName().equalsIgnoreCase("help")) {
                return false;
            } else {
                if (sender instanceof Player) {
                    Player p = (Player) sender;

                    if (subCommand.getName().equalsIgnoreCase("create")) {
                        if (args.length == 4) {
                            create(args, Shop.ShopType.NORMAL, p);
                        } else if (args.length == 5) {
                            if (args[4].equalsIgnoreCase("normal")) {
                                create(args, Shop.ShopType.NORMAL, p);
                            } else if (args[4].equalsIgnoreCase("admin")) {
                                if (p.hasPermission(Permissions.CREATE_ADMIN)) {
                                    create(args, Shop.ShopType.ADMIN, p);
                                } else {
                                    p.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_CREATE_ADMIN));
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else if (subCommand.getName().equalsIgnoreCase("remove")) {
                        remove(p);
                    } else if (subCommand.getName().equalsIgnoreCase("inspect")) {
                        info(p);
                    } else if (subCommand.getName().equalsIgnoreCase("limits")) {
                        plugin.debug(p.getName() + " is viewing his shop limits: " + shopUtils.getShopAmount(p) + "/" + shopUtils.getShopLimit(p));
                        int limit = shopUtils.getShopLimit(p);
                        p.sendMessage(messageRegistry.getMessage(Message.OCCUPIED_SHOP_SLOTS,
                                new Replacement(Placeholder.LIMIT, (limit < 0 ? "∞" : String.valueOf(limit))),
                                new Replacement(Placeholder.AMOUNT, String.valueOf(shopUtils.getShopAmount(p)))));
                    } else if (subCommand.getName().equalsIgnoreCase("open")) {
                        open(p);
                    } else if (subCommand.getName().equalsIgnoreCase("list")) {
                        if (args.length > 2) {
                            return false;
                        }
                        final Integer page = parsePage(p, args.length == 2 ? args[1] : null);
                        if (page != null) {
                            listShops(p, p, page, false);
                        }
                    } else if (subCommand.getName().equalsIgnoreCase("recent")) {
                        if (!p.hasPermission(Permissions.RECENT)) {
                            p.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_RECENT));
                        } else if (args.length > 2) {
                            return false;
                        } else {
                            final Integer page = parsePage(p, args.length == 2 ? args[1] : null);
                            if (page != null) {
                                listRecentTransactions(p, page);
                            }
                        }
                    } else {
                        return false;
                    }
                }
            }

            return true;
        }

        return false;
    }

    private void listRecentTransactions(Player player, int requestedPage) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        if (!Config.enableEconomyLog) {
            player.sendMessage(messageRegistry.getMessage(Message.RECENT_LOGGING_DISABLED));
        }
        player.sendMessage(messageRegistry.getMessage(Message.RECENT_LOADING));

        plugin.getShopDatabase().getRecentTransactions(
                player.getUniqueId(),
                requestedPage,
                RECENT_PAGE_SIZE,
                new Callback<RecentTransactionPage>(plugin) {
                    @Override
                    public void onResult(RecentTransactionPage result) {
                        if (player.isOnline()) {
                            displayRecentTransactions(player, result);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (throwable != null) {
                            plugin.debug(throwable);
                        }
                        if (player.isOnline()) {
                            player.sendMessage(messageRegistry.getMessage(Message.RECENT_ERROR));
                        }
                    }
                });
    }

    private void displayRecentTransactions(Player player, RecentTransactionPage page) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        if (page.entries().isEmpty()) {
            player.sendMessage(messageRegistry.getMessage(Message.RECENT_EMPTY));
            return;
        }

        player.sendMessage(" ");
        player.sendMessage(messageRegistry.getMessage(
                Message.RECENT_HEADER,
                new Replacement(Placeholder.PAGE, page.page()),
                new Replacement(Placeholder.PAGES, page.pageCount()),
                new Replacement(Placeholder.AMOUNT, page.totalEntries())));

        double earned = 0;
        double spent = 0;
        for (RecentTransaction transaction : page.entries()) {
            final double moneyDelta = transaction.moneyDelta(player.getUniqueId());
            if (moneyDelta > 0) {
                earned += moneyDelta;
            } else if (moneyDelta < 0) {
                spent -= moneyDelta;
            }

            final Message entryMessage = recentEntryMessage(transaction.perspective(player.getUniqueId()));
            if (entryMessage == null) {
                continue;
            }
            final String itemName = HologramTextFormatter.sanitizeItemName(
                    transaction.productName(), RECENT_ITEM_NAME_LENGTH);
            final String counterparty = HologramTextFormatter.sanitizeItemName(
                    transaction.counterparty(player.getUniqueId()), 24);
            final String signedPrice = formatSignedMoney(moneyDelta);
            final String entry = messageRegistry.getMessage(
                    entryMessage,
                    new Replacement(Placeholder.AMOUNT, transaction.amount()),
                    new Replacement(Placeholder.ITEM_NAME, itemName),
                    new Replacement(Placeholder.COUNTERPARTY, counterparty),
                    new Replacement(Placeholder.PRICE, signedPrice));
            player.sendMessage(LEGACY_SERIALIZER.deserialize(entry)
                    .hoverEvent(buildRecentHover(
                            transaction, itemName, counterparty, signedPrice)));
        }

        player.sendMessage(messageRegistry.getMessage(
                Message.RECENT_SUMMARY,
                new Replacement(Placeholder.EARNED, plugin.getEconomy().format(earned)),
                new Replacement(Placeholder.SPENT, plugin.getEconomy().format(spent)),
                new Replacement(Placeholder.NET, formatSignedMoney(earned - spent))));
        sendRecentNavigation(player, page);
        player.sendMessage(" ");
    }

    private Message recentEntryMessage(RecentTransaction.Perspective perspective) {
        return switch (perspective) {
            case PLAYER_BOUGHT -> Message.RECENT_ENTRY_PLAYER_BUY;
            case PLAYER_SOLD -> Message.RECENT_ENTRY_PLAYER_SELL;
            case SHOP_SOLD -> Message.RECENT_ENTRY_SHOP_BUY;
            case SHOP_BOUGHT -> Message.RECENT_ENTRY_SHOP_SELL;
            case UNRELATED -> null;
        };
    }

    private Component buildRecentHover(
            RecentTransaction transaction,
            String itemName,
            String counterparty,
            String signedPrice
    ) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final String world = HologramTextFormatter.sanitizeItemName(transaction.world(), 32);
        final List<String> lines = List.of(
                messageRegistry.getMessage(Message.RECENT_HOVER_HEADER),
                messageRegistry.getMessage(
                        Message.RECENT_HOVER_DATE,
                        new Replacement(Placeholder.TIME, transaction.timestamp())),
                messageRegistry.getMessage(
                        Message.RECENT_HOVER_COUNTERPARTY,
                        new Replacement(Placeholder.COUNTERPARTY, counterparty)),
                messageRegistry.getMessage(
                        Message.RECENT_HOVER_ITEM,
                        new Replacement(Placeholder.AMOUNT, transaction.amount()),
                        new Replacement(Placeholder.ITEM_NAME, itemName)),
                messageRegistry.getMessage(
                        Message.RECENT_HOVER_MONEY,
                        new Replacement(Placeholder.PRICE, signedPrice),
                        new Replacement(
                                Placeholder.UNIT_PRICE,
                                plugin.getEconomy().format(transaction.unitPrice()))),
                messageRegistry.getMessage(
                        Message.RECENT_HOVER_SHOP,
                        new Replacement(Placeholder.SHOP_ID, transaction.shopId()),
                        new Replacement(Placeholder.WORLD, world),
                        new Replacement(Placeholder.X, transaction.x()),
                        new Replacement(Placeholder.Y, transaction.y()),
                        new Replacement(Placeholder.Z, transaction.z())));

        Component hover = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                hover = hover.append(Component.newline());
            }
            hover = hover.append(LEGACY_SERIALIZER.deserialize(lines.get(index)));
        }
        return hover;
    }

    private String formatSignedMoney(double amount) {
        if (amount > 0.0000001) {
            return "+" + plugin.getEconomy().format(amount);
        }
        if (amount < -0.0000001) {
            return "-" + plugin.getEconomy().format(-amount);
        }
        return plugin.getEconomy().format(0);
    }

    private void sendRecentNavigation(Player player, RecentTransactionPage page) {
        if (page.pageCount() <= 1) {
            return;
        }

        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        Component navigation = Component.empty();
        if (page.page() > 1) {
            navigation = navigation.append(LEGACY_SERIALIZER
                    .deserialize(messageRegistry.getMessage(Message.SHOP_LIST_PREVIOUS))
                    .clickEvent(ClickEvent.runCommand(
                            "/" + Config.mainCommandName + " recent " + (page.page() - 1))));
        }
        if (page.page() > 1 && page.page() < page.pageCount()) {
            navigation = navigation.append(Component.text("  ", NamedTextColor.GRAY));
        }
        if (page.page() < page.pageCount()) {
            navigation = navigation.append(LEGACY_SERIALIZER
                    .deserialize(messageRegistry.getMessage(Message.SHOP_LIST_NEXT))
                    .clickEvent(ClickEvent.runCommand(
                            "/" + Config.mainCommandName + " recent " + (page.page() + 1))));
        }
        player.sendMessage(navigation);
    }

    private void sendPluginInfo(CommandSender sender) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final Replacement command = new Replacement(Placeholder.COMMAND, Config.mainCommandName);
        final Replacement version = new Replacement(Placeholder.VERSION, plugin.getPluginMeta().getVersion());

        sender.sendMessage(" ");
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_HEADER, version));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_INTRO));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_STEP_PLACE));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_STEP_CREATE, command));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_STEP_CHEST));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_PRICE_HINT, command));

        if (sender instanceof Player player) {
            final Component guideLink = LegacyComponentSerializer.legacySection().deserialize(
                            messageRegistry.getMessage(Message.INFO_GUIDE))
                    .clickEvent(ClickEvent.openUrl(DOCS_URL))
                    .hoverEvent(Component.text(DOCS_URL, NamedTextColor.GRAY));
            player.sendMessage(guideLink);
        } else {
            sender.sendMessage(messageRegistry.getMessage(Message.INFO_GUIDE) + ": " + DOCS_URL);
        }

        sender.sendMessage(" ");
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        if (args.length == 1) {
            if (hasAdminCommandPermission(sender)) {
                sendAdminHelp(sender);
            } else {
                sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_ADMIN));
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("list")) {
            if (!sender.hasPermission(Permissions.ADMIN_LIST)) {
                sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_ADMIN_LIST));
                return true;
            }
            if (args.length < 3 || args.length > 4) {
                sendAdminHelp(sender);
                return true;
            }

            final OfflinePlayer owner = findOfflinePlayer(args[2]);
            if (owner == null) {
                sender.sendMessage(messageRegistry.getMessage(
                        Message.SHOP_LIST_PLAYER_NOT_FOUND,
                        new Replacement(Placeholder.PLAYER, args[2])));
                return true;
            }

            final Integer page = parsePage(sender, args.length == 4 ? args[3] : null);
            if (page != null) {
                listShops(sender, owner, page, true);
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission(Permissions.ADMIN_DEBUG)) {
                sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_ADMIN_DEBUG));
                return true;
            }
            if (args.length != 2) {
                sendAdminHelp(sender);
                return true;
            }
            sendAdminDebug(sender);
            return true;
        }

        if (args[1].equalsIgnoreCase("teleport") && args.length == 3) {
            if (!sender.hasPermission(Permissions.ADMIN_LIST)) {
                sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_ADMIN_LIST));
                return true;
            }
            if (sender instanceof Player player) {
                teleportToListedShop(player, args[2]);
            } else {
                sender.sendMessage(Component.text("Only players can teleport to a shop.", NamedTextColor.RED));
            }
            return true;
        }

        if (hasAdminCommandPermission(sender)) {
            sendAdminHelp(sender);
        } else {
            sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_ADMIN));
        }
        return true;
    }

    private boolean hasAdminCommandPermission(CommandSender sender) {
        return sender.hasPermission(Permissions.ADMIN_LIST)
                || sender.hasPermission(Permissions.ADMIN_DEBUG);
    }

    private void sendAdminHelp(CommandSender sender) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final Replacement command = new Replacement(Placeholder.COMMAND, Config.mainCommandName);

        sender.sendMessage(" ");
        sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_HELP_HEADER));
        if (sender.hasPermission(Permissions.ADMIN_LIST)) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_HELP_LIST, command));
        }
        if (sender.hasPermission(Permissions.ADMIN_DEBUG)) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_HELP_DEBUG, command));
        }
        sender.sendMessage(" ");
    }

    private void sendAdminDebug(CommandSender sender) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_DEBUG_COLLECTING));

        plugin.getShopDatabase().getDiagnostics(new Callback<DatabaseDiagnostics>(plugin) {
            @Override
            public void onResult(DatabaseDiagnostics diagnostics) {
                sendAdminDebugReport(sender, diagnostics, false);
            }

            @Override
            public void onError(Throwable throwable) {
                sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_DEBUG_DATABASE_FAILED));
                sendAdminDebugReport(
                        sender,
                        DatabaseDiagnostics.unavailable(plugin.getShopDatabase().isInitialized()),
                        true);
            }
        });
    }

    private void sendAdminDebugReport(
            CommandSender sender,
            DatabaseDiagnostics databaseDiagnostics,
            boolean databaseQueryFailed
    ) {
        final ShopChestSupportReport report = ShopChestSupportReport.create(
                plugin, databaseDiagnostics, databaseQueryFailed);
        if (!(sender instanceof Player)) {
            report.lines().forEach(sender::sendMessage);
            return;
        }

        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        sender.sendMessage(" ");
        sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_DEBUG_HEADER));
        report.lines().stream()
                .filter(this::isDebugSummaryLine)
                .forEach(line -> sender.sendMessage(Component.text(line, NamedTextColor.GRAY)));
        report.warnings().forEach(warning -> sender.sendMessage(
                Component.text("! " + warning, NamedTextColor.YELLOW)));

        final Component copyButton = LEGACY_SERIALIZER.deserialize(
                        messageRegistry.getMessage(Message.ADMIN_DEBUG_COPY))
                .hoverEvent(LEGACY_SERIALIZER.deserialize(
                        messageRegistry.getMessage(Message.ADMIN_DEBUG_COPY_HOVER)))
                .clickEvent(ClickEvent.copyToClipboard(report.plainText()));
        sender.sendMessage(copyButton);
        sender.sendMessage(" ");
    }

    private boolean isDebugSummaryLine(String line) {
        return line.startsWith("Plugin:")
                || line.startsWith("Runtime:")
                || line.startsWith("Java:")
                || line.startsWith("Platform:")
                || line.startsWith("Item naming:")
                || line.startsWith("Dependencies:")
                || line.startsWith("Economy:")
                || line.startsWith("Active hooks:")
                || line.startsWith("Database:")
                || line.startsWith("Registered shops:")
                || line.startsWith("Loaded shops:")
                || line.startsWith("Loaded visuals:")
                || line.startsWith("Warnings:");
    }

    private OfflinePlayer findOfflinePlayer(String playerNameOrUuid) {
        final Player onlinePlayer = Bukkit.getPlayerExact(playerNameOrUuid);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }

        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(playerNameOrUuid));
        } catch (IllegalArgumentException ignored) {
            return Bukkit.getOfflinePlayerIfCached(playerNameOrUuid);
        }
    }

    private Integer parsePage(CommandSender sender, String value) {
        if (value == null) {
            return 1;
        }

        try {
            final int page = Integer.parseInt(value);
            if (page > 0) {
                return page;
            }
        } catch (NumberFormatException ignored) {
            // The localized message below covers invalid values.
        }

        sender.sendMessage(plugin.getLanguageManager().getMessageRegistry()
                .getMessage(Message.SHOP_LIST_INVALID_PAGE));
        return null;
    }

    private void listShops(CommandSender sender, OfflinePlayer owner, int requestedPage, boolean adminView) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        sender.sendMessage(messageRegistry.getMessage(Message.SHOP_LIST_LOADING));

        shopUtils.getShops(owner, new Callback<Collection<Shop>>(plugin) {
            @Override
            public void onResult(Collection<Shop> result) {
                if (sender instanceof Player player && !player.isOnline()) {
                    return;
                }
                displayShopList(sender, owner, result, requestedPage, adminView);
            }

            @Override
            public void onError(Throwable throwable) {
                plugin.getLogger().severe("Failed to list shops for " + owner.getUniqueId());
                if (throwable != null) {
                    plugin.debug(throwable);
                }
                sender.sendMessage(messageRegistry.getMessage(Message.SHOP_LIST_ERROR));
            }
        });
    }

    private void displayShopList(
            CommandSender sender,
            OfflinePlayer owner,
            Collection<Shop> result,
            int requestedPage,
            boolean adminView
    ) {
        final List<Shop> shops = new ArrayList<>(result);
        shops.sort(Comparator
                .comparing((Shop shop) -> worldName(shop.getLocation()), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(shop -> shop.getLocation().getBlockX())
                .thenComparingInt(shop -> shop.getLocation().getBlockY())
                .thenComparingInt(shop -> shop.getLocation().getBlockZ())
                .thenComparingInt(Shop::getID));

        if (adminView && sender instanceof Player admin) {
            final Map<Integer, Location> targets = new HashMap<>();
            for (Shop shop : shops) {
                if (shop.getID() >= 0 && shop.getLocation() != null) {
                    targets.put(shop.getID(), shop.getLocation().clone());
                }
            }
            adminTeleportTargets.put(admin.getUniqueId(), Map.copyOf(targets));
        }

        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final String ownerName = owner.getName() == null ? owner.getUniqueId().toString() : owner.getName();

        if (shops.isEmpty()) {
            final Message emptyMessage = adminView ? Message.SHOP_LIST_ADMIN_EMPTY : Message.SHOP_LIST_EMPTY;
            sender.sendMessage(messageRegistry.getMessage(
                    emptyMessage,
                    new Replacement(Placeholder.PLAYER, ownerName)));
            return;
        }

        final PageSlice<Shop> page = PageSlice.of(shops, requestedPage, SHOP_LIST_PAGE_SIZE);
        final Message headerMessage = adminView ? Message.SHOP_LIST_ADMIN_HEADER : Message.SHOP_LIST_HEADER;

        sender.sendMessage(" ");
        sender.sendMessage(messageRegistry.getMessage(
                headerMessage,
                new Replacement(Placeholder.PLAYER, ownerName),
                new Replacement(Placeholder.PAGE, page.page()),
                new Replacement(Placeholder.PAGES, page.pageCount()),
                new Replacement(Placeholder.AMOUNT, page.totalEntries())));

        for (Shop shop : page.entries()) {
            final Location location = shop.getLocation();
            final String itemName = HologramTextFormatter.sanitizeItemName(
                    shop.getProduct().getLocalizedName(),
                    SHOP_LIST_ITEM_NAME_LENGTH);
            final ShopListStock stock = resolveShopListStock(shop);
            final String stockBadge = stock.outOfStock()
                    ? messageRegistry.getMessage(Message.SHOP_LIST_OUT_OF_STOCK)
                    : "";
            final Message entryMessage = sender instanceof Player
                    ? Message.SHOP_LIST_ENTRY
                    : Message.SHOP_LIST_CONSOLE_ENTRY;
            final String entry = messageRegistry.getMessage(
                    entryMessage,
                    new Replacement(Placeholder.SHOP_ID, shop.getID()),
                    new Replacement(Placeholder.AMOUNT, shop.getProduct().getAmount()),
                    new Replacement(Placeholder.ITEM_NAME, itemName),
                    new Replacement(Placeholder.STOCK, stockBadge),
                    new Replacement(Placeholder.WORLD, worldName(location)),
                    new Replacement(Placeholder.X, location.getBlockX()),
                    new Replacement(Placeholder.Y, location.getBlockY()),
                    new Replacement(Placeholder.Z, location.getBlockZ()));

            Component line = LEGACY_SERIALIZER.deserialize(entry);
            if (sender instanceof Player) {
                final boolean canTeleport = adminView && shop.getID() >= 0;
                line = line.hoverEvent(buildShopListHover(shop, itemName, stock, canTeleport));
                if (canTeleport) {
                    line = line.clickEvent(ClickEvent.runCommand(
                            "/" + Config.mainCommandName + " admin teleport " + shop.getID()));
                }
            }
            sender.sendMessage(line);
        }

        sendShopListNavigation(sender, owner, page, adminView);
        sender.sendMessage(" ");
    }

    private ShopListStock resolveShopListStock(Shop shop) {
        final boolean adminShop = shop.getShopType() == ShopType.ADMIN;
        if (adminShop || shop.getBuyPrice() <= 0) {
            return ShopListStock.resolve(
                    shop.getBuyPrice(),
                    adminShop,
                    false,
                    0,
                    shop.getProduct().getAmount());
        }

        final Location location = shop.getLocation();
        final World world = location == null ? null : location.getWorld();
        if (world == null || !world.isChunkLoaded(
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4)) {
            return ShopListStock.resolve(
                    shop.getBuyPrice(),
                    false,
                    false,
                    0,
                    shop.getProduct().getAmount());
        }

        final InventoryHolder inventoryHolder = shop.getInventoryHolder();
        if (inventoryHolder == null) {
            return ShopListStock.resolve(
                    shop.getBuyPrice(),
                    false,
                    false,
                    0,
                    shop.getProduct().getAmount());
        }

        final int stock = Utils.getAmount(
                inventoryHolder.getInventory(),
                shop.getProduct().getItemStack());
        return ShopListStock.resolve(
                shop.getBuyPrice(),
                false,
                true,
                stock,
                shop.getProduct().getAmount());
    }

    private Component buildShopListHover(
            Shop shop,
            String itemName,
            ShopListStock stock,
            boolean canTeleport
    ) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final Location location = shop.getLocation();
        final String disabled = messageRegistry.getMessage(Message.SHOP_INFO_DISABLED);
        final String buyPrice = shop.getBuyPrice() > 0
                ? String.valueOf(shop.getBuyPrice())
                : disabled;
        final String sellPrice = shop.getSellPrice() > 0
                ? String.valueOf(shop.getSellPrice())
                : disabled;
        final List<String> lines = new ArrayList<>(List.of(
                messageRegistry.getMessage(
                        Message.SHOP_LIST_HOVER_HEADER,
                        new Replacement(Placeholder.SHOP_ID, shop.getID())),
                messageRegistry.getMessage(
                        Message.SHOP_LIST_HOVER_ITEM,
                        new Replacement(Placeholder.AMOUNT, shop.getProduct().getAmount()),
                        new Replacement(Placeholder.ITEM_NAME, itemName)),
                messageRegistry.getMessage(
                        Message.SHOP_LIST_HOVER_PRICES,
                        new Replacement(Placeholder.BUY_PRICE, buyPrice),
                        new Replacement(Placeholder.SELL_PRICE, sellPrice)),
                messageRegistry.getMessage(
                        Message.SHOP_LIST_HOVER_STOCK,
                        new Replacement(
                                Placeholder.STOCK,
                                shopListStockText(stock, shop.getProduct().getAmount()))),
                messageRegistry.getMessage(
                        Message.SHOP_LIST_HOVER_LOCATION,
                        new Replacement(Placeholder.WORLD, worldName(location)),
                        new Replacement(Placeholder.X, location.getBlockX()),
                        new Replacement(Placeholder.Y, location.getBlockY()),
                        new Replacement(Placeholder.Z, location.getBlockZ())),
                messageRegistry.getMessage(
                        shop.getShopType() == ShopType.ADMIN
                                ? Message.SHOP_INFO_ADMIN
                                : Message.SHOP_INFO_NORMAL)));
        if (canTeleport) {
            lines.add(messageRegistry.getMessage(Message.SHOP_LIST_CLICK_TELEPORT));
        }

        Component hover = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                hover = hover.append(Component.newline());
            }
            hover = hover.append(LEGACY_SERIALIZER.deserialize(lines.get(index)));
        }
        return hover;
    }

    private String shopListStockText(ShopListStock stock, int transactionAmount) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        return switch (stock.state()) {
            case AVAILABLE -> messageRegistry.getMessage(
                    Message.SHOP_LIST_STOCK_AVAILABLE,
                    new Replacement(Placeholder.STOCK, stock.available()));
            case OUT_OF_STOCK -> messageRegistry.getMessage(
                    Message.SHOP_LIST_STOCK_OUT,
                    new Replacement(Placeholder.STOCK, stock.available()),
                    new Replacement(Placeholder.AMOUNT, transactionAmount));
            case UNKNOWN -> messageRegistry.getMessage(Message.SHOP_LIST_STOCK_UNKNOWN);
            case UNLIMITED -> messageRegistry.getMessage(Message.SHOP_LIST_STOCK_UNLIMITED);
            case NOT_SOLD -> messageRegistry.getMessage(Message.SHOP_LIST_STOCK_NOT_SOLD);
        };
    }

    private String worldName(Location location) {
        return location != null && location.getWorld() != null
                ? location.getWorld().getName()
                : "<unavailable>";
    }

    private void sendShopListNavigation(
            CommandSender sender,
            OfflinePlayer owner,
            PageSlice<Shop> page,
            boolean adminView
    ) {
        if (page.pageCount() <= 1) {
            return;
        }

        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        Component navigation = Component.empty();
        if (page.page() > 1) {
            navigation = navigation.append(LEGACY_SERIALIZER
                    .deserialize(messageRegistry.getMessage(Message.SHOP_LIST_PREVIOUS))
                    .clickEvent(ClickEvent.runCommand(shopListPageCommand(owner, page.page() - 1, adminView))));
        }
        if (page.page() > 1 && page.page() < page.pageCount()) {
            navigation = navigation.append(Component.text("  ", NamedTextColor.GRAY));
        }
        if (page.page() < page.pageCount()) {
            navigation = navigation.append(LEGACY_SERIALIZER
                    .deserialize(messageRegistry.getMessage(Message.SHOP_LIST_NEXT))
                    .clickEvent(ClickEvent.runCommand(shopListPageCommand(owner, page.page() + 1, adminView))));
        }
        sender.sendMessage(navigation);
    }

    private String shopListPageCommand(OfflinePlayer owner, int page, boolean adminView) {
        if (adminView) {
            return "/" + Config.mainCommandName + " admin list " + owner.getUniqueId() + " " + page;
        }
        return "/" + Config.mainCommandName + " list " + page;
    }

    private void teleportToListedShop(Player player, String shopIdValue) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final int shopId;
        try {
            shopId = Integer.parseInt(shopIdValue);
        } catch (NumberFormatException ignored) {
            player.sendMessage(messageRegistry.getMessage(Message.ADMIN_TELEPORT_TARGET_EXPIRED));
            return;
        }

        final Location shopLocation = adminTeleportTargets
                .getOrDefault(player.getUniqueId(), Map.of())
                .get(shopId);
        if (shopLocation == null) {
            player.sendMessage(messageRegistry.getMessage(Message.ADMIN_TELEPORT_TARGET_EXPIRED));
            return;
        }
        if (shopLocation.getWorld() == null) {
            player.sendMessage(messageRegistry.getMessage(Message.ADMIN_TELEPORT_WORLD_UNAVAILABLE));
            return;
        }

        final Location destination = shopLocation.clone().add(0.5, 1.0, 0.5);
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        player.teleportAsync(destination).whenComplete((success, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    final Message result = throwable == null && Boolean.TRUE.equals(success)
                            ? Message.ADMIN_TELEPORT_SUCCESS
                            : Message.ADMIN_TELEPORT_FAILED;
                    player.sendMessage(messageRegistry.getMessage(
                            result,
                            new Replacement(Placeholder.SHOP_ID, shopId)));
                    if (throwable != null) {
                        plugin.debug(throwable);
                    }
                }));
    }

    /**
     * A given player reloads the shops
     * @param sender The command executor
     */
    private void reload(final CommandSender sender) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        plugin.debug(sender.getName() + " is reloading the shops");

        ShopReloadEvent event = new ShopReloadEvent(sender);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.debug("Reload event cancelled");
            return;
        }

        // Reload configurations
        plugin.getShopChestConfig().reload(false, true, true);
        plugin.getHologramFormat().reload();
        plugin.getCmiWorthPriceAdvisor().refresh();
        plugin.getUpdater().restart();

        // Remove all shops
        for (Shop shop : shopUtils.getShops()) {
            shopUtils.removeShop(shop, false);
        }

        Chunk[] loadedChunks = Bukkit.getWorlds().stream().map(World::getLoadedChunks)
                .flatMap(Stream::of).toArray(Chunk[]::new);

        // Reconnect to the database and re-load shops in loaded chunks
        plugin.getShopDatabase().connect(new Callback<Integer>(plugin) {
            @Override
            public void onResult(Integer result) {
                shopUtils.loadShops(loadedChunks, new Callback<Integer>(plugin) {
                    @Override
                    public void onResult(Integer result) {
                        sender.sendMessage(messageRegistry.getMessage(Message.RELOADED_SHOPS,
                                new Replacement(Placeholder.AMOUNT, String.valueOf(result))));
                        plugin.debug(sender.getName() + " has reloaded " + result + " shops");
                    }
        
                    @Override
                    public void onError(Throwable throwable) {
                        sender.sendMessage(messageRegistry.getMessage(Message.ERROR_OCCURRED,
                                new Replacement(Placeholder.ERROR, "Failed to load shops from database")));
                        plugin.getLogger().severe("Failed to load shops");
                        if (throwable != null) plugin.getLogger().severe(throwable.getMessage());
                    }
                });
            }

            @Override
            public void onError(Throwable throwable) {
                // Database connection probably failed => disable plugin to prevent more errors
                sender.sendMessage(messageRegistry.getMessage(Message.ERROR_OCCURRED,
                        new Replacement(Placeholder.ERROR, "No database access: Disabling ShopChest")));
                plugin.getLogger().severe("No database access: Disabling ShopChest");
                if (throwable != null) plugin.getLogger().severe(throwable.getMessage());
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            }
        });
    }

    /**
     * A given player creates a shop
     * @param args Arguments of the entered command
     * @param shopType The {@link Shop.ShopType}, the shop will have
     * @param p The command executor
     */
    private void create(String[] args, Shop.ShopType shopType, final Player p) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        plugin.debug(p.getName() + " wants to create a shop");

        int amount;
        double buyPrice, sellPrice;

        // Check if amount and prices are valid
        try {
            amount = Integer.parseInt(args[1]);
            buyPrice = Double.parseDouble(args[2]);
            sellPrice = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            p.sendMessage(messageRegistry.getMessage(Message.AMOUNT_PRICE_NOT_NUMBER));
            plugin.debug(p.getName() + " has entered an invalid amount and/or prices");
            return;
        }

        if (!Utils.hasPermissionToCreateShop(p, Utils.getPreferredItemInHand(p), buyPrice > 0, sellPrice > 0)) {
            p.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_CREATE));
            plugin.debug(p.getName() + " is not permitted to create the shop");
            return;
        }

        // Check for limits
        int limit = shopUtils.getShopLimit(p);
        if (limit != -1) {
            if (shopUtils.getShopAmount(p) >= limit) {
                if (shopType != Shop.ShopType.ADMIN) {
                    p.sendMessage(messageRegistry.getMessage(Message.SHOP_LIMIT_REACHED, new Replacement(Placeholder.LIMIT, String.valueOf(limit))));
                    plugin.debug(p.getName() + " has reached the limit");
                    return;
                }
            }
        }

        if (amount <= 0) {
            p.sendMessage(messageRegistry.getMessage(Message.AMOUNT_IS_ZERO));
            plugin.debug(p.getName() + " has entered an invalid amount");
            return;
        }

        if (!Config.allowDecimalsInPrice && (buyPrice != (int) buyPrice || sellPrice != (int) sellPrice)) {
            p.sendMessage(messageRegistry.getMessage(Message.PRICES_CONTAIN_DECIMALS));
            plugin.debug(p.getName() + " has entered an invalid price");
            return;
        }

        boolean buyEnabled = buyPrice > 0;
        boolean sellEnabled = sellPrice > 0;

        if (!buyEnabled && !sellEnabled) {
            p.sendMessage(messageRegistry.getMessage(Message.BUY_SELL_DISABLED));
            plugin.debug(p.getName() + " has disabled buying and selling");
            return;
        }

        ItemStack inHand = Utils.getPreferredItemInHand(p);

        // Check if item in hand
        if (inHand == null) {
            plugin.debug(p.getName() + " does not have an item in his hand");

            if (!Config.creativeSelectItem) {
                p.sendMessage(messageRegistry.getMessage(Message.NO_ITEM_IN_HAND));
                return;
            }

            if (!(ClickType.getPlayerClickType(p) instanceof SelectClickType)) {
                // Don't set previous game mode to creative if player already has select click type
                ClickType.setPlayerClickType(p, new SelectClickType(p.getGameMode(), amount, buyPrice, sellPrice, shopType));
                p.setGameMode(GameMode.CREATIVE);
            }

            p.sendMessage(messageRegistry.getMessage(Message.SELECT_ITEM));
        } else {
            SelectClickType ct = new SelectClickType(null, amount, buyPrice, sellPrice, shopType);
            ct.setItem(inHand);
            create2(p, ct);
        }
    }

    /**
     * <b>SHALL ONLY BE CALLED VIA {@link ShopCommand#createShopAfterSelected(Player player, SelectClickType clickType)}</b>
     */
    protected void create2(Player p, SelectClickType selectClickType) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        ItemStack itemStack = selectClickType.getItem();
        int amount = selectClickType.getAmount();
        double buyPrice = selectClickType.getBuyPrice();
        double sellPrice = selectClickType.getSellPrice();
        boolean buyEnabled = buyPrice > 0;
        boolean sellEnabled = sellPrice > 0;
        ShopType shopType = selectClickType.getShopType();

        // Check if item on blacklist
        for (String item :Config.blacklist) {
            ItemStack is = ItemUtils.getItemStack(item);

            if (is == null) {
                plugin.getLogger().warning("Invalid item found in blacklist: " + item);
                plugin.debug("Invalid item in blacklist: " + item);
                continue;
            }

            if (ItemUtils.isSameTypeAndDamage(is, itemStack)) {
                p.sendMessage(messageRegistry.getMessage(Message.CANNOT_SELL_ITEM));
                plugin.debug(p.getName() + "'s item is on the blacklist");
                return;
            }
        }

        // Check if prices lower than minimum price
        for (String key :Config.minimumPrices) {
            ItemStack is = ItemUtils.getItemStack(key);
            double minPrice = plugin.getConfig().getDouble("minimum-prices." + key);

            if (is == null) {
                plugin.getLogger().warning("Invalid item found in minimum-prices: " + key);
                plugin.debug("Invalid item in minimum-prices: " + key);
                continue;
            }

            if (ItemUtils.isSameTypeAndDamage(is, itemStack)) {
                if (buyEnabled) {
                    if ((buyPrice < amount * minPrice) && (buyPrice > 0)) {
                        p.sendMessage(messageRegistry.getMessage(Message.BUY_PRICE_TOO_LOW, new Replacement(Placeholder.MIN_PRICE, String.valueOf(amount * minPrice))));
                        plugin.debug(p.getName() + "'s buy price is lower than the minimum");
                        return;
                    }
                }

                if (sellEnabled) {
                    if ((sellPrice < amount * minPrice) && (sellPrice > 0)) {
                        p.sendMessage(messageRegistry.getMessage(Message.SELL_PRICE_TOO_LOW, new Replacement(Placeholder.MIN_PRICE, String.valueOf(amount * minPrice))));
                        plugin.debug(p.getName() + "'s sell price is lower than the minimum");
                        return;
                    }
                }
            }
        }

        // Check if prices higher than maximum price
        for (String key :Config.maximumPrices) {
            ItemStack is = ItemUtils.getItemStack(key);
            double maxPrice = plugin.getConfig().getDouble("maximum-prices." + key);

            if (is == null) {
                plugin.getLogger().warning("Invalid item found in maximum-prices: " + key);
                plugin.debug("Invalid item in maximum-prices: " + key);
                continue;
            }

            if (ItemUtils.isSameTypeAndDamage(is, itemStack)) {
                if (buyEnabled) {
                    if ((buyPrice > amount * maxPrice) && (buyPrice > 0)) {
                        p.sendMessage(messageRegistry.getMessage(Message.BUY_PRICE_TOO_HIGH, new Replacement(Placeholder.MAX_PRICE, String.valueOf(amount * maxPrice))));
                        plugin.debug(p.getName() + "'s buy price is higher than the maximum");
                        return;
                    }
                }

                if (sellEnabled) {
                    if ((sellPrice > amount * maxPrice) && (sellPrice > 0)) {
                        p.sendMessage(messageRegistry.getMessage(Message.SELL_PRICE_TOO_HIGH, new Replacement(Placeholder.MAX_PRICE, String.valueOf(amount * maxPrice))));
                        plugin.debug(p.getName() + "'s sell price is higher than the maximum");
                        return;
                    }
                }
            }
        }


        if (sellEnabled && buyEnabled) {
            if (Config.buyGreaterOrEqualSell) {
                if (buyPrice < sellPrice) {
                    p.sendMessage(messageRegistry.getMessage(Message.BUY_PRICE_TOO_LOW, new Replacement(Placeholder.MIN_PRICE, String.valueOf(sellPrice))));
                    plugin.debug(p.getName() + "'s buy price is lower than the sell price");
                    return;
                }
            }
        }

        if (UNBREAKING_ENCHANT.canEnchantItem(itemStack)) {
            if (ItemUtils.getDamage(itemStack) > 0 && !Config.allowBrokenItems) {
                p.sendMessage(messageRegistry.getMessage(Message.CANNOT_SELL_BROKEN_ITEM));
                plugin.debug(p.getName() + "'s item is broken");
                return;
            }
        }

        double creationPrice = (shopType == Shop.ShopType.NORMAL) ?Config.shopCreationPriceNormal :Config.shopCreationPriceAdmin;
        if (creationPrice > 0) {
            if (plugin.getEconomy().getBalance(p, p.getWorld().getName()) < creationPrice) {
                p.sendMessage(messageRegistry.getMessage(Message.SHOP_CREATE_NOT_ENOUGH_MONEY, new Replacement(Placeholder.CREATION_PRICE, String.valueOf(creationPrice))));
                plugin.debug(p.getName() + " can not pay the creation price");
                return;
            }
        }

        ShopProduct product = new ShopProduct(itemStack, amount);
        ShopPreCreateEvent event = new ShopPreCreateEvent(p, new Shop(plugin, p, product, null, buyPrice, sellPrice, shopType));
        Bukkit.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            plugin.getCmiWorthPriceAdvisor().advise(
                    p, product, buyPrice, sellPrice, shopType);
            ClickType.setPlayerClickType(p, new CreateClickType(product, buyPrice, sellPrice, shopType));
            plugin.debug(p.getName() + " can now click a chest");
            p.sendMessage(messageRegistry.getMessage(Message.CLICK_CHEST_CREATE));
        } else {
            plugin.debug("Shop pre create event cancelled");
        }
    }

    /**
     * A given player removes a shop
     * @param p The command executor
     */
    private void remove(final Player p) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        plugin.debug(p.getName() + " wants to remove a shop");

        ShopPreRemoveEvent event = new ShopPreRemoveEvent(p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.debug("Shop pre remove event cancelled");
            return;
        }

        plugin.debug(p.getName() + " can now click a chest");
        p.sendMessage(messageRegistry.getMessage(Message.CLICK_CHEST_REMOVE));
        ClickType.setPlayerClickType(p, new ClickType(ClickType.EnumClickType.REMOVE));
    }

    /**
     * A given player retrieves information about a shop
     * @param p The command executor
     */
    private void info(final Player p) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        plugin.debug(p.getName() + " wants to retrieve information");

        ShopPreInfoEvent event = new ShopPreInfoEvent(p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.debug("Shop pre info event cancelled");
            return;
        }

        plugin.debug(p.getName() + " can now click a chest");
        p.sendMessage(messageRegistry.getMessage(Message.CLICK_CHEST_INFO));
        ClickType.setPlayerClickType(p, new ClickType(ClickType.EnumClickType.INFO));
    }

    /**
     * A given player opens a shop
     * @param p The command executor
     */
    private void open(final Player p) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        plugin.debug(p.getName() + " wants to open a shop");

        ShopPreOpenEvent event = new ShopPreOpenEvent(p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.debug("Shop pre open event cancelled");
            return;
        }

        plugin.debug(p.getName() + " can now click a chest");
        p.sendMessage(messageRegistry.getMessage(Message.CLICK_CHEST_OPEN));
        ClickType.setPlayerClickType(p, new ClickType(ClickType.EnumClickType.OPEN));
    }

    private boolean changeConfig(CommandSender sender, String[] args) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        plugin.debug(sender.getName() + " is changing the configuration");

        String property = args[2];
        String value = args[3];
        boolean updateHologramLocations = isHologramLocationProperty(property);
        boolean updateHologramDisplays = isHologramDisplayProperty(property);

        if (args[1].equalsIgnoreCase("set")) {
            plugin.getShopChestConfig().set(property, value);
            sender.sendMessage(messageRegistry.getMessage(Message.CHANGED_CONFIG_SET, new Replacement(Placeholder.PROPERTY, property), new Replacement(Placeholder.VALUE, value)));
        } else if (args[1].equalsIgnoreCase("add")) {
            plugin.getShopChestConfig().add(property, value);
            sender.sendMessage(messageRegistry.getMessage(Message.CHANGED_CONFIG_ADDED, new Replacement(Placeholder.PROPERTY, property), new Replacement(Placeholder.VALUE, value)));
        } else if (args[1].equalsIgnoreCase("remove")) {
            plugin.getShopChestConfig().remove(property, value);
            sender.sendMessage(messageRegistry.getMessage(Message.CHANGED_CONFIG_REMOVED, new Replacement(Placeholder.PROPERTY, property), new Replacement(Placeholder.VALUE, value)));
        } else {
            return false;
        }

        if (updateHologramLocations) {
            for (Shop shop : shopUtils.getShops()) {
                shop.updateHologramLocation();
            }
        }
        if (updateHologramDisplays) {
            for (Shop shop : shopUtils.getShops()) {
                shop.updateHologramText();
            }
        }

        return true;
    }

    private boolean isHologramLocationProperty(String property) {
        return property.equalsIgnoreCase("hologram-lift")
                || property.equalsIgnoreCase("hologram-fixed-bottom");
    }

    private boolean isHologramDisplayProperty(String property) {
        return property.equalsIgnoreCase("hologram-panel-width")
                || property.equalsIgnoreCase("hologram-text-scale")
                || property.equalsIgnoreCase("hologram-background-color")
                || property.equalsIgnoreCase("hologram-background-opacity")
                || property.equalsIgnoreCase("hologram-max-item-name-length")
                || property.equalsIgnoreCase("hologram-max-item-detail-entries")
                || property.equalsIgnoreCase("hologram-item-details-per-line")
                || property.equalsIgnoreCase("hologram-fixed-facing")
                || property.regionMatches(true, 0, "hologram-colors.", 0, "hologram-colors.".length());
    }

    private void removeAll(CommandSender sender, String[] args) {
        OfflinePlayer vendor = Bukkit.getOfflinePlayer(args[1]);

        plugin.debug(sender.getName() + " is removing all shops of " + vendor.getName());

        plugin.getShopUtils().getShops(vendor, new Callback<Collection<Shop>>(plugin) {
            @Override
            public void onResult(Collection<Shop> result) {
                List<Shop> shops = new ArrayList<>(result);

                ShopRemoveAllEvent event = new ShopRemoveAllEvent(sender, vendor, shops);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    plugin.debug("Remove all event cancelled");
                    return;
                }

                for (Shop shop : shops) {
                    shopUtils.removeShop(shop, true);
                }

                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

                sender.sendMessage(messageRegistry.getMessage(Message.ALL_SHOPS_REMOVED,
                        new Replacement(Placeholder.AMOUNT, String.valueOf(shops.size())),
                        new Replacement(Placeholder.VENDOR, vendor.getName())));
            }

            @Override
            public void onError(Throwable throwable) {
                final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

                sender.sendMessage(messageRegistry.getMessage(Message.ERROR_OCCURRED,
                        new Replacement(Placeholder.ERROR, "Failed to get player's shops")));
            }
        });

        
    }
}
