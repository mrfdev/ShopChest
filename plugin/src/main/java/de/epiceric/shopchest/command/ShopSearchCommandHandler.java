package de.epiceric.shopchest.command;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.catalog.MaterialKeyResolver;
import de.epiceric.shopchest.catalog.PublicShopListing;
import de.epiceric.shopchest.catalog.ReconciledSearchPage;
import de.epiceric.shopchest.catalog.ResolvedMaterial;
import de.epiceric.shopchest.catalog.RuntimeCatalogueEntry;
import de.epiceric.shopchest.catalog.RuntimeCatalogueListing;
import de.epiceric.shopchest.catalog.ShopSearchPage;
import de.epiceric.shopchest.catalog.ShopSearchPageReconciler;
import de.epiceric.shopchest.catalog.ShopSearchRequest;
import de.epiceric.shopchest.catalog.ShopSearchRequestParser;
import de.epiceric.shopchest.catalog.ShopSearchSnapshot;
import de.epiceric.shopchest.catalog.ShopSearchSummary;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.display.HologramTextFormatter;
import de.epiceric.shopchest.storefront.StorefrontProfile;
import de.epiceric.shopchest.utils.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Player-facing `/shops search` orchestration and rendering. */
final class ShopSearchCommandHandler {

    private static final int MAX_ITEM_NAME_LENGTH = 48;

    private final ShopChest plugin;
    private final ShopSearchRequestParser parser =
            new ShopSearchRequestParser(new MaterialKeyResolver());
    private final Map<SearchKey, CachedSearch> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSearchAt = new ConcurrentHashMap<>();
    private final SearchRequestGuard requestGuard = new SearchRequestGuard();

    ShopSearchCommandHandler(ShopChest plugin) {
        this.plugin = plugin;
    }

