package de.epiceric.shopchest.command;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.catalog.ListingAvailability;
import de.epiceric.shopchest.catalog.ListingCapacityState;
import de.epiceric.shopchest.catalog.RuntimeCatalogueEntry;
import de.epiceric.shopchest.catalog.RuntimeCatalogueListing;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.display.HologramTextFormatter;
import de.epiceric.shopchest.display.TextComponentHelper;
import de.epiceric.shopchest.sql.JdbcStorefrontRepository;
import de.epiceric.shopchest.storefront.StorefrontProfile;
import de.epiceric.shopchest.storefront.StorefrontProfileEligibility;
import de.epiceric.shopchest.storefront.StorefrontProfileField;
import de.epiceric.shopchest.storefront.StorefrontTextPolicy;
import de.epiceric.shopchest.storefront.FeaturedListingChoices;
import de.epiceric.shopchest.utils.Callback;
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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Player and staff command layer for persisted Storefront Profiles. */
final class StorefrontProfileCommandHandler {

    private static final int PAGE_SIZE = 4;
    private static final int MAX_ITEM_NAME_LENGTH = 48;

    private final ShopChest plugin;
    private final Set<UUID> pendingProfileUpdates = ConcurrentHashMap.newKeySet();

    StorefrontProfileCommandHandler(ShopChest plugin) {
        this.plugin = plugin;
    }

