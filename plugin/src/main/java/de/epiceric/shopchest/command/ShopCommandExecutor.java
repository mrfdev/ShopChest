package de.epiceric.shopchest.command;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.config.hologram.HologramColorPalette;
import de.epiceric.shopchest.config.hologram.HologramItemDetails;
import de.epiceric.shopchest.diagnostics.ShopChestSupportReport;
import de.epiceric.shopchest.diagnostics.PluginBuildInfo;
import de.epiceric.shopchest.diagnostics.ShopAuditFinding;
import de.epiceric.shopchest.diagnostics.ShopAuditIssue;
import de.epiceric.shopchest.diagnostics.ShopAuditReport;
import de.epiceric.shopchest.diagnostics.ShopAuditService;
import de.epiceric.shopchest.diagnostics.ShopAuditSummary;
import de.epiceric.shopchest.event.*;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.MessageRegistry;
import de.epiceric.shopchest.language.Replacement;
import de.epiceric.shopchest.display.HologramTextFormatter;
import de.epiceric.shopchest.display.TextComponentHelper;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopContainer;
import de.epiceric.shopchest.shop.ShopDisplayOrientation;
import de.epiceric.shopchest.shop.Shop.ShopType;
import de.epiceric.shopchest.shop.ShopProduct;
import de.epiceric.shopchest.shop.ShopTerms;
import de.epiceric.shopchest.shop.ShopTermsValidator;
import de.epiceric.shopchest.sql.DatabaseDiagnostics;
import de.epiceric.shopchest.sql.RecentTransaction;
import de.epiceric.shopchest.sql.RecentTransactionPage;
import de.epiceric.shopchest.sql.ShopAuditRecord;
import de.epiceric.shopchest.utils.*;
import de.epiceric.shopchest.utils.ClickType.CreateClickType;
import de.epiceric.shopchest.utils.ClickType.EditClickType;
import de.epiceric.shopchest.utils.ClickType.SelectClickType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

class ShopCommandExecutor implements CommandExecutor {

    private static final String DOCS_URL =
            "https://docs.1moreblock.com/player-guides/custom-server-plugins/shopchest/";
    private static final int SHOP_LIST_PAGE_SIZE = 8;
    private static final int SHOP_LIST_ITEM_NAME_LENGTH = 36;
    private static final int RECENT_PAGE_SIZE = 8;
    private static final int RECENT_ITEM_NAME_LENGTH = 32;
    private static final int DEBUG_PAGE_SIZE = 8;
    private static final int ADMIN_AUDIT_PAGE_SIZE = 8;
    private static final long ADMIN_AUDIT_CACHE_TTL_NANOS =
            TimeUnit.SECONDS.toNanos(60);
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final ShopChest plugin;
    private final ShopUtils shopUtils;
    private final ShopAuditService shopAuditService;
    private final ShopSearchCommandHandler shopSearchCommandHandler;
    private final StorefrontProfileCommandHandler storefrontProfileCommandHandler;
    private final AdvertisingCommandHandler advertisingCommandHandler;
    private final MarketplaceExportCommandHandler marketplaceExportCommandHandler;
    private final Map<UUID, Map<Integer, Location>> adminTeleportTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> adminTeleportTargetExpiry = new ConcurrentHashMap<>();
    private final Map<AuditCacheKey, AuditCacheEntry> adminAuditCache = new HashMap<>();
    private final java.util.Set<Integer> pendingShopEdits = ConcurrentHashMap.newKeySet();
    private boolean adminAuditInProgress;
    private static final Enchantment UNBREAKING_ENCHANT = Enchantment.UNBREAKING;

    ShopCommandExecutor(ShopChest plugin) {
        this.plugin = plugin;
        this.shopUtils = plugin.getShopUtils();
        this.shopAuditService = new ShopAuditService(plugin);
        this.shopSearchCommandHandler = new ShopSearchCommandHandler(plugin);
        this.storefrontProfileCommandHandler = new StorefrontProfileCommandHandler(plugin);
        this.advertisingCommandHandler = new AdvertisingCommandHandler(plugin);
        this.marketplaceExportCommandHandler = new MarketplaceExportCommandHandler(plugin);
    }

    void cacheAdminTeleportTargets(Player player, Map<Integer, Location> targets) {
        if (!player.hasPermission(Permissions.ADMIN_LIST)) {
            return;
        }
        final Map<Integer, Location> copies = new HashMap<>();
        targets.forEach((id, location) -> {
            if (id != null && id >= 0 && location != null) {
                copies.put(id, location.clone());
            }
        });
        adminTeleportTargets.put(player.getUniqueId(), Map.copyOf(copies));
        adminTeleportTargetExpiry.put(player.getUniqueId(), System.currentTimeMillis() + 60_000L);
    }