    boolean handle(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(Permissions.SEARCH)) {
            sender.sendMessage(Component.text(
                    "You do not have permission to search player shops.",
                    NamedTextColor.RED));
            return true;
        }
        final var parsed = parser.parse(Arrays.asList(arguments).subList(1, arguments.length));
        if (parsed.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Usage: /" + Config.mainCommandName + " search <item> [page]",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(
                    "Use a base item name such as stone_bricks or minecraft:stone_bricks.",
                    NamedTextColor.GRAY));
            renderMaterialSuggestions(sender, arguments);
            return true;
        }
        if (!plugin.getPublicCatalogue().isReady()) {
            sender.sendMessage(Component.text(
                    "The shop catalogue is warming up. Please try again in a moment.",
                    NamedTextColor.YELLOW));
            return true;
        }

        final ShopSearchRequest request = parsed.orElseThrow();
        final String viewerKey = viewerKey(sender);
        final SearchKey cacheKey = new SearchKey(viewerKey, request.material().canonicalKey());
        final CachedSearch cached = cache.get(cacheKey);
        final long now = System.nanoTime();
        if (request.requestedPage() > 1
                && cached != null
                && requestGuard.isCurrent(cached.ticket())
                && cached.expiresAtNanos() > now) {
            render(sender, cached, request.requestedPage());
            return true;
        }

        final long cooldownNanos = TimeUnit.MILLISECONDS.toNanos(
                Config.storefrontSearchCooldownMillis);
        final long previous = lastSearchAt.getOrDefault(viewerKey, Long.MIN_VALUE / 2);
        if (now - previous < cooldownNanos) {
            sender.sendMessage(Component.text(
                    "Please wait a moment before starting another shop search.",
                    NamedTextColor.YELLOW));
            return true;
        }
        final var ticket = requestGuard.tryStart(viewerKey);
        if (ticket.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Your previous shop search is still running.",
                    NamedTextColor.YELLOW));
            return true;
        }
        final SearchRequestGuard.Ticket requestTicket = ticket.orElseThrow();
        lastSearchAt.put(viewerKey, now);
        sender.sendMessage(Component.text(
                "Searching in-stock shops for " + friendlyMaterial(request.material()) + "...",
                NamedTextColor.GRAY));

        final List<RuntimeCatalogueEntry> candidates = plugin.getPublicCatalogue()
                .customerBuyEntries(request.material().material());
        plugin.getPublicCatalogue().inspectAll(candidates, inspected -> {
            try {
                if (!requestGuard.isCurrent(requestTicket)
                        || sender instanceof Player player && !player.isOnline()) {
                    return;
                }
                final List<PublicShopListing> listings = inspected.stream()
                        .map(listing -> new PublicShopListing(
                                listing.entry().candidate(),
                                listing.entry().productTemplate(),
                                listing.stock()))
                        .toList();
                final ShopSearchSnapshot snapshot = ShopSearchSnapshot.capture(
                        request.material(), Instant.now(), listings);
                final Map<Integer, RuntimeCatalogueEntry> byId = new HashMap<>();
                candidates.forEach(entry -> byId.put(entry.shopId(), entry));
                final CachedSearch completed = new CachedSearch(
                        snapshot,
                        Map.copyOf(byId),
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(
                                Config.storefrontSnapshotSeconds),
                        requestTicket);
                cache.put(cacheKey, completed);
                if (!requestGuard.isCurrent(requestTicket)) {
                    cache.remove(cacheKey, completed);
                    return;
                }
                trimCache();
                render(sender, completed, request.requestedPage());
            } finally {
                requestGuard.finish(requestTicket);
            }
        });
        return true;
    }

    void invalidate() {
        requestGuard.invalidate();
        cache.clear();
        lastSearchAt.clear();
    }

    private void render(CommandSender sender, CachedSearch cached, int requestedPage) {
        if (!requestGuard.isCurrent(cached.ticket())) {
            return;
        }
        final ShopSearchPage page = cached.snapshot().page(requestedPage);
        final List<RuntimeCatalogueEntry> currentEntries = page.listings().stream()
                .map(listing -> cached.entriesById().get(listing.candidate().shopId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        plugin.getPublicCatalogue().inspectAll(currentEntries, inspected -> {
            if (!requestGuard.isCurrent(cached.ticket())
                    || sender instanceof Player player && !player.isOnline()) {
                return;
            }
            final Map<Integer, de.epiceric.shopchest.catalog.ListingStock> currentStock =
                    new HashMap<>();
            inspected.forEach(listing -> currentStock.put(
                    listing.entry().shopId(), listing.stock()));
            renderValidated(
                    sender,
                    cached,
                    page,
                    ShopSearchPageReconciler.reconcile(page, currentStock));
        });
    }

    private void renderValidated(
            CommandSender sender,
            CachedSearch cached,
            ShopSearchPage page,
            ReconciledSearchPage reconciled
    ) {
        final ShopSearchSummary summary = page.summary();
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Shop search: ", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(
                        friendlyMaterial(page.material()) + "  " + page.page() + "/" + page.pageCount(),
                        NamedTextColor.WHITE,
                        TextDecoration.BOLD)));

        if (summary.inStockShops() == 0) {
            sender.sendMessage(Component.text(
                    "No in-stock player shop currently sells this item.",
                    NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text(
                    "Found " + summary.inStockShops() + " in-stock "
                            + plural(summary.inStockShops(), "shop", "shops")
                            + " across " + summary.inStockStorefronts() + " "
                            + plural(summary.inStockStorefronts(), "storefront", "storefronts") + ".",
                    NamedTextColor.GREEN));
        }
        if (summary.outOfStockShops() > 0 || summary.uncheckedShops() > 0) {
            final StringBuilder note = new StringBuilder();
            if (summary.outOfStockShops() > 0) {
                note.append(summary.outOfStockShops())
                        .append(" more ")
                        .append(plural(summary.outOfStockShops(), "shop is", "shops are"))
                        .append(" out of stock");
            }
            if (summary.uncheckedShops() > 0) {
                if (!note.isEmpty()) {
                    note.append("; ");
                }
                note.append(summary.uncheckedShops())
                        .append(" could not be checked because their chunks are unloaded");
            }
            sender.sendMessage(Component.text(note + ".", NamedTextColor.GRAY));
        }
        if (reconciled.changedRows() > 0) {
            sender.sendMessage(Component.text(
                    reconciled.changedRows() + " "
                            + plural(reconciled.changedRows(), "result", "results")
                            + " changed"
                            + " since this search and "
                            + plural(reconciled.changedRows(), "was", "were")
                            + " omitted. Start a new search to refresh totals.",
                    NamedTextColor.YELLOW));
        }

        final Map<Integer, Location> staffTargets = new HashMap<>();
        for (PublicShopListing listing : reconciled.listings()) {
            final RuntimeCatalogueEntry entry = cached.entriesById()
                    .get(listing.candidate().shopId());
            if (entry == null) {
                continue;
            }
            renderListing(sender, entry, listing, staffTargets);
        }
        if (sender instanceof Player player && !staffTargets.isEmpty()) {
            plugin.getShopCommand().cacheAdminTeleportTargets(player, staffTargets);
        }

        final boolean hasMarketplaceListing = cached.entriesById().values().stream()
                .anyMatch(RuntimeCatalogueEntry::marketplaceLocation);
        if (hasMarketplaceListing) {
            sender.sendMessage(Component.text("Visit ", NamedTextColor.GRAY)
                    .append(Component.text("/warp shops", NamedTextColor.AQUA)
                            .decorate(TextDecoration.UNDERLINED)
                            .hoverEvent(HoverEvent.showText(Component.text(
                                    "Click to travel to the player marketplace")))
                            .clickEvent(ClickEvent.runCommand("/warp shops")))
                    .append(Component.text(" to browse in person.", NamedTextColor.GRAY)));
        }
        renderNavigation(sender, page);
        sender.sendMessage(Component.empty());
    }

    private void renderListing(
            CommandSender sender,
            RuntimeCatalogueEntry entry,
            PublicShopListing listing,
            Map<Integer, Location> staffTargets
    ) {
        final String itemName = HologramTextFormatter.sanitizeItemName(
                plugin.getLanguageManager().getItemNameManager()
                        .getItemName(entry.productTemplate()),
                MAX_ITEM_NAME_LENGTH);
        final String ownerName = ownerName(entry.ownerId());
        final String storefrontName = plugin.getPublicCatalogue().profile(entry.ownerId())
                .filter(profile -> !profile.textHidden())
                .map(StorefrontProfile::name)
                .filter(value -> value != null && !value.isBlank())
                .orElse("Shops by " + ownerName);
        final double unitPrice = entry.customerBuyPrice() / entry.bundleAmount();

        sender.sendMessage(Component.text("• ", NamedTextColor.DARK_GRAY)
                .append(Component.text(
                        entry.bundleAmount() + "x " + itemName,
                        NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.GRAY))
                .append(Component.text(
                        plugin.getEconomy().format(entry.customerBuyPrice()),
                        NamedTextColor.GREEN))
                .append(Component.text(
                        " (" + plugin.getEconomy().format(unitPrice) + " each)",
                        NamedTextColor.DARK_GRAY)));

        final Component storefront = Component.text(storefrontName, NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "View " + ownerName + "'s storefront profile")))
                .clickEvent(ClickEvent.runCommand(
                        "/" + Config.mainCommandName + " profile " + entry.ownerId()));
        sender.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                .append(storefront)
                .append(Component.text(
                        " • " + listing.stock().completeBundles() + " full "
                                + plural(listing.stock().completeBundles(), "bundle", "bundles")
                                + " available",
                        NamedTextColor.GRAY)));

        final Location location = entry.location();
        Component locationLine = Component.text(
                "  Location: " + location.getWorld().getName() + " "
                        + location.getBlockX() + ", " + location.getBlockY() + ", "
                        + location.getBlockZ(),
                NamedTextColor.DARK_GRAY);
        if (sender instanceof Player player && player.hasPermission(Permissions.ADMIN_LIST)) {
            staffTargets.put(entry.shopId(), location);
            locationLine = locationLine
                    .color(NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "Staff: click to teleport after a live permission and target check")))
                    .clickEvent(ClickEvent.runCommand(
                            "/" + Config.mainCommandName + " admin teleport " + entry.shopId()));
        }
        sender.sendMessage(locationLine);
    }

    private void renderNavigation(CommandSender sender, ShopSearchPage page) {
        if (page.pageCount() <= 1) {
            return;
        }
        Component navigation = Component.empty();
        final String base = "/" + Config.mainCommandName + " search "
                + page.material().canonicalKey() + " ";
        if (page.page() > 1) {
            navigation = navigation.append(Component.text("[Previous]", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand(base + (page.page() - 1))));
        }
        if (page.page() > 1 && page.page() < page.pageCount()) {
            navigation = navigation.append(Component.space());
        }
        if (page.page() < page.pageCount()) {
            navigation = navigation.append(Component.text("[Next]", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand(base + (page.page() + 1))));
        }
        sender.sendMessage(navigation);
    }

    private void renderMaterialSuggestions(CommandSender sender, String[] arguments) {
        if (arguments.length < 2 || !plugin.getPublicCatalogue().isReady()) {
            return;
        }
        final List<String> queryArguments = new java.util.ArrayList<>(
                Arrays.asList(arguments).subList(1, arguments.length));
        if (queryArguments.size() > 1
                && queryArguments.getLast().matches("[1-9][0-9]*")) {
            queryArguments.removeLast();
        }
        final List<ResolvedMaterial> suggestions = plugin.getPublicCatalogue()
                .suggestMaterials(String.join(" ", queryArguments), 3);
        if (suggestions.isEmpty()) {
            return;
        }

        Component line = Component.text("Did you mean: ", NamedTextColor.GRAY);
        for (int index = 0; index < suggestions.size(); index++) {
            final ResolvedMaterial suggestion = suggestions.get(index);
            if (index > 0) {
                line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(Component.text(
                            friendlyMaterial(suggestion), NamedTextColor.AQUA)
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "Search for " + suggestion.canonicalKey())))
                    .clickEvent(ClickEvent.runCommand(
                            "/" + Config.mainCommandName + " search "
                                    + suggestion.canonicalKey())));
        }
        sender.sendMessage(line);
    }

    private String ownerName(UUID ownerId) {
        final OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        return owner.getName() == null ? ownerId.toString().substring(0, 8) : owner.getName();
    }

    private static String friendlyMaterial(ResolvedMaterial material) {
        final String path = material.canonicalKey().replaceFirst("^minecraft:", "");
        final String[] words = path.split("_");
        final StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }

    private static String plural(int amount, String singular, String plural) {
        return amount == 1 ? singular : plural;
    }

    private static String viewerKey(CommandSender sender) {
        return sender instanceof Player player
                ? player.getUniqueId().toString()
                : "console:" + sender.getName().toLowerCase(Locale.ROOT);
    }

    private void trimCache() {
        final long now = System.nanoTime();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
        if (cache.size() <= 256) {
            return;
        }
        final Set<SearchKey> excess = new HashSet<>(cache.keySet());
        excess.stream().limit(cache.size() - 256L).forEach(cache::remove);
    }

    private record SearchKey(String viewer, String material) {
    }

    private record CachedSearch(
            ShopSearchSnapshot snapshot,
            Map<Integer, RuntimeCatalogueEntry> entriesById,
            long expiresAtNanos,
            SearchRequestGuard.Ticket ticket
    ) {
    }
}