    boolean handle(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(Permissions.PROFILE)) {
            sender.sendMessage(Component.text(
                    "You do not have permission to use storefront profiles.",
                    NamedTextColor.RED));
            return true;
        }
        if (arguments.length >= 2 && arguments[1].equalsIgnoreCase("set")) {
            return setField(sender, arguments);
        }
        if (arguments.length >= 2 && arguments[1].equalsIgnoreCase("clear")) {
            return clearField(sender, arguments);
        }
        if (arguments.length >= 2 && arguments[1].equalsIgnoreCase("featured")) {
            return featured(sender, arguments);
        }
        return view(sender, arguments);
    }

    boolean handleModeration(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(Permissions.ADMIN_STOREFRONT)) {
            sender.sendMessage(Component.text(
                    "You do not have permission to moderate storefronts.",
                    NamedTextColor.RED));
            return true;
        }
        if (arguments.length != 4) {
            sender.sendMessage(Component.text(
                    "Usage: /" + Config.mainCommandName
                            + " admin storefront <player> <hide|show|suspend|unsuspend|clear>",
                    NamedTextColor.YELLOW));
            return true;
        }
        final OfflinePlayer owner = findOfflinePlayer(arguments[2]);
        if (owner == null) {
            sender.sendMessage(Component.text("That player is not cached on this server.", NamedTextColor.RED));
            return true;
        }
        final String action = arguments[3].toLowerCase(Locale.ROOT);
        repository().findProfile(owner.getUniqueId(), new Callback<Optional<StorefrontProfile>>(plugin) {
            @Override
            public void onResult(Optional<StorefrontProfile> result) {
                final StorefrontProfile current = result.orElseGet(() ->
                        StorefrontProfile.empty(owner.getUniqueId(), System.currentTimeMillis()));
                final StorefrontProfile updated = switch (action) {
                    case "hide" -> current.withModeration(true, current.suspended(), System.currentTimeMillis());
                    case "show" -> current.withModeration(false, current.suspended(), System.currentTimeMillis());
                    case "suspend" -> current.withModeration(current.textHidden(), true, System.currentTimeMillis());
                    case "unsuspend" -> current.withModeration(current.textHidden(), false, System.currentTimeMillis());
                    case "clear" -> StorefrontProfile.empty(owner.getUniqueId(), System.currentTimeMillis())
                            .withModeration(current.textHidden(), current.suspended(), System.currentTimeMillis());
                    default -> null;
                };
                if (updated == null) {
                    sender.sendMessage(Component.text("Unknown storefront moderation action.", NamedTextColor.RED));
                    return;
                }
                repository().saveProfile(updated, new Callback<Void>(plugin) {
                    @Override
                    public void onResult(Void ignored) {
                        plugin.getPublicCatalogue().applyProfileUpdate(updated);
                        plugin.getPublicCatalogue().requestRefresh();
                        sender.sendMessage(Component.text(
                                "Storefront moderation updated for " + displayOwnerName(owner) + ".",
                                NamedTextColor.GREEN));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        persistenceError(sender, throwable);
                    }
                });
            }

            @Override
            public void onError(Throwable throwable) {
                persistenceError(sender, throwable);
            }
        });
        return true;
    }

    private boolean setField(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can edit their storefront.", NamedTextColor.RED));
            return true;
        }
        if (arguments.length < 4) {
            profileEditHelp(sender);
            return true;
        }
        final StorefrontProfileField field;
        final String normalized;
        try {
            field = StorefrontProfileField.parse(arguments[2]);
            normalized = StorefrontTextPolicy.normalize(
                    field,
                    String.join(" ", java.util.Arrays.copyOfRange(
                            arguments, 3, arguments.length)));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            return true;
        }
        updateOwnProfile(player, profile -> profile.withField(
                field, normalized, System.currentTimeMillis()),
                "Your storefront " + field.name().toLowerCase(Locale.ROOT) + " was saved.");
        return true;
    }

    private boolean clearField(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can edit their storefront.", NamedTextColor.RED));
            return true;
        }
        if (arguments.length != 3) {
            profileEditHelp(sender);
            return true;
        }
        final StorefrontProfileField field;
        try {
            field = StorefrontProfileField.parse(arguments[2]);
        } catch (IllegalArgumentException exception) {
            profileEditHelp(sender);
            return true;
        }
        updateOwnProfile(player, profile -> profile.withField(
                field, null, System.currentTimeMillis()),
                "Your storefront " + field.name().toLowerCase(Locale.ROOT) + " was cleared.");
        return true;
    }

    private boolean featured(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can edit Featured Listings.", NamedTextColor.RED));
            return true;
        }
        if (arguments.length < 3 || arguments.length > 4) {
            featuredHelp(player);
            return true;
        }
        final String action = arguments[2].toLowerCase(Locale.ROOT);
        if (action.equals("clear") && arguments.length == 3) {
            saveFeatured(player, List.of(), "Your Featured Listings were cleared.");
            return true;
        }
        if ((!action.equals("add") && !action.equals("remove")) || arguments.length != 4) {
            featuredHelp(player);
            return true;
        }
        final int shopId;
        try {
            shopId = Integer.parseInt(arguments[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Shop ID must be a number.", NamedTextColor.RED));
            return true;
        }
        repository().findFeatured(player.getUniqueId(), new Callback<List<Integer>>(plugin) {
            @Override
            public void onResult(List<Integer> current) {
                final List<Integer> updated = new ArrayList<>(current);
                if (action.equals("add")) {
                    final boolean eligible = plugin.getPublicCatalogue()
                            .ownerEntries(player.getUniqueId()).stream()
                            .anyMatch(entry -> entry.shopId() == shopId
                                    && FeaturedListingChoices.isEligible(
                                            player.getUniqueId(), entry.candidate()));
                    if (!eligible) {
                        player.sendMessage(Component.text(
                                "That is not one of your eligible customer-buy shops.",
                                NamedTextColor.RED));
                        player.sendMessage(StorefrontCommandComponents.featuredPickerPrompt(
                                Config.mainCommandName));
                        return;
                    }
                    if (updated.contains(shopId)) {
                        player.sendMessage(Component.text(
                                "That shop is already featured.", NamedTextColor.YELLOW));
                        return;
                    }
                    if (updated.size() >= 3) {
                        player.sendMessage(Component.text(
                                "You can feature at most three shops. Remove one first.",
                                NamedTextColor.YELLOW));
                        return;
                    }
                    updated.add(shopId);
                } else if (!updated.remove(Integer.valueOf(shopId))) {
                    player.sendMessage(Component.text(
                            "That shop is not currently featured.", NamedTextColor.YELLOW));
                    return;
                }
                saveFeatured(player, updated, "Your Featured Listings were updated.");
            }

            @Override
            public void onError(Throwable throwable) {
                persistenceError(player, throwable);
            }
        });
        return true;
    }

    private boolean view(CommandSender sender, String[] arguments) {
        if (!plugin.getPublicCatalogue().isReady()) {
            sender.sendMessage(Component.text(
                    "The public shop catalogue is warming up. Please try again shortly.",
                    NamedTextColor.YELLOW));
            return true;
        }
        final Target target = parseTarget(sender, arguments);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "Usage: /" + Config.mainCommandName
                            + " profile [player|uuid] [shops [page]]",
                    NamedTextColor.YELLOW));
            return true;
        }
        repository().findProfile(target.owner().getUniqueId(),
                new Callback<Optional<StorefrontProfile>>(plugin) {
                    @Override
                    public void onResult(Optional<StorefrontProfile> result) {
                        final StorefrontProfile profile = result.orElseGet(() ->
                                StorefrontProfile.empty(
                                        target.owner().getUniqueId(), 0L));
                        if (profile.suspended()) {
                            sender.sendMessage(Component.text(
                                    "This storefront is not currently public.",
                                    NamedTextColor.YELLOW));
                            return;
                        }
                        final List<RuntimeCatalogueEntry> entries = plugin.getPublicCatalogue()
                                .ownerEntries(target.owner().getUniqueId());
                        if (entries.isEmpty()) {
                            sender.sendMessage(Component.text(
                                    "This player has no public shops at the marketplace right now.",
                                    NamedTextColor.YELLOW));
                            return;
                        }
                        plugin.getPublicCatalogue().inspectAll(entries, inspected ->
                                repository().findFeatured(target.owner().getUniqueId(),
                                        new Callback<List<Integer>>(plugin) {
                                            @Override
                                            public void onResult(List<Integer> featured) {
                                                if (target.listings()) {
                                                    renderListings(sender, target, profile, inspected, featured);
                                                } else {
                                                    renderOverview(sender, target.owner(), profile, inspected);
                                                }
                                            }

                                            @Override
                                            public void onError(Throwable throwable) {
                                                persistenceError(sender, throwable);
                                            }
                                        }));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        persistenceError(sender, throwable);
                    }
                });
        return true;
    }

    private void renderOverview(
            CommandSender sender,
            OfflinePlayer owner,
            StorefrontProfile profile,
            List<RuntimeCatalogueListing> listings
    ) {
        final String ownerName = displayOwnerName(owner);
        final String storefrontName = publicValue(profile, StorefrontProfile::name)
                .orElse("Shops by " + ownerName);
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text(storefrontName, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        if (!storefrontName.equals("Shops by " + ownerName)) {
            sender.sendMessage(Component.text("by " + ownerName, NamedTextColor.GRAY));
        }
        if (profile.textHidden()) {
            sender.sendMessage(Component.text(
                    "Storefront details are temporarily hidden by staff.",
                    NamedTextColor.YELLOW));
        } else {
            publicValue(profile, StorefrontProfile::tagline).ifPresent(value ->
                    sender.sendMessage(Component.text(value, NamedTextColor.AQUA)));
            publicValue(profile, StorefrontProfile::description).ifPresent(value ->
                    sender.sendMessage(Component.text("About: " + value, NamedTextColor.WHITE)));
            publicValue(profile, StorefrontProfile::directions).ifPresent(value ->
                    sender.sendMessage(Component.text("Find us: " + value, NamedTextColor.GRAY)));
        }

        final long buyOffers = listings.stream()
                .filter(listing -> listing.entry().customerBuyPrice() > 0.0D).count();
        final long sellOffers = listings.stream()
                .filter(listing -> listing.entry().customerSellPrice() > 0.0D).count();
        final long outOfStock = listings.stream()
                .filter(listing -> listing.entry().customerBuyPrice() > 0.0D)
                .filter(listing -> listing.stock().availability() == ListingAvailability.OUT_OF_STOCK)
                .count();
        final long uncheckedStock = listings.stream()
                .filter(listing -> listing.entry().customerBuyPrice() > 0.0D)
                .filter(listing -> listing.stock().availability() == ListingAvailability.UNCHECKED)
                .count();
        final long unavailableStock = listings.stream()
                .filter(listing -> listing.entry().customerBuyPrice() > 0.0D)
                .filter(listing -> listing.stock().availability() == ListingAvailability.UNAVAILABLE)
                .count();
        final long full = listings.stream()
                .filter(listing -> listing.entry().customerSellPrice() > 0.0D)
                .filter(listing -> listing.capacity().state() == ListingCapacityState.FULL)
                .count();
        final long uncheckedCapacity = listings.stream()
                .filter(listing -> listing.entry().customerSellPrice() > 0.0D)
                .filter(listing -> listing.capacity().state() == ListingCapacityState.UNCHECKED)
                .count();
        final long unavailableCapacity = listings.stream()
                .filter(listing -> listing.entry().customerSellPrice() > 0.0D)
                .filter(listing -> listing.capacity().state() == ListingCapacityState.UNAVAILABLE)
                .count();
        sender.sendMessage(Component.text(
                "Shops: " + listings.size() + " total • " + buyOffers
                        + " sell to players • " + sellOffers + " buy from players",
                NamedTextColor.GRAY));
        if (buyOffers > 0) {
            sender.sendMessage(Component.text(
                    "Customer-buy stock: " + outOfStock + " out of stock • "
                            + uncheckedStock + " unchecked • "
                            + unavailableStock + " unavailable",
                    NamedTextColor.GRAY));
        }
        if (sellOffers > 0) {
            sender.sendMessage(Component.text(
                    "Customer-sell capacity: " + full + " full • "
                            + uncheckedCapacity + " unchecked • "
                            + unavailableCapacity + " unavailable",
                    NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("[Browse this storefront's shops]", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("Show four shop listings per page")))
                .clickEvent(ClickEvent.runCommand(
                        "/" + Config.mainCommandName + " profile "
                                + owner.getUniqueId() + " shops 1")));
        if (listings.stream().anyMatch(listing -> listing.entry().marketplaceLocation())) {
            sender.sendMessage(Component.text("[/warp shops]", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/warp shops")));
        }
        sender.sendMessage(Component.empty());
    }

    private void renderListings(
            CommandSender sender,
            Target target,
            StorefrontProfile profile,
            List<RuntimeCatalogueListing> source,
            List<Integer> featured
    ) {
        final Map<Integer, Integer> featuredPosition = new HashMap<>();
        for (int position = 0; position < featured.size(); position++) {
            featuredPosition.put(featured.get(position), position);
        }
        final List<RuntimeCatalogueListing> ordered = source.stream()
                .sorted(Comparator
                        .comparingInt((RuntimeCatalogueListing listing) ->
                                featuredPosition.getOrDefault(
                                        listing.entry().shopId(), Integer.MAX_VALUE))
                        .thenComparingInt(listing -> listing.entry().shopId()))
                .toList();
        final PageSlice<RuntimeCatalogueListing> page = PageSlice.of(
                ordered, target.page(), PAGE_SIZE);
        final String ownerName = displayOwnerName(target.owner());
        final String title = publicValue(profile, StorefrontProfile::name)
                .orElse("Shops by " + ownerName);
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text(
                title + " • shops " + page.page() + "/" + page.pageCount(),
                NamedTextColor.GOLD,
                TextDecoration.BOLD));

        final Map<Integer, Location> staffTargets = new HashMap<>();
        final boolean managingOwnListings = sender instanceof Player player
                && player.getUniqueId().equals(target.owner().getUniqueId());
        for (RuntimeCatalogueListing listing : page.entries()) {
            renderProfileListing(
                    sender,
                    listing,
                    featuredPosition,
                    staffTargets,
                    managingOwnListings);
        }
        if (sender instanceof Player player && !staffTargets.isEmpty()) {
            plugin.getShopCommand().cacheAdminTeleportTargets(player, staffTargets);
        }
        renderNavigation(sender, target.owner().getUniqueId(), page);
        sender.sendMessage(Component.empty());
    }

    private void renderProfileListing(
            CommandSender sender,
            RuntimeCatalogueListing listing,
            Map<Integer, Integer> featured,
            Map<Integer, Location> staffTargets,
            boolean managingOwnListings
    ) {
        final RuntimeCatalogueEntry entry = listing.entry();
        final ItemStack productTemplate = entry.productTemplate();
        final String itemName = HologramTextFormatter.sanitizeItemName(
                plugin.getLanguageManager().getItemNameManager()
                        .getItemName(productTemplate),
                MAX_ITEM_NAME_LENGTH);
        final Component itemNameComponent = TextComponentHelper.withDetailedItemTooltip(
                Component.text(itemName, NamedTextColor.WHITE),
                productTemplate);
        final String marker = featured.containsKey(entry.shopId()) ? "★ " : "• ";
        Component listingLine = Component.text(marker, NamedTextColor.GOLD);
        if (managingOwnListings) {
            listingLine = listingLine.append(Component.text(
                    "#" + entry.shopId() + " • ", NamedTextColor.GRAY));
        }
        sender.sendMessage(listingLine
                .append(Component.text(entry.bundleAmount() + "x ", NamedTextColor.WHITE))
                .append(itemNameComponent));
        if (entry.customerBuyPrice() > 0.0D) {
            sender.sendMessage(Component.text(
                    "  Players buy for " + plugin.getEconomy().format(entry.customerBuyPrice())
                            + " • " + availabilityLabel(listing),
                    availabilityColor(listing)));
        }
        if (entry.customerSellPrice() > 0.0D) {
            sender.sendMessage(Component.text(
                    "  Shop buys for " + plugin.getEconomy().format(entry.customerSellPrice())
                            + " • " + capacityLabel(listing),
                    capacityColor(listing)));
        }

        if (managingOwnListings) {
            final boolean eligible = FeaturedListingChoices.isEligible(
                    entry.ownerId(), entry.candidate());
            if (eligible && featured.containsKey(entry.shopId())) {
                sender.sendMessage(Component.text(
                                "  ★ Featured shop #" + entry.shopId(),
                                NamedTextColor.GOLD)
                        .append(Component.space())
                        .append(StorefrontCommandComponents.removeFeaturedAction(
                                Config.mainCommandName, entry.shopId())));
            } else if (eligible) {
                sender.sendMessage(Component.text("  ")
                        .append(StorefrontCommandComponents.addFeaturedAction(
                                Config.mainCommandName, entry.shopId())));
            } else {
                sender.sendMessage(Component.text(
                        "  Not eligible for Featured Listings: no customer-buy offer",
                        NamedTextColor.DARK_GRAY));
            }
        }

        final Location location = entry.location();
        Component locationLine = Component.text(
                "  " + location.getWorld().getName() + " "
                        + location.getBlockX() + ", " + location.getBlockY() + ", "
                        + location.getBlockZ(),
                NamedTextColor.DARK_GRAY);
        if (sender instanceof Player player && player.hasPermission(Permissions.ADMIN_LIST)) {
            staffTargets.put(entry.shopId(), location);
            locationLine = locationLine.color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand(
                            "/" + Config.mainCommandName + " admin teleport " + entry.shopId()))
                    .hoverEvent(HoverEvent.showText(Component.text("Staff: teleport to this shop")));
        }
        sender.sendMessage(locationLine);
    }

    private void renderNavigation(
            CommandSender sender,
            UUID ownerId,
            PageSlice<RuntimeCatalogueListing> page
    ) {
        if (page.pageCount() <= 1) {
            return;
        }
        final String base = "/" + Config.mainCommandName + " profile "
                + ownerId + " shops ";
        Component navigation = Component.empty();
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

    private Target parseTarget(CommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            return sender instanceof Player player
                    ? new Target(player, false, 1)
                    : null;
        }
        if (arguments[1].equalsIgnoreCase("shops")) {
            if (!(sender instanceof Player player) || arguments.length > 3) {
                return null;
            }
            return new Target(player, true, parsePage(arguments.length == 3 ? arguments[2] : null));
        }
        final OfflinePlayer owner = findOfflinePlayer(arguments[1]);
        if (owner == null) {
            return null;
        }
        if (arguments.length == 2) {
            return new Target(owner, false, 1);
        }
        if (arguments.length >= 3 && arguments[2].equalsIgnoreCase("shops")
                && arguments.length <= 4) {
            return new Target(owner, true, parsePage(arguments.length == 4 ? arguments[3] : null));
        }
        return null;
    }

    private int parsePage(String value) {
        if (value == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void updateOwnProfile(
            Player player,
            java.util.function.UnaryOperator<StorefrontProfile> update,
            String success
    ) {
        if (!pendingProfileUpdates.add(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "Your previous storefront change is still being saved.",
                    NamedTextColor.YELLOW));
            return;
        }
        repository().findProfile(player.getUniqueId(),
                new Callback<Optional<StorefrontProfile>>(plugin) {
                    @Override
                    public void onResult(Optional<StorefrontProfile> result) {
                        final boolean catalogueReady = plugin.getPublicCatalogue().isReady();
                        final boolean hasEligibleListing = catalogueReady
                                && !plugin.getPublicCatalogue()
                                .ownerEntries(player.getUniqueId()).isEmpty();
                        if (!StorefrontProfileEligibility.canEdit(
                                result.isPresent(), catalogueReady, hasEligibleListing)) {
                            pendingProfileUpdates.remove(player.getUniqueId());
                            player.sendMessage(Component.text(
                                    catalogueReady
                                            ? "Create an eligible normal shop before publishing a storefront profile."
                                            : "The public shop catalogue is warming up. Please try again shortly.",
                                    NamedTextColor.YELLOW));
                            return;
                        }
                        final StorefrontProfile current = result.orElseGet(() ->
                                StorefrontProfile.empty(
                                        player.getUniqueId(), System.currentTimeMillis()));
                        final StorefrontProfile updated = update.apply(current);
                        repository().saveProfile(updated, new Callback<Void>(plugin) {
                            @Override
                            public void onResult(Void ignored) {
                                pendingProfileUpdates.remove(player.getUniqueId());
                                plugin.getPublicCatalogue().applyProfileUpdate(updated);
                                plugin.getPublicCatalogue().requestRefresh();
                                player.sendMessage(Component.text(success, NamedTextColor.GREEN));
                                player.sendMessage(Component.text(
                                        "Preview: /" + Config.mainCommandName + " profile",
                                        NamedTextColor.GRAY));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                pendingProfileUpdates.remove(player.getUniqueId());
                                persistenceError(player, throwable);
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        pendingProfileUpdates.remove(player.getUniqueId());
                        persistenceError(player, throwable);
                    }
                });
    }

    private void saveFeatured(Player player, List<Integer> featured, String success) {
        repository().replaceFeatured(player.getUniqueId(), featured, new Callback<Void>(plugin) {
            @Override
            public void onResult(Void ignored) {
                player.sendMessage(Component.text(success, NamedTextColor.GREEN));
            }

            @Override
            public void onError(Throwable throwable) {
                persistenceError(player, throwable);
            }
        });
    }

    private JdbcStorefrontRepository repository() {
        return plugin.getStorefrontRepository();
    }

    private OfflinePlayer findOfflinePlayer(String nameOrUuid) {
        final Player online = Bukkit.getPlayerExact(nameOrUuid);
        if (online != null) {
            return online;
        }
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException ignored) {
            return Bukkit.getOfflinePlayerIfCached(nameOrUuid);
        }
    }

    private static String displayOwnerName(OfflinePlayer owner) {
        return owner.getName() == null
                ? owner.getUniqueId().toString().substring(0, 8)
                : owner.getName();
    }

    private Optional<String> publicValue(
            StorefrontProfile profile,
            java.util.function.Function<StorefrontProfile, String> getter
    ) {
        if (profile.textHidden()) {
            return Optional.empty();
        }
        return Optional.ofNullable(getter.apply(profile)).filter(value -> !value.isBlank());
    }

    private String availabilityLabel(RuntimeCatalogueListing listing) {
        return switch (listing.stock().availability()) {
            case IN_STOCK -> listing.stock().completeBundles() + " bundles in stock";
            case OUT_OF_STOCK -> "Out of stock";
            case UNCHECKED -> "Stock unchecked";
            case UNAVAILABLE -> "Unavailable";
        };
    }

    private NamedTextColor availabilityColor(RuntimeCatalogueListing listing) {
        return switch (listing.stock().availability()) {
            case IN_STOCK -> NamedTextColor.GREEN;
            case UNCHECKED -> NamedTextColor.YELLOW;
            case OUT_OF_STOCK, UNAVAILABLE -> NamedTextColor.RED;
        };
    }

    private String capacityLabel(RuntimeCatalogueListing listing) {
        return switch (listing.capacity().state()) {
            case CAN_ACCEPT -> "room for " + listing.capacity().completeBundles()
                    + " complete "
                    + (listing.capacity().completeBundles() == 1 ? "bundle" : "bundles");
            case FULL -> "Full for this bundle";
            case UNCHECKED -> "Capacity unchecked";
            case UNAVAILABLE -> "Unavailable";
        };
    }

    private NamedTextColor capacityColor(RuntimeCatalogueListing listing) {
        return switch (listing.capacity().state()) {
            case CAN_ACCEPT -> NamedTextColor.GREEN;
            case UNCHECKED -> NamedTextColor.YELLOW;
            case FULL, UNAVAILABLE -> NamedTextColor.RED;
        };
    }

    private void profileEditHelp(CommandSender sender) {
        sender.sendMessage(Component.text(
                "Use /" + Config.mainCommandName
                        + " profile set <name|advertisement|description|location> <text>",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "Use /" + Config.mainCommandName + " profile clear <field>",
                NamedTextColor.GRAY));
    }

    private void featuredHelp(CommandSender sender) {
        sender.sendMessage(Component.text(
                "Use /" + Config.mainCommandName
                        + " profile featured <add|remove> <shop-id>, or featured clear.",
                NamedTextColor.YELLOW));
        sender.sendMessage(StorefrontCommandComponents.featuredPickerPrompt(
                Config.mainCommandName));
    }

    private void persistenceError(CommandSender sender, Throwable throwable) {
        plugin.debug(throwable);
        sender.sendMessage(Component.text(
                "Storefront data could not be saved or loaded. Please try again.",
                NamedTextColor.RED));
    }

    private record Target(OfflinePlayer owner, boolean listings, int page) {
    }
}