    void invalidateEphemeralState() {
        shopSearchCommandHandler.invalidate();
        advertisingCommandHandler.invalidateDrafts();
        adminTeleportTargets.clear();
        adminTeleportTargetExpiry.clear();
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
            } else if (subCommand.getName().equalsIgnoreCase("debug")) {
                return handleDebugCommand(sender, args);
            } else if (subCommand.getName().equalsIgnoreCase("search")) {
                return shopSearchCommandHandler.handle(sender, args);
            } else if (subCommand.getName().equalsIgnoreCase("profile")) {
                return storefrontProfileCommandHandler.handle(sender, args);
            } else if (subCommand.getName().equalsIgnoreCase("advertise")) {
                return advertisingCommandHandler.handlePlayer(sender, args);
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
                    } else if (subCommand.getName().equalsIgnoreCase("edit")) {
                        if (args.length != 3) {
                            return false;
                        }
                        edit(args, p);
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
        final PluginBuildInfo build = PluginBuildInfo.load(plugin);
        final Replacement command = new Replacement(Placeholder.COMMAND, Config.mainCommandName);
        final Replacement version = new Replacement(Placeholder.VERSION, plugin.getPluginMeta().getVersion());

        sender.sendMessage(" ");
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_HEADER, version));
        sender.sendMessage(messageRegistry.getMessage(
                Message.INFO_BUILD,
                new Replacement(Placeholder.BUILD, build.build()),
                new Replacement(Placeholder.JAVA_TARGET, build.javaTarget()),
                new Replacement(Placeholder.PAPER_TARGET, build.paperTarget()),
                new Replacement(Placeholder.PAPER_BUILD, build.paperBuild()),
                new Replacement(Placeholder.PAPER_CHANNEL, build.paperChannel()),
                new Replacement(Placeholder.PAPER_API, build.paperApiVersion())));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_INTRO));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_STEP_PLACE));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_STEP_CREATE, command));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_STEP_CHEST));
        sender.sendMessage(messageRegistry.getMessage(Message.INFO_PRICE_HINT, command));

        if (sender instanceof Player player) {
            final Component healthLink = LEGACY_SERIALIZER.deserialize(
                            messageRegistry.getMessage(Message.INFO_SHOP_HEALTH, command))
                    .clickEvent(ClickEvent.runCommand(
                            "/" + Config.mainCommandName + " list"));
            player.sendMessage(healthLink);

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

        if (args[1].equalsIgnoreCase("audit")) {
            if (!sender.hasPermission(Permissions.ADMIN_AUDIT)) {
                sender.sendMessage(messageRegistry.getMessage(
                        Message.NO_PERMISSION_ADMIN_AUDIT));
                return true;
            }
            if (args.length > 4) {
                sendAdminHelp(sender);
                return true;
            }

            final AuditScope scope;
            if (args.length == 2
                    || args[2].equalsIgnoreCase("all")
                    || args[2].equals("*")) {
                scope = AuditScope.all();
            } else {
                final OfflinePlayer owner = findOfflinePlayer(args[2]);
                if (owner == null) {
                    sender.sendMessage(messageRegistry.getMessage(
                            Message.SHOP_LIST_PLAYER_NOT_FOUND,
                            new Replacement(Placeholder.PLAYER, args[2])));
                    return true;
                }
                scope = AuditScope.player(owner);
            }

            final Integer page = parsePage(sender, args.length == 4 ? args[3] : null);
            if (page != null) {
                sendAdminAudit(sender, scope, page, args.length == 4);
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("storefront")) {
            return storefrontProfileCommandHandler.handleModeration(sender, args);
        }

        if (args[1].equalsIgnoreCase("advertise")) {
            return advertisingCommandHandler.handleAdmin(sender, args);
        }

        if (args[1].equalsIgnoreCase("export")) {
            return marketplaceExportCommandHandler.handle(sender, args);
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
                || sender.hasPermission(Permissions.ADMIN_AUDIT)
                || sender.hasPermission(Permissions.ADMIN_DEBUG)
                || sender.hasPermission(Permissions.ADMIN_STOREFRONT)
                || sender.hasPermission(Permissions.ADMIN_ADVERTISE)
                || sender.hasPermission(Permissions.ADMIN_EXPORT);
    }

    private boolean handleDebugCommand(CommandSender sender, String[] args) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        if (!sender.hasPermission(Permissions.ADMIN_DEBUG)) {
            sender.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_ADMIN_DEBUG));
            return true;
        }
        if (args.length > 3) {
            sendDebugUsage(sender);
            return true;
        }

        final String section = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
        if (section.equals("status") || section.equals("overview")) {
            if (args.length == 3) {
                sendDebugUsage(sender);
            } else {
                sendAdminDebug(sender);
            }
            return true;
        }

        final String canonicalSection;
        switch (section) {
            case "commands" -> canonicalSection = "commands";
            case "permissions", "perms" -> canonicalSection = "permissions";
            case "placeholders", "placeholder" -> canonicalSection = "placeholders";
            default -> {
                sendDebugUsage(sender);
                return true;
            }
        }

        final Integer page = parsePage(sender, args.length == 3 ? args[2] : null);
        if (page == null) {
            return true;
        }
        switch (canonicalSection) {
            case "commands" -> sendDebugCommands(sender, page);
            case "permissions" -> sendDebugPermissions(sender, page);
            case "placeholders" -> sendDebugPlaceholders(sender, page);
            default -> throw new IllegalStateException(
                    "Unexpected debug section " + canonicalSection);
        }
        return true;
    }

    private void sendDebugUsage(CommandSender sender) {
        sender.sendMessage(Component.text(
                "Usage: /" + Config.mainCommandName
                        + " debug [status|commands|permissions|placeholders] [page]",
                NamedTextColor.YELLOW));
    }

    private void sendDebugCommands(CommandSender sender, int requestedPage) {
        sendDebugPage(
                sender,
                "Commands",
                ShopDebugCatalog.commands(Config.mainCommandName),
                requestedPage,
                "commands",
                (target, entry) -> {
                    target.sendMessage(Component.text(entry.usage(), NamedTextColor.AQUA));
                    Component details = Component.text("  " + entry.description(), NamedTextColor.GRAY);
                    if (!entry.permission().isBlank()) {
                        details = details.append(Component.text(
                                " [" + entry.permission() + "]",
                                NamedTextColor.DARK_GRAY));
                    }
                    target.sendMessage(details);
                });
    }

    private void sendDebugPermissions(CommandSender sender, int requestedPage) {
        final List<ShopDebugCatalog.PermissionEntry> entries = new ArrayList<>();
        plugin.getPluginMeta().getPermissions().stream()
                .map(permission -> new ShopDebugCatalog.PermissionEntry(
                        permission.getName(),
                        permission.getDefault().name().toLowerCase(java.util.Locale.ROOT),
                        permission.getDescription()))
                .forEach(entries::add);
        entries.addAll(ShopDebugCatalog.dynamicPermissions());
        entries.sort(Comparator.comparing(
                ShopDebugCatalog.PermissionEntry::node,
                String.CASE_INSENSITIVE_ORDER));

        sendDebugPage(
                sender,
                "Permissions",
                entries,
                requestedPage,
                "permissions",
                (target, entry) -> {
                    target.sendMessage(Component.text(entry.node(), NamedTextColor.AQUA)
                            .append(Component.text(
                                    " default=" + entry.defaultValue(),
                                    NamedTextColor.DARK_GRAY)));
                    target.sendMessage(Component.text(
                            "  " + entry.description(),
                            NamedTextColor.GRAY));
                });
    }

    private void sendDebugPlaceholders(CommandSender sender, int requestedPage) {
        sender.sendMessage(Component.text(
                "These are internal hologram-format.yml tokens, not PlaceholderAPI placeholders.",
                NamedTextColor.GRAY));
        sendDebugPage(
                sender,
                "Placeholders",
                ShopDebugCatalog.placeholders(),
                requestedPage,
                "placeholders",
                (target, entry) -> target.sendMessage(
                        Component.text(entry.token(), NamedTextColor.AQUA)
                                .append(Component.text(
                                        " - " + entry.description(),
                                        NamedTextColor.GRAY))));
    }

    private <T> void sendDebugPage(
            CommandSender sender,
            String title,
            List<T> entries,
            int requestedPage,
            String section,
            BiConsumer<CommandSender, T> rowRenderer
    ) {
        final PageSlice<T> page = PageSlice.of(entries, requestedPage, DEBUG_PAGE_SIZE);
        sender.sendMessage(" ");
        sender.sendMessage(Component.text("ShopChest Debug ", NamedTextColor.GOLD)
                .append(Component.text(title, NamedTextColor.YELLOW))
                .append(Component.text(
                        " - Page " + page.page() + "/" + page.pageCount()
                                + " (" + page.totalEntries() + " entries)",
                        NamedTextColor.DARK_GRAY)));
        page.entries().forEach(entry -> rowRenderer.accept(sender, entry));
        sendDebugNavigation(sender, section, page);
        sender.sendMessage(" ");
    }

    private void sendDebugNavigation(
            CommandSender sender,
            String section,
            PageSlice<?> page
    ) {
        Component navigation = Component.text(
                "Page " + page.page() + "/" + page.pageCount(),
                NamedTextColor.DARK_GRAY);
        final String commandPrefix = "/" + Config.mainCommandName + " debug " + section + " ";
        if (page.page() > 1) {
            navigation = Component.text("\u00ab Previous", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand(commandPrefix + (page.page() - 1)))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(navigation);
        }
        if (page.page() < page.pageCount()) {
            navigation = navigation
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Next \u00bb", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.runCommand(commandPrefix + (page.page() + 1))));
        }
        sender.sendMessage(navigation);
    }

    private void sendAdminHelp(CommandSender sender) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final Replacement command = new Replacement(Placeholder.COMMAND, Config.mainCommandName);

        sender.sendMessage(" ");
        sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_HELP_HEADER));
        if (sender.hasPermission(Permissions.ADMIN_LIST)) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_HELP_LIST, command));
        }
        if (sender.hasPermission(Permissions.ADMIN_AUDIT)) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_HELP_AUDIT, command));
        }
        if (sender.hasPermission(Permissions.ADMIN_DEBUG)) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_HELP_DEBUG, command));
        }
        if (sender.hasPermission(Permissions.ADMIN_STOREFRONT)) {
            sender.sendMessage("§6/" + Config.mainCommandName
                    + " admin storefront <player> <hide|show|suspend|unsuspend|clear>"
                    + " §7- Moderate public storefront discovery");
        }
        if (sender.hasPermission(Permissions.ADMIN_ADVERTISE)) {
            sender.sendMessage("§6/" + Config.mainCommandName
                    + " admin advertise currency <status|capture|clear>"
                    + " §7- Manage the captured AFK Shrine Token");
        }
        if (sender.hasPermission(Permissions.ADMIN_EXPORT)) {
            sender.sendMessage("§6/" + Config.mainCommandName
                    + " admin export marketplace §7- Create a reviewed website snapshot");
        }
        sender.sendMessage(" ");
    }

    private void sendAdminAudit(
            CommandSender sender,
            AuditScope scope,
            int requestedPage,
            boolean reuseCompletedSnapshot
    ) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final long now = System.nanoTime();
        adminAuditCache.entrySet().removeIf(entry -> entry.getValue().expired(now));
        final AuditCacheKey cacheKey = AuditCacheKey.of(sender, scope);
        if (reuseCompletedSnapshot) {
            final AuditCacheEntry cached = adminAuditCache.get(cacheKey);
            if (cached != null) {
                displayAdminAudit(sender, scope, cached.report(), requestedPage);
                return;
            }
        }
        if (adminAuditInProgress) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_AUDIT_BUSY));
            return;
        }

        adminAuditCache.remove(cacheKey);
        adminAuditInProgress = true;
        sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_AUDIT_LOADING));

        try {
            plugin.getShopDatabase().getShopAuditRecords(
                    new Callback<List<ShopAuditRecord>>(plugin) {
                        @Override
                        public void onResult(List<ShopAuditRecord> records) {
                            if (!canReceiveAudit(sender)) {
                                adminAuditInProgress = false;
                                return;
                            }
                            try {
                                shopAuditService.inspect(
                                        records,
                                        scope.ownerUuid(),
                                        report -> {
                                            adminAuditInProgress = false;
                                            if (canReceiveAudit(sender)) {
                                                adminAuditCache.put(
                                                        cacheKey,
                                                        new AuditCacheEntry(
                                                                report,
                                                                System.nanoTime()));
                                                displayAdminAudit(
                                                        sender,
                                                        scope,
                                                        report,
                                                        requestedPage);
                                            }
                                        },
                                        throwable -> {
                                            adminAuditInProgress = false;
                                            sendAdminAuditError(sender, throwable);
                                        });
                            } catch (RuntimeException exception) {
                                adminAuditInProgress = false;
                                sendAdminAuditError(sender, exception);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            adminAuditInProgress = false;
                            sendAdminAuditError(sender, throwable);
                        }
                    });
        } catch (RuntimeException exception) {
            adminAuditInProgress = false;
            sendAdminAuditError(sender, exception);
        }
    }

    private void displayAdminAudit(
            CommandSender sender,
            AuditScope scope,
            ShopAuditReport report,
            int requestedPage
    ) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final ShopAuditSummary summary = report.summary();
        final PageSlice<ShopAuditFinding> page = PageSlice.of(
                report.reviewFindings(),
                requestedPage,
                ADMIN_AUDIT_PAGE_SIZE);

        sender.sendMessage(" ");
        sender.sendMessage(messageRegistry.getMessage(
                Message.ADMIN_AUDIT_HEADER,
                new Replacement(Placeholder.PAGE, page.page()),
                new Replacement(Placeholder.PAGES, page.pageCount()),
                new Replacement(Placeholder.AMOUNT, page.totalEntries())));
        sender.sendMessage(scope.ownerUuid() == null
                ? messageRegistry.getMessage(Message.ADMIN_AUDIT_SCOPE_ALL)
                : messageRegistry.getMessage(
                        Message.ADMIN_AUDIT_SCOPE_PLAYER,
                        new Replacement(
                                Placeholder.PLAYER,
                                sanitizeAuditValue(scope.displayName(), 36))));
        if (Config.removeShopOnError) {
            sender.sendMessage(messageRegistry.getMessage(
                    Message.ADMIN_AUDIT_REMOVE_ON_ERROR_WARNING));
        }
        sender.sendMessage(messageRegistry.getMessage(
                Message.ADMIN_AUDIT_SENSITIVE));

        if (summary.scanned() == 0) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_AUDIT_EMPTY));
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_AUDIT_DRY_RUN));
            sender.sendMessage(" ");
            return;
        }

        sender.sendMessage(messageRegistry.getMessage(
                Message.ADMIN_AUDIT_SUMMARY,
                new Replacement(Placeholder.AMOUNT, summary.scanned()),
                new Replacement(Placeholder.HEALTHY, summary.healthy()),
                new Replacement(Placeholder.ATTENTION, summary.attention()),
                new Replacement(Placeholder.UNCHECKED, summary.unchecked())));
        sender.sendMessage(messageRegistry.getMessage(
                Message.ADMIN_AUDIT_PHYSICAL,
                new Replacement(Placeholder.MISSING_WORLDS, summary.missingWorlds()),
                new Replacement(
                        Placeholder.MISSING_CONTAINERS,
                        summary.missingContainers()),
                new Replacement(
                        Placeholder.UNSUPPORTED_CONTAINERS,
                        summary.unsupportedContainers()),
                new Replacement(Placeholder.BLOCKED, summary.blocked())));
        sender.sendMessage(messageRegistry.getMessage(
                Message.ADMIN_AUDIT_DATA,
                new Replacement(Placeholder.INVALID_PRODUCTS, summary.invalidProducts()),
                new Replacement(Placeholder.INVALID_RECORDS, summary.invalidRecords()),
                new Replacement(Placeholder.STALE, summary.staleCandidates())));

        for (ShopAuditFinding finding : page.entries()) {
            final ShopAuditRecord record = finding.record();
            sender.sendMessage(messageRegistry.getMessage(
                    Message.ADMIN_AUDIT_ENTRY,
                    new Replacement(
                            Placeholder.SHOP_ID,
                            sanitizeAuditValue(record.rawId(), 24)),
                    new Replacement(Placeholder.VALUE, auditReasonList(finding)),
                    new Replacement(
                            Placeholder.PLAYER,
                            sanitizeAuditValue(record.vendor(), 36)),
                    new Replacement(
                            Placeholder.WORLD,
                            sanitizeAuditValue(record.world(), 32)),
                    new Replacement(
                            Placeholder.X,
                            sanitizeAuditValue(record.rawX(), 24)),
                    new Replacement(
                            Placeholder.Y,
                            sanitizeAuditValue(record.rawY(), 24)),
                    new Replacement(
                            Placeholder.Z,
                            sanitizeAuditValue(record.rawZ(), 24))));
        }

        if (summary.attention() == 0) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_AUDIT_CLEAN));
        }
        if (summary.unchecked() > 0) {
            sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_AUDIT_INCOMPLETE));
        }
        sendAdminAuditNavigation(sender, scope, page);
        sender.sendMessage(messageRegistry.getMessage(Message.ADMIN_AUDIT_DRY_RUN));
        sender.sendMessage(" ");
    }

    private String auditReasonList(ShopAuditFinding finding) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final List<String> reasons = new ArrayList<>();
        for (ShopAuditIssue issue : ShopAuditIssue.values()) {
            if (finding.issues().contains(issue)) {
                reasons.add(messageRegistry.getMessage(auditReasonMessage(issue)));
            }
        }
        if (finding.unchecked()) {
            reasons.add(messageRegistry.getMessage(
                    Message.ADMIN_AUDIT_REASON_UNCHECKED));
        }
        return String.join(", ", reasons);
    }

    private Message auditReasonMessage(ShopAuditIssue issue) {
        return switch (issue) {
            case WORLD_UNAVAILABLE -> Message.ADMIN_AUDIT_REASON_MISSING_WORLD;
            case MISSING_CONTAINER -> Message.ADMIN_AUDIT_REASON_MISSING_CONTAINER;
            case UNSUPPORTED_CONTAINER -> Message.ADMIN_AUDIT_REASON_UNSUPPORTED_CONTAINER;
            case INCOMPLETE_CONTAINER -> Message.ADMIN_AUDIT_REASON_INCOMPLETE_CONTAINER;
            case BLOCKED_DISPLAY -> Message.ADMIN_AUDIT_REASON_BLOCKED_DISPLAY;
            case INVALID_PRODUCT -> Message.ADMIN_AUDIT_REASON_INVALID_PRODUCT;
            case INVALID_OWNER -> Message.ADMIN_AUDIT_REASON_INVALID_OWNER;
            case INVALID_SHOP_TYPE -> Message.ADMIN_AUDIT_REASON_INVALID_SHOP_TYPE;
            case INVALID_TERMS -> Message.ADMIN_AUDIT_REASON_INVALID_TERMS;
            case INVALID_LOCATION -> Message.ADMIN_AUDIT_REASON_INVALID_LOCATION;
            case INVALID_RECORD -> Message.ADMIN_AUDIT_REASON_INVALID_RECORD;
            case CONFLICTING_RECORD -> Message.ADMIN_AUDIT_REASON_CONFLICTING_RECORD;
            case SHADOWED_RECORD -> Message.ADMIN_AUDIT_REASON_SHADOWED_RECORD;
            case INACTIVE_RECORD -> Message.ADMIN_AUDIT_REASON_INACTIVE_RECORD;
        };
    }

    private void sendAdminAuditNavigation(
            CommandSender sender,
            AuditScope scope,
            PageSlice<?> page
    ) {
        if (page.pageCount() <= 1) {
            return;
        }

        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        if (!(sender instanceof Player)) {
            if (page.page() > 1) {
                sender.sendMessage(messageRegistry.getMessage(
                        Message.ADMIN_AUDIT_NAVIGATION_COMMAND,
                        new Replacement(
                                Placeholder.VALUE,
                                adminAuditPageCommand(scope, page.page() - 1))));
            }
            if (page.page() < page.pageCount()) {
                sender.sendMessage(messageRegistry.getMessage(
                        Message.ADMIN_AUDIT_NAVIGATION_COMMAND,
                        new Replacement(
                                Placeholder.VALUE,
                                adminAuditPageCommand(scope, page.page() + 1))));
            }
            return;
        }

        Component navigation = Component.empty();
        if (page.page() > 1) {
            navigation = navigation.append(LEGACY_SERIALIZER
                    .deserialize(messageRegistry.getMessage(Message.SHOP_LIST_PREVIOUS))
                    .clickEvent(ClickEvent.runCommand(
                            adminAuditPageCommand(scope, page.page() - 1))));
        }
        if (page.page() > 1 && page.page() < page.pageCount()) {
            navigation = navigation.append(Component.text("  ", NamedTextColor.GRAY));
        }
        if (page.page() < page.pageCount()) {
            navigation = navigation.append(LEGACY_SERIALIZER
                    .deserialize(messageRegistry.getMessage(Message.SHOP_LIST_NEXT))
                    .clickEvent(ClickEvent.runCommand(
                            adminAuditPageCommand(scope, page.page() + 1))));
        }
        sender.sendMessage(navigation);
    }

    private String adminAuditPageCommand(AuditScope scope, int page) {
        return "/" + Config.mainCommandName + " admin audit "
                + scope.pageSelector() + " " + page;
    }

    private String sanitizeAuditValue(String value, int maximumLength) {
        String raw = value == null ? "<null>" : value;
        final int maximumScanLength = Math.max(maximumLength, maximumLength * 4);
        if (raw.length() > maximumScanLength) {
            raw = raw.substring(0, maximumScanLength) + "...";
        }
        raw = raw.replace('\u00a7', '?');
        return HologramTextFormatter.sanitizeItemName(raw, maximumLength);
    }

    private boolean canReceiveAudit(CommandSender sender) {
        return sender.hasPermission(Permissions.ADMIN_AUDIT)
                && (!(sender instanceof Player player) || player.isOnline());
    }

    private void sendAdminAuditError(CommandSender sender, Throwable throwable) {
        plugin.getLogger().severe("Failed to complete the shop maintenance audit");
        if (throwable != null) {
            plugin.debug(throwable);
        }
        if (canReceiveAudit(sender)) {
            sender.sendMessage(plugin.getLanguageManager().getMessageRegistry()
                    .getMessage(Message.ADMIN_AUDIT_ERROR));
        }
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
                || line.startsWith("Compiled API:")
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
            cacheAdminTeleportTargets(admin, targets);
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
        final Map<Shop, ShopListHealth> healthByShop = new HashMap<>();
        for (Shop shop : shops) {
            healthByShop.put(shop, resolveShopListHealth(shop));
        }
        final ShopHealthSummary healthSummary = ShopHealthSummary.summarize(healthByShop.values());

        sender.sendMessage(" ");
        sender.sendMessage(messageRegistry.getMessage(
                headerMessage,
                new Replacement(Placeholder.PLAYER, ownerName),
                new Replacement(Placeholder.PAGE, page.page()),
                new Replacement(Placeholder.PAGES, page.pageCount()),
                new Replacement(Placeholder.AMOUNT, page.totalEntries())));
        sender.sendMessage(messageRegistry.getMessage(
                Message.SHOP_LIST_HEALTH,
                new Replacement(Placeholder.HEALTHY, healthSummary.healthy()),
                new Replacement(Placeholder.ATTENTION, healthSummary.attention()),
                new Replacement(Placeholder.OUT_OF_STOCK, healthSummary.outOfStock()),
                new Replacement(Placeholder.FULL, healthSummary.full()),
                new Replacement(Placeholder.BLOCKED, healthSummary.blocked()),
                new Replacement(Placeholder.UNAVAILABLE, healthSummary.unavailable()),
                new Replacement(Placeholder.UNCHECKED, healthSummary.unchecked())));

        for (Shop shop : page.entries()) {
            final Location location = shop.getLocation();
            final String itemName = HologramTextFormatter.sanitizeItemName(
                    shop.getProduct().getLocalizedName(),
                    SHOP_LIST_ITEM_NAME_LENGTH);
            final ShopListHealth health = healthByShop.get(shop);
            final String healthBadges = shopListHealthBadges(health);
            final Message entryMessage = sender instanceof Player
                    ? Message.SHOP_LIST_ENTRY
                    : Message.SHOP_LIST_CONSOLE_ENTRY;
            final String entry = messageRegistry.getMessage(
                    entryMessage,
                    new Replacement(Placeholder.SHOP_ID, shop.getID()),
                    new Replacement(Placeholder.AMOUNT, shop.getProduct().getAmount()),
                    new Replacement(Placeholder.ITEM_NAME, itemName),
                    new Replacement(Placeholder.STOCK, healthBadges),
                    new Replacement(Placeholder.WORLD, worldName(location)),
                    new Replacement(Placeholder.X, location.getBlockX()),
                    new Replacement(Placeholder.Y, location.getBlockY()),
                    new Replacement(Placeholder.Z, location.getBlockZ()));

            Component line = LEGACY_SERIALIZER.deserialize(entry);
            if (sender instanceof Player) {
                final boolean canTeleport = adminView && shop.getID() >= 0;
                line = line.hoverEvent(buildShopListHover(shop, itemName, health, canTeleport));
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

    private ShopListHealth resolveShopListHealth(Shop shop) {
        final boolean adminShop = shop.getShopType() == ShopType.ADMIN;
        final int transactionAmount = shop.getProduct().getAmount();
        final Location location = shop.getLocation();
        if (location == null || !location.isWorldLoaded()) {
            return ShopListHealth.unavailable(
                    shop.getBuyPrice(),
                    adminShop,
                    transactionAmount);
        }

        final World world = location.getWorld();
        if (!world.isChunkLoaded(
                ChunkCoordinates.fromBlock(location.getBlockX()),
                ChunkCoordinates.fromBlock(location.getBlockZ()))) {
            return ShopListHealth.unchecked(
                    shop.getBuyPrice(),
                    adminShop,
                    transactionAmount);
        }

        final Block containerBlock = world.getBlockAt(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
        if (!hasLoadedContainerPartner(world, containerBlock)) {
            return ShopListHealth.unchecked(
                    shop.getBuyPrice(),
                    adminShop,
                    transactionAmount);
        }

        final ShopContainer container = ShopContainer.resolve(plugin, containerBlock);
        if (container == null || !hasCompleteContainer(containerBlock, container)) {
            return ShopListHealth.unavailable(
                    shop.getBuyPrice(),
                    adminShop,
                    transactionAmount);
        }

        final boolean blocked = !container.hasDisplaySpace();
        if (adminShop) {
            return ShopListHealth.checked(
                    shop.getBuyPrice(),
                    shop.getSellPrice(),
                    true,
                    0,
                    0,
                    transactionAmount,
                    blocked);
        }

        final InventoryHealth inventoryHealth = inspectInventory(
                container.getInventory(),
                shop.getProduct().getItemStack());
        return ShopListHealth.checked(
                shop.getBuyPrice(),
                shop.getSellPrice(),
                false,
                inventoryHealth.stock(),
                inventoryHealth.freeSpace(),
                transactionAmount,
                blocked);
    }

    private boolean hasLoadedContainerPartner(World world, Block block) {
        if (!(block.getBlockData() instanceof Chest chest)
                || chest.getType() == Chest.Type.SINGLE) {
            return true;
        }

        final BlockFace partnerFace = switch (chest.getFacing()) {
            case NORTH -> chest.getType() == Chest.Type.LEFT ? BlockFace.EAST : BlockFace.WEST;
            case EAST -> chest.getType() == Chest.Type.LEFT ? BlockFace.SOUTH : BlockFace.NORTH;
            case SOUTH -> chest.getType() == Chest.Type.LEFT ? BlockFace.WEST : BlockFace.EAST;
            case WEST -> chest.getType() == Chest.Type.LEFT ? BlockFace.NORTH : BlockFace.SOUTH;
            default -> null;
        };
        if (partnerFace == null) {
            return false;
        }

        final int partnerX = block.getX() + partnerFace.getModX();
        final int partnerZ = block.getZ() + partnerFace.getModZ();
        return world.isChunkLoaded(
                ChunkCoordinates.fromBlock(partnerX),
                ChunkCoordinates.fromBlock(partnerZ));
    }

    private boolean hasCompleteContainer(Block block, ShopContainer container) {
        return !(block.getBlockData() instanceof Chest chest)
                || chest.getType() == Chest.Type.SINGLE
                || container.getLocations().size() == 2;
    }

    private InventoryHealth inspectInventory(Inventory inventory, ItemStack product) {
        int stock = 0;
        int freeSpace = 0;
        final int maxStackSize = Math.max(1, product.getMaxStackSize());

        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType().isAir()) {
                freeSpace += maxStackSize;
                continue;
            }

            // Utils.isItemSimilar normalizes written-book metadata. Compare
            // clones so this read-only report cannot mutate a live inventory.
            if (Utils.isItemSimilar(current.clone(), product.clone())) {
                stock += Math.max(0, current.getAmount());
                freeSpace += Math.max(0, maxStackSize - current.getAmount());
            }
        }

        return new InventoryHealth(stock, freeSpace);
    }

    private String shopListHealthBadges(ShopListHealth health) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        final StringBuilder badges = new StringBuilder();
        if (health.unavailable()) {
            badges.append(messageRegistry.getMessage(Message.SHOP_LIST_UNAVAILABLE));
        }
        if (health.blocked()) {
            badges.append(messageRegistry.getMessage(Message.SHOP_LIST_BLOCKED));
        }
        if (health.outOfStock()) {
            badges.append(messageRegistry.getMessage(Message.SHOP_LIST_OUT_OF_STOCK));
        }
        if (health.full()) {
            badges.append(messageRegistry.getMessage(Message.SHOP_LIST_FULL));
        }
        return badges.toString();
    }

    private Component buildShopListHover(
            Shop shop,
            String itemName,
            ShopListHealth health,
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
                                shopListStockText(health, shop.getProduct().getAmount()))),
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

    private String shopListStockText(ShopListHealth health, int transactionAmount) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        if (health.unavailable()) {
            return messageRegistry.getMessage(Message.SHOP_LIST_STOCK_UNAVAILABLE);
        }
        final ShopListStock stock = health.stock();
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
        if (location == null || !location.isWorldLoaded()) {
            return "<unavailable>";
        }
        return location.getWorld().getName();
    }

    private record InventoryHealth(int stock, int freeSpace) {
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
        final long expiresAt = adminTeleportTargetExpiry.getOrDefault(player.getUniqueId(), 0L);
        if (shopLocation == null || System.currentTimeMillis() > expiresAt) {
            player.sendMessage(messageRegistry.getMessage(Message.ADMIN_TELEPORT_TARGET_EXPIRED));
            return;
        }
        if (!shopLocation.isWorldLoaded()) {
            player.sendMessage(messageRegistry.getMessage(Message.ADMIN_TELEPORT_WORLD_UNAVAILABLE));
            return;
        }
        final World targetWorld = shopLocation.getWorld();
        final boolean targetChunkLoaded = targetWorld != null && targetWorld.isChunkLoaded(
                ChunkCoordinates.fromBlock(shopLocation.getBlockX()),
                ChunkCoordinates.fromBlock(shopLocation.getBlockZ()));
        if (!targetChunkLoaded) {
            player.sendMessage(messageRegistry.getMessage(Message.ADMIN_TELEPORT_TARGET_EXPIRED));
            return;
        }
        final Shop currentShop = shopUtils.getShop(shopLocation);
        if (currentShop == null || currentShop.getID() != shopId) {
            player.sendMessage(messageRegistry.getMessage(Message.ADMIN_TELEPORT_TARGET_EXPIRED));
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

        invalidateEphemeralState();

        // Reload configurations
        plugin.getShopChestConfig().reload(false, true, true);
        plugin.getHologramFormat().reload();
        plugin.getCmiWorthPriceAdvisor().refresh();
        plugin.getUpdater().restart();
        plugin.getPublicCatalogue().stop();
        plugin.getAdvertisingFeature().stop();

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
                        plugin.getPublicCatalogue().start();
                        plugin.getAdvertisingFeature().start();
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

    private void edit(String[] args, Player player) {
        final MessageRegistry messages = plugin.getLanguageManager().getMessageRegistry();
        final String fieldName = args[1].toLowerCase(java.util.Locale.ROOT);

        if (fieldName.equals("holograms")) {
            final ShopDisplayOrientation orientation;
            try {
                orientation = ShopDisplayOrientation.parse(args[2]);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(messages.getMessage(Message.EDIT_UNKNOWN_HOLOGRAM_ORIENTATION));
                return;
            }
            ClickType.setPlayerClickType(
                    player,
                    new EditClickType(new ShopEditOperation.Holograms(orientation)));
            player.sendMessage(messages.getMessage(Message.CLICK_CHEST_EDIT));
            return;
        }

        if (!fieldName.equals("amount")
                && !fieldName.equals("buy")
                && !fieldName.equals("sell")) {
            player.sendMessage(messages.getMessage(Message.EDIT_UNKNOWN_FIELD));
            return;
        }

        final ShopEditRequest request;
        try {
            request = ShopEditRequest.parse(fieldName, args[2]);
        } catch (NumberFormatException exception) {
            player.sendMessage(messages.getMessage(Message.AMOUNT_PRICE_NOT_NUMBER));
            return;
        }

        if (request.field() == ShopEditRequest.Field.AMOUNT && request.value() <= 0) {
            player.sendMessage(messages.getMessage(Message.AMOUNT_IS_ZERO));
            return;
        }
        if (request.field() != ShopEditRequest.Field.AMOUNT
                && (!Double.isFinite(request.value()) || request.value() < 0)) {
            player.sendMessage(messages.getMessage(Message.PRICES_INVALID));
            return;
        }
        if (request.field() != ShopEditRequest.Field.AMOUNT
                && !Config.allowDecimalsInPrice
                && request.value() != Math.rint(request.value())) {
            player.sendMessage(messages.getMessage(Message.PRICES_CONTAIN_DECIMALS));
            return;
        }

        ClickType.setPlayerClickType(
                player,
                new EditClickType(new ShopEditOperation.Terms(request)));
        player.sendMessage(messages.getMessage(Message.CLICK_CHEST_EDIT));
        plugin.debug(player.getName() + " can now select a shop to edit");
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
        ShopType shopType = selectClickType.getShopType();

        if (!validateShopProposal(
                p,
                itemStack,
                new ShopTerms(amount, buyPrice, sellPrice),
                Message.NO_PERMISSION_CREATE)) {
            return;
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

    protected void edit2(Player player, Shop selectedShop, EditClickType clickType) {
        final MessageRegistry messages = plugin.getLanguageManager().getMessageRegistry();

        if (!player.getUniqueId().equals(selectedShop.getVendor().getUniqueId())) {
            player.sendMessage(messages.getMessage(Message.NO_PERMISSION_EDIT_OTHERS));
            return;
        }
        if (selectedShop.getShopType() == ShopType.ADMIN
                && !player.hasPermission(Permissions.CREATE_ADMIN)) {
            player.sendMessage(messages.getMessage(Message.NO_PERMISSION_EDIT_ADMIN));
            return;
        }

        if (clickType.getOperation() instanceof ShopEditOperation.Holograms holograms) {
            editDisplayOrientation(player, selectedShop, holograms.orientation());
            return;
        }
        if (!(clickType.getOperation() instanceof ShopEditOperation.Terms terms)) {
            player.sendMessage(messages.getMessage(Message.SHOP_EDIT_FAILED));
            return;
        }

        final ShopTerms proposedTerms = terms.request().applyTo(
                ShopTerms.from(selectedShop));
        if (!validateShopProposal(
                player,
                selectedShop.getProduct().getItemStack(),
                proposedTerms,
                Message.NO_PERMISSION_EDIT)) {
            return;
        }

        final int shopId = selectedShop.getID();
        if (!pendingShopEdits.add(shopId)) {
            player.sendMessage(messages.getMessage(Message.SHOP_EDIT_PENDING));
            return;
        }

        plugin.getShopDatabase().updateShopTerms(
                shopId,
                proposedTerms,
                new Callback<Void>(plugin) {
                    @Override
                    public void onResult(Void result) {
                        pendingShopEdits.remove(shopId);
                        final Shop activeShop = shopUtils.getShop(selectedShop.getLocation());
                        if (activeShop != null && activeShop.getID() == shopId) {
                            activeShop.applyTerms(proposedTerms);
                        }
                        plugin.getPublicCatalogue().requestRefresh();

                        if (player.isOnline()) {
                            plugin.getCmiWorthPriceAdvisor().advise(
                                    player,
                                    new ShopProduct(
                                            selectedShop.getProduct().getItemStack(),
                                            proposedTerms.amount()),
                                    proposedTerms.buyPrice(),
                                    proposedTerms.sellPrice(),
                                    selectedShop.getShopType());
                            player.sendMessage(messages.getMessage(
                                    Message.SHOP_EDITED,
                                    new Replacement(Placeholder.AMOUNT, proposedTerms.amount()),
                                    new Replacement(
                                            Placeholder.ITEM_NAME,
                                            selectedShop.getProduct().getLocalizedName()),
                                    new Replacement(
                                            Placeholder.BUY_PRICE,
                                            proposedTerms.buyPrice()),
                                    new Replacement(
                                            Placeholder.SELL_PRICE,
                                            proposedTerms.sellPrice())));
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        pendingShopEdits.remove(shopId);
                        if (throwable != null) {
                            plugin.debug(throwable);
                        }
                        if (player.isOnline()) {
                            player.sendMessage(messages.getMessage(Message.SHOP_EDIT_FAILED));
                        }
                    }
                });
    }

    private void editDisplayOrientation(
            Player player,
            Shop selectedShop,
            ShopDisplayOrientation orientation
    ) {
        final MessageRegistry messages = plugin.getLanguageManager().getMessageRegistry();
        final ShopContainer container = selectedShop.getContainer();
        if (container == null) {
            player.sendMessage(messages.getMessage(Message.SHOP_DISPLAY_ORIENTATION_FAILED));
            return;
        }

        final BlockFace facing = orientation.resolve(
                container.getCenter(),
                player.getLocation());
        if (!container.setShopDisplayFacing(plugin, facing)) {
            player.sendMessage(messages.getMessage(Message.SHOP_DISPLAY_ORIENTATION_FAILED));
            return;
        }

        selectedShop.updateDisplayLocation();
        if (orientation == ShopDisplayOrientation.RESET) {
            player.sendMessage(messages.getMessage(Message.SHOP_DISPLAY_ORIENTATION_RESET));
        } else {
            player.sendMessage(messages.getMessage(
                    Message.SHOP_DISPLAY_ORIENTATION_UPDATED,
                    new Replacement(
                            Placeholder.VALUE,
                            facing.name().toLowerCase(java.util.Locale.ROOT))));
        }
        plugin.debug("Updated shop display orientation (#"
                + selectedShop.getID() + "): "
                + (facing == null ? "automatic" : facing));
    }

    private boolean validateShopProposal(
            Player player,
            ItemStack itemStack,
            ShopTerms terms,
            Message permissionFailure
    ) {
        final MessageRegistry messages = plugin.getLanguageManager().getMessageRegistry();
        final int amount = terms.amount();
        final double buyPrice = terms.buyPrice();
        final double sellPrice = terms.sellPrice();

        if (itemStack == null) {
            player.sendMessage(messages.getMessage(Message.NO_ITEM_IN_HAND));
            return false;
        }

        final boolean buyEnabled = buyPrice > 0;
        final boolean sellEnabled = sellPrice > 0;
        final java.util.Optional<ShopTermsValidator.Failure> basicFailure =
                ShopTermsValidator.validate(
                        terms,
                        Config.allowDecimalsInPrice,
                        Config.buyGreaterOrEqualSell);
        if (basicFailure.isPresent()) {
            sendShopTermsFailure(player, terms, basicFailure.get());
            return false;
        }
        if (!Utils.hasPermissionToCreateShop(
                player, itemStack, buyEnabled, sellEnabled)) {
            player.sendMessage(messages.getMessage(permissionFailure));
            return false;
        }

        for (String item : Config.blacklist) {
            final ItemStack configuredItem = ItemUtils.getItemStack(item);
            if (configuredItem == null) {
                plugin.getLogger().warning("Invalid item found in blacklist: " + item);
                plugin.debug("Invalid item in blacklist: " + item);
                continue;
            }
            if (ItemUtils.isSameTypeAndDamage(configuredItem, itemStack)) {
                player.sendMessage(messages.getMessage(Message.CANNOT_SELL_ITEM));
                return false;
            }
        }

        if (!validateConfiguredPriceBounds(
                player,
                itemStack,
                amount,
                buyPrice,
                sellPrice,
                buyEnabled,
                sellEnabled)) {
            return false;
        }

        if (UNBREAKING_ENCHANT.canEnchantItem(itemStack)
                && ItemUtils.getDamage(itemStack) > 0
                && !Config.allowBrokenItems) {
            player.sendMessage(messages.getMessage(Message.CANNOT_SELL_BROKEN_ITEM));
            return false;
        }

        return true;
    }

    private void sendShopTermsFailure(
            Player player,
            ShopTerms terms,
            ShopTermsValidator.Failure failure
    ) {
        final MessageRegistry messages = plugin.getLanguageManager().getMessageRegistry();
        switch (failure) {
            case AMOUNT_NOT_POSITIVE ->
                    player.sendMessage(messages.getMessage(Message.AMOUNT_IS_ZERO));
            case INVALID_PRICE ->
                    player.sendMessage(messages.getMessage(Message.PRICES_INVALID));
            case DECIMALS_NOT_ALLOWED ->
                    player.sendMessage(messages.getMessage(Message.PRICES_CONTAIN_DECIMALS));
            case NO_TRADE_DIRECTION ->
                    player.sendMessage(messages.getMessage(Message.BUY_SELL_DISABLED));
            case BUY_BELOW_SELL ->
                    player.sendMessage(messages.getMessage(
                            Message.BUY_PRICE_TOO_LOW,
                            new Replacement(
                                    Placeholder.MIN_PRICE,
                                    String.valueOf(terms.sellPrice()))));
        }
    }

    private boolean validateConfiguredPriceBounds(
            Player player,
            ItemStack itemStack,
            int amount,
            double buyPrice,
            double sellPrice,
            boolean buyEnabled,
            boolean sellEnabled
    ) {
        final MessageRegistry messages = plugin.getLanguageManager().getMessageRegistry();

        for (String key : Config.minimumPrices) {
            final ItemStack configuredItem = ItemUtils.getItemStack(key);
            if (configuredItem == null) {
                plugin.getLogger().warning("Invalid item found in minimum-prices: " + key);
                continue;
            }
            if (!ItemUtils.isSameTypeAndDamage(configuredItem, itemStack)) {
                continue;
            }

            final double minimum = amount * plugin.getConfig().getDouble("minimum-prices." + key);
            if (buyEnabled && buyPrice < minimum) {
                player.sendMessage(messages.getMessage(
                        Message.BUY_PRICE_TOO_LOW,
                        new Replacement(Placeholder.MIN_PRICE, String.valueOf(minimum))));
                return false;
            }
            if (sellEnabled && sellPrice < minimum) {
                player.sendMessage(messages.getMessage(
                        Message.SELL_PRICE_TOO_LOW,
                        new Replacement(Placeholder.MIN_PRICE, String.valueOf(minimum))));
                return false;
            }
        }

        for (String key : Config.maximumPrices) {
            final ItemStack configuredItem = ItemUtils.getItemStack(key);
            if (configuredItem == null) {
                plugin.getLogger().warning("Invalid item found in maximum-prices: " + key);
                continue;
            }
            if (!ItemUtils.isSameTypeAndDamage(configuredItem, itemStack)) {
                continue;
            }

            final double maximum = amount * plugin.getConfig().getDouble("maximum-prices." + key);
            if (buyEnabled && buyPrice > maximum) {
                player.sendMessage(messages.getMessage(
                        Message.BUY_PRICE_TOO_HIGH,
                        new Replacement(Placeholder.MAX_PRICE, String.valueOf(maximum))));
                return false;
            }
            if (sellEnabled && sellPrice > maximum) {
                player.sendMessage(messages.getMessage(
                        Message.SELL_PRICE_TOO_HIGH,
                        new Replacement(Placeholder.MAX_PRICE, String.valueOf(maximum))));
                return false;
            }
        }

        return true;
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

        ShopInspectionSupport.inspectOrSelect(
                p,
                shopUtils::getShop,
                (player, shop) -> {
                    ClickType.removePlayerClickType(player);
                    inspect(player, shop);
                },
                player -> {
                    plugin.debug(player.getName() + " can now click a shop");
                    player.sendMessage(messageRegistry.getMessage(Message.CLICK_CHEST_INFO));
                    ClickType.setPlayerClickType(
                            player,
                            new ClickType(ClickType.EnumClickType.INFO));
                });
    }

    void inspect(Player executor, Shop shop) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        plugin.debug(executor.getName() + " is retrieving shop info (#" + shop.getID() + ")");
        final ShopInfoEvent event = new ShopInfoEvent(executor, shop);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.debug("Info event cancelled (#" + shop.getID() + ")");
            return;
        }

        final Inventory inventory = shop.getInventory();
        if (inventory == null) {
            plugin.debug("Shop container is unavailable (#" + shop.getID() + ")");
            executor.sendMessage(messageRegistry.getMessage(
                    Message.ERROR_OCCURRED,
                    new Replacement(Placeholder.ERROR, "Shop container is unavailable")));
            return;
        }

        final ItemStack itemStack = shop.getProduct().getItemStack();
        final int amount = Utils.getAmount(inventory, itemStack);
        final int space = Utils.getFreeSpaceForItem(inventory, itemStack);
        final String vendorName = shop.getVendor().getName() == null
                ? shop.getVendor().getUniqueId().toString()
                : shop.getVendor().getName();
        final String vendorString = messageRegistry.getMessage(
                Message.SHOP_INFO_VENDOR,
                new Replacement(Placeholder.VENDOR, vendorName));

        final ShopProduct product = shop.getProduct();
        final Consumer<Player> productMessage = TextComponentHelper.getSendableItemInfo(
                messageRegistry.getMessage(
                        Message.SHOP_INFO_PRODUCT,
                        new Replacement(Placeholder.AMOUNT, product.getAmount())),
                Placeholder.ITEM_NAME.toString(),
                product.getItemStack(),
                product.getLocalizedNameComponent());
        final HologramItemDetails itemDetails = HologramItemDetails.from(
                product.getItemStack(),
                Config.hologramColors.textColor(HologramColorPalette.Role.DETAILS),
                Config.hologramColors.textColor(HologramColorPalette.Role.SEPARATOR));
        final Component itemDetailsMessage = itemDetails.isEmpty()
                ? Component.empty()
                : HologramTextFormatter.replaceComponents(
                        messageRegistry.getMessage(Message.SHOP_INFO_ITEM_DETAILS),
                        Map.of(
                                Placeholder.ITEM_DETAILS.toString(),
                                itemDetails.combined(
                                        Config.hologramMaxItemDetailEntries,
                                        Config.hologramItemDetailsPerLine,
                                        hiddenCount -> HologramTextFormatter.fromLegacy(
                                                        messageRegistry.getMessage(
                                                                Message.HOLOGRAM_MORE_ITEM_DETAILS,
                                                                new Replacement(
                                                                        Placeholder.DETAIL_COUNT,
                                                                        hiddenCount)))
                                                .color(Config.hologramColors.textColor(
                                                        HologramColorPalette.Role.SEPARATOR)))));

        final String disabled = messageRegistry.getMessage(Message.SHOP_INFO_DISABLED);
        final String priceString = messageRegistry.getMessage(
                Message.SHOP_INFO_PRICE,
                new Replacement(
                        Placeholder.BUY_PRICE,
                        shop.getBuyPrice() > 0 ? String.valueOf(shop.getBuyPrice()) : disabled),
                new Replacement(
                        Placeholder.SELL_PRICE,
                        shop.getSellPrice() > 0
                                ? String.valueOf(shop.getSellPrice())
                                : disabled));
        final String shopType = messageRegistry.getMessage(
                shop.getShopType() == ShopType.NORMAL
                        ? Message.SHOP_INFO_NORMAL
                        : Message.SHOP_INFO_ADMIN);
        final String stock = messageRegistry.getMessage(
                Message.SHOP_INFO_STOCK,
                new Replacement(Placeholder.STOCK, amount));
        final String chestSpace = messageRegistry.getMessage(
                Message.SHOP_INFO_CHEST_SPACE,
                new Replacement(Placeholder.CHEST_SPACE, space));
        final boolean canViewShopId = ShopInspectionSupport.canViewShopId(
                executor.getUniqueId(),
                shop.getVendor().getUniqueId(),
                executor.hasPermission(Permissions.ADMIN)
                        || executor.hasPermission(Permissions.ADMIN_LIST));

        executor.sendMessage(" ");
        if (canViewShopId) {
            executor.sendMessage(messageRegistry.getMessage(
                    Message.SHOP_INFO_ID,
                    new Replacement(Placeholder.SHOP_ID, shop.getID())));
        }
        if (shop.getShopType() != ShopType.ADMIN) {
            executor.sendMessage(vendorString);
        }
        productMessage.accept(executor);
        if (!itemDetails.isEmpty()) {
            executor.sendMessage(itemDetailsMessage);
        }
        if (shop.getShopType() != ShopType.ADMIN && shop.getBuyPrice() > 0) {
            executor.sendMessage(stock);
        }
        if (shop.getShopType() != ShopType.ADMIN && shop.getSellPrice() > 0) {
            executor.sendMessage(chestSpace);
        }
        executor.sendMessage(priceString);
        executor.sendMessage(shopType);
        executor.sendMessage(" ");
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
        boolean updateFloatingIconAnimation = isFloatingIconAnimationProperty(property);

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
        if (updateFloatingIconAnimation) {
            plugin.getShopItemAnimator().refresh();
        }

        return true;
    }

    private boolean isHologramLocationProperty(String property) {
        return property.equalsIgnoreCase("hologram-lift")
                || property.equalsIgnoreCase("hologram-fixed-bottom")
                || property.equalsIgnoreCase("floating-icon-height");
    }

    private boolean isHologramDisplayProperty(String property) {
        return property.equalsIgnoreCase("hologram-panel-width")
                || property.equalsIgnoreCase("hologram-text-scale")
                || property.equalsIgnoreCase("hologram-background-color")
                || property.equalsIgnoreCase("hologram-background-opacity")
                || property.equalsIgnoreCase("hologram-text-opacity")
                || property.equalsIgnoreCase("hologram-text-shadowed")
                || property.equalsIgnoreCase("hologram-text-see-through")
                || property.equalsIgnoreCase("hologram-text-alignment")
                || property.equalsIgnoreCase("hologram-max-item-name-length")
                || property.equalsIgnoreCase("hologram-max-item-detail-entries")
                || property.equalsIgnoreCase("hologram-item-details-per-line")
                || property.equalsIgnoreCase("hologram-fixed-facing")
                || property.regionMatches(true, 0, "hologram-colors.", 0, "hologram-colors.".length());
    }

    private boolean isFloatingIconAnimationProperty(String property) {
        return property.equalsIgnoreCase("floating-icon-scale")
                || property.equalsIgnoreCase("floating-icon-bobbing-enabled")
                || property.equalsIgnoreCase("floating-icon-bob-amplitude")
                || property.equalsIgnoreCase("floating-icon-bob-period-seconds")
                || property.equalsIgnoreCase("floating-icon-rotation-enabled")
                || property.equalsIgnoreCase("floating-icon-rotation-period-seconds");
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

    private record AuditScope(
            UUID ownerUuid,
            String pageSelector,
            String displayName
    ) {

        private static AuditScope all() {
            return new AuditScope(null, "all", "all registered shops");
        }

        private static AuditScope player(OfflinePlayer owner) {
            final UUID ownerUuid = owner.getUniqueId();
            final String displayName = owner.getName() == null
                    ? ownerUuid.toString()
                    : owner.getName();
            return new AuditScope(ownerUuid, ownerUuid.toString(), displayName);
        }
    }

    private record AuditCacheKey(String audience, UUID ownerUuid) {

        private static AuditCacheKey of(CommandSender sender, AuditScope scope) {
            final String audience = sender instanceof Player player
                    ? "player:" + player.getUniqueId()
                    : sender.getClass().getName() + ":" + sender.getName();
            return new AuditCacheKey(audience, scope.ownerUuid());
        }
    }

    private record AuditCacheEntry(ShopAuditReport report, long completedAtNanos) {

        private boolean expired(long now) {
            return now - completedAtNanos > ADMIN_AUDIT_CACHE_TTL_NANOS;
        }
    }
}
