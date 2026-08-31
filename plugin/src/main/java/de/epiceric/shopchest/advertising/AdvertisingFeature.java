package de.epiceric.shopchest.advertising;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.catalog.ListingAvailability;
import de.epiceric.shopchest.catalog.RuntimeCatalogueEntry;
import de.epiceric.shopchest.catalog.RuntimeCatalogueListing;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.display.HologramTextFormatter;
import de.epiceric.shopchest.sql.JdbcAdvertisingRepository;
import de.epiceric.shopchest.storefront.StorefrontProfile;
import de.epiceric.shopchest.utils.Callback;
import de.epiceric.shopchest.utils.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Lifecycle aggregate for captured currency, durable passes, and FIFO broadcasts. */
public final class AdvertisingFeature {

    private final ShopChest plugin;
    private final AdvertisingCurrencyTemplateStore currencyStore;
    private final AtomicBoolean dispatching = new AtomicBoolean();
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    private final Set<UUID> purchaseLocks = ConcurrentHashMap.newKeySet();
    private final ItemStackEscrowCodec escrowCodec = new ItemStackEscrowCodec();

    private AdvertisingLifecyclePolicy policy;
    private BukkitTask scheduler;
    private volatile boolean started;

    public AdvertisingFeature(ShopChest plugin) {
        this.plugin = plugin;
        this.currencyStore = new AdvertisingCurrencyTemplateStore(plugin);
        reloadPolicy();
    }

    public void start() {
        stop();
        final long generation = lifecycleGeneration.incrementAndGet();
        reloadPolicy();
        currencyStore.loadAsync(() -> {
            if (lifecycleGeneration.get() != generation || !plugin.isEnabled()) {
                return;
            }
            started = true;
            scheduler = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    this::pollQueue,
                    Config.advertisingPollSeconds * 20L,
                    Config.advertisingPollSeconds * 20L);
            for (Player player : Bukkit.getOnlinePlayers()) {
                recoverPurchase(player);
            }
        });
    }

    public void stop() {
        lifecycleGeneration.incrementAndGet();
        started = false;
        dispatching.set(false);
        purchaseLocks.clear();
        if (scheduler != null) {
            scheduler.cancel();
            scheduler = null;
        }
    }

    public boolean isReady() {
        return started && currencyStore.isLoaded()
                && plugin.getShopDatabase().isInitialized()
                && plugin.getPublicCatalogue().isReady();
    }

    public AdvertisingCurrencyTemplateStore currencyStore() {
        return currencyStore;
    }

    public AdvertisingLifecyclePolicy policy() {
        return policy;
    }

    public JdbcAdvertisingRepository repository() {
        return plugin.getAdvertisingRepository();
    }

    /**
     * Locks a player's storage inventory for the short compare/remove/deliver window.
     * The command handler performs every inventory operation on the main server thread.
     */
    public boolean tryLockPurchase(UUID playerId) {
        return purchaseLocks.add(playerId);
    }

    public void unlockPurchase(UUID playerId) {
        purchaseLocks.remove(playerId);
    }

    public boolean isPurchaseLocked(UUID playerId) {
        return purchaseLocks.contains(playerId);
    }

    /** Recovers the owner's single unresolved purchase after reconnect or restart. */
    public void recoverPurchase(Player player) {
        if (!started || !player.isOnline() || !tryLockPurchase(player.getUniqueId())) {
            return;
        }
        repository().findUnresolvedPurchase(
                player.getUniqueId(),
                new Callback<Optional<AdvertisingPassPurchase>>(plugin) {
                    @Override
                    public void onResult(Optional<AdvertisingPassPurchase> result) {
                        if (result.isEmpty()) {
                            unlockPurchase(player.getUniqueId());
                            return;
                        }
                        recoverPurchaseRecord(player, result.orElseThrow());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        releaseRecovery(player, throwable);
                    }
                });
    }

    /** Resolves a known purchase after an ambiguous delivery callback. */
    public void recoverPurchase(Player player, String nonce) {
        if (!started || !player.isOnline() || !tryLockPurchase(player.getUniqueId())) {
            return;
        }
        repository().findPurchase(
                nonce,
                new Callback<Optional<AdvertisingPassPurchase>>(plugin) {
                    @Override
                    public void onResult(Optional<AdvertisingPassPurchase> result) {
                        if (result.isEmpty()) {
                            releaseRecovery(player, new IllegalStateException(
                                    "Advertising purchase recovery record is missing"));
                            return;
                        }
                        recoverPurchaseRecord(player, result.orElseThrow());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        releaseRecovery(player, throwable);
                    }
                });
    }

    private void recoverPurchaseRecord(Player player, AdvertisingPassPurchase purchase) {
        if (!player.isOnline()) {
            unlockPurchase(player.getUniqueId());
            return;
        }
        if (purchase.status().isTerminal()) {
            finishTerminalRecovery(player, purchase);
            return;
        }

        final PurchaseEscrowEvidence<ItemStack> evidence;
        final PurchaseInventoryState inventoryState;
        try {
            evidence = escrowCodec.decode(purchase.escrowPayload());
            inventoryState = evidence.classify(storageSnapshot(player.getInventory()));
        } catch (RuntimeException exception) {
            leavePendingForStaff(player, purchase, exception);
            return;
        }

        switch (AdvertisingPurchaseRecoveryPolicy.decide(
                purchase.status(), inventoryState)) {
            case MARK_NOT_CHARGED -> markNotCharged(player, purchase);
            case RETRY_DELIVERY -> retryPassDelivery(player, purchase);
            case RESTORE_REFUND -> restoreExactRefund(player, purchase, evidence);
            case MARK_REFUNDED -> markRefunded(player, purchase);
            case WAIT_FOR_EXACT_EVIDENCE -> leavePendingForStaff(player, purchase, null);
            case NONE -> finishTerminalRecovery(player, purchase);
        }
    }

    private void retryPassDelivery(Player player, AdvertisingPassPurchase purchase) {
        repository().deliverPreparedPurchase(
                purchase.nonce(),
                new Callback<AdvertisingPass>(plugin) {
                    @Override
                    public void onResult(AdvertisingPass pass) {
                        unlockPurchase(player.getUniqueId());
                        player.sendMessage(Component.text(
                                "Your interrupted Advertising Pass purchase was delivered.",
                                NamedTextColor.GREEN));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (throwable instanceof AdvertisingPurchaseDeliveryRejectedException) {
                            repository().markRefundPending(
                                    purchase.nonce(),
                                    throwable.getMessage(),
                                    new Callback<Void>(plugin) {
                                        @Override
                                        public void onResult(Void ignored) {
                                            repository().findPurchase(
                                                    purchase.nonce(),
                                                    new Callback<Optional<AdvertisingPassPurchase>>(plugin) {
                                                        @Override
                                                        public void onResult(
                                                                Optional<AdvertisingPassPurchase> result
                                                        ) {
                                                            if (result.isPresent()) {
                                                                recoverPurchaseRecord(
                                                                        player, result.orElseThrow());
                                                            } else {
                                                                releaseRecovery(player, throwable);
                                                            }
                                                        }

                                                        @Override
                                                        public void onError(Throwable lookupFailure) {
                                                            releaseRecovery(player, lookupFailure);
                                                        }
                                                    });
                                        }

                                        @Override
                                        public void onError(Throwable persistenceFailure) {
                                            releaseRecovery(player, persistenceFailure);
                                        }
                                    });
                            return;
                        }
                        releaseRecovery(player, throwable);
                    }
                });
    }

    private void markNotCharged(Player player, AdvertisingPassPurchase purchase) {
        repository().markNotCharged(purchase.nonce(), new Callback<Void>(plugin) {
            @Override
            public void onResult(Void ignored) {
                unlockPurchase(player.getUniqueId());
                player.sendMessage(Component.text(
                        "An interrupted Advertising Pass purchase was cleared; no token was charged.",
                        NamedTextColor.YELLOW));
            }

            @Override
            public void onError(Throwable throwable) {
                releaseRecovery(player, throwable);
            }
        });
    }

    private void restoreExactRefund(
            Player player,
            AdvertisingPassPurchase purchase,
            PurchaseEscrowEvidence<ItemStack> evidence
    ) {
        try {
            final List<ItemStack> current = storageSnapshot(player.getInventory());
            applyStorage(player.getInventory(), evidence.restoreRefund(current));
        } catch (RuntimeException exception) {
            leavePendingForStaff(player, purchase, exception);
            return;
        }
        markRefunded(player, purchase);
    }

    private void markRefunded(Player player, AdvertisingPassPurchase purchase) {
        repository().markRefunded(purchase.nonce(), new Callback<Void>(plugin) {
            @Override
            public void onResult(Void ignored) {
                unlockPurchase(player.getUniqueId());
                player.sendMessage(Component.text(
                        "Your exact AFK Shrine Tokens were restored from purchase escrow.",
                        NamedTextColor.GREEN));
            }

            @Override
            public void onError(Throwable throwable) {
                releaseRecovery(player, throwable);
            }
        });
    }

    private void leavePendingForStaff(
            Player player,
            AdvertisingPassPurchase purchase,
            Throwable cause
    ) {
        if (purchase.status() == AdvertisingPurchaseStatus.PREPARED) {
            repository().markRefundPending(
                    purchase.nonce(),
                    cause == null ? "Affected inventory slots diverged"
                            : cause.getMessage(),
                    new Callback<Void>(plugin) {
                        @Override
                        public void onResult(Void ignored) {
                            finishPendingRecovery(player, cause);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            releaseRecovery(player, throwable);
                        }
                    });
            return;
        }
        finishPendingRecovery(player, cause);
    }

    private void finishPendingRecovery(Player player, Throwable cause) {
        if (cause != null) {
            plugin.debug(cause);
        }
        unlockPurchase(player.getUniqueId());
        player.sendMessage(Component.text(
                "Your exact advertising-token escrow is still pending."
                        + " Staff must review it before another purchase.",
                NamedTextColor.RED));
    }

    private void finishTerminalRecovery(Player player, AdvertisingPassPurchase purchase) {
        unlockPurchase(player.getUniqueId());
        if (purchase.status() == AdvertisingPurchaseStatus.DELIVERED) {
            player.sendMessage(Component.text(
                    "Your Advertising Pass purchase was already delivered safely.",
                    NamedTextColor.GREEN));
        } else if (purchase.status() == AdvertisingPurchaseStatus.REFUNDED) {
            player.sendMessage(Component.text(
                    "Your interrupted Advertising Pass purchase was already refunded.",
                    NamedTextColor.YELLOW));
        }
    }

    private void releaseRecovery(Player player, Throwable throwable) {
        unlockPurchase(player.getUniqueId());
        plugin.debug(throwable);
        if (player.isOnline()) {
            player.sendMessage(Component.text(
                    "Advertising escrow recovery is still pending; do not retry the purchase yet.",
                    NamedTextColor.RED));
        }
    }

    private static List<ItemStack> storageSnapshot(PlayerInventory inventory) {
        final List<ItemStack> snapshot = new ArrayList<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            snapshot.add(stack == null ? null : stack.clone());
        }
        return snapshot;
    }

    private static void applyStorage(PlayerInventory inventory, List<ItemStack> stacks) {
        inventory.setStorageContents(stacks.toArray(ItemStack[]::new));
    }

    public void buildPresentation(
            UUID ownerId,
            Consumer<Optional<AdvertisementPresentation>> completion,
            Consumer<Throwable> failure
    ) {
        plugin.getStorefrontRepository().findProfile(
                ownerId,
                new Callback<Optional<StorefrontProfile>>(plugin) {
                    @Override
                    public void onResult(Optional<StorefrontProfile> result) {
                        if (result.isEmpty() || result.orElseThrow().suspended()) {
                            completion.accept(Optional.empty());
                            return;
                        }
                        buildPresentation(
                                result.orElseThrow(), completion, failure);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        failure.accept(throwable);
                    }
                });
    }

    private void buildPresentation(
            StorefrontProfile profile,
            Consumer<Optional<AdvertisementPresentation>> completion,
            Consumer<Throwable> failure
    ) {
        final UUID ownerId = profile.ownerId();
        plugin.getStorefrontRepository().findFeatured(
                ownerId,
                new Callback<List<Integer>>(plugin) {
                    @Override
                    public void onResult(List<Integer> featuredIds) {
                        final Map<Integer, RuntimeCatalogueEntry> eligible = new HashMap<>();
                        plugin.getPublicCatalogue().ownerEntries(ownerId).stream()
                                .filter(entry -> entry.customerBuyPrice() > 0.0D)
                                .forEach(entry -> eligible.put(entry.shopId(), entry));
                        final List<RuntimeCatalogueEntry> selected = featuredIds.stream()
                                .map(eligible::get)
                                .filter(java.util.Objects::nonNull)
                                .limit(3)
                                .toList();
                        if (selected.isEmpty()) {
                            completion.accept(Optional.empty());
                            return;
                        }
                        plugin.getPublicCatalogue().inspectAll(selected, inspected -> {
                            final RuntimeCatalogueListing primary = inspected.getFirst();
                            if (primary.stock().availability() != ListingAvailability.IN_STOCK) {
                                completion.accept(Optional.of(
                                        AdvertisementPresentation.transientlyUnavailable(
                                                ownerId,
                                                primary.stock().availability())));
                                return;
                            }
                            final List<RuntimeCatalogueListing> ready = inspected.stream()
                                    .filter(listing -> listing.stock().availability()
                                            == ListingAvailability.IN_STOCK)
                                    .limit(3)
                                    .toList();
                            completion.accept(Optional.of(AdvertisementPresentation.ready(
                                    ownerId, profile, ready)));
                        });
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        failure.accept(throwable);
                    }
                });
    }

    private void pollQueue() {
        final long generation = lifecycleGeneration.get();
        if (!Config.advertisingEnabled || !isReady() || !dispatching.compareAndSet(false, true)) {
            return;
        }
        if (!isActiveGeneration(generation)) {
            releaseDispatch(generation);
            return;
        }
        final Instant now = Instant.now();
        repository().globalNextBroadcastAt(new Callback<Instant>(plugin) {
            @Override
            public void onResult(Instant globalNext) {
                if (!isActiveGeneration(generation)) {
                    return;
                }
                if (globalNext.isAfter(now)) {
                    releaseDispatch(generation);
                    return;
                }
                repository().findNextEligibleRequest(
                        now,
                        new Callback<Optional<AdvertisementRequest>>(plugin) {
                            @Override
                            public void onResult(Optional<AdvertisementRequest> result) {
                                if (!isActiveGeneration(generation)) {
                                    return;
                                }
                                if (result.isEmpty()) {
                                    releaseDispatch(generation);
                                    return;
                                }
                                dispatch(result.orElseThrow(), now, generation);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                failDispatch(generation, throwable);
                            }
                        });
            }

            @Override
            public void onError(Throwable throwable) {
                failDispatch(generation, throwable);
            }
        });
    }

    private void dispatch(AdvertisementRequest request, Instant now, long generation) {
        if (!isActiveGeneration(generation)) {
            return;
        }
        repository().findPass(request.ownerId(), new Callback<Optional<AdvertisingPass>>(plugin) {
            @Override
            public void onResult(Optional<AdvertisingPass> result) {
                if (!isActiveGeneration(generation)) {
                    return;
                }
                if (result.isEmpty()
                        || !request.id().equals(result.orElseThrow().openRequestId())) {
                    releaseDispatch(generation);
                    return;
                }
                final AdvertisingPass pass = result.orElseThrow();
                if (now.isAfter(request.submittedAt().plus(
                        Duration.ofHours(Config.advertisingRequestTtlHours)))) {
                    closeInvalid(pass, request, now, generation);
                    return;
                }
                final Player owner = Bukkit.getPlayer(request.ownerId());
                if (owner == null) {
                    parkRequest(request, now, generation);
                    return;
                }
                if (!owner.hasPermission(Permissions.ADVERTISE)) {
                    closeInvalid(pass, request, now, generation);
                    return;
                }
                buildPresentation(request.ownerId(), presentation -> {
                    if (!isActiveGeneration(generation)) {
                        return;
                    }
                    if (presentation.isEmpty()) {
                        closeInvalid(pass, request, now, generation);
                        return;
                    }
                    final AdvertisementPresentation rendered = presentation.orElseThrow();
                    if (!rendered.ready()) {
                        parkRequest(request, now, generation);
                        return;
                    }
                    final AdvertisementTransition transition;
                    try {
                        transition = policy.broadcast(pass, request, now);
                    } catch (RuntimeException exception) {
                        failDispatch(generation, exception);
                        return;
                    }
                    if (!isActiveGeneration(generation)) {
                        return;
                    }
                    repository().commitBroadcast(
                            pass,
                            request,
                            transition,
                            now,
                            now.plus(Duration.ofMinutes(
                                    Config.advertisingGlobalCooldownMinutes)),
                            new Callback<Boolean>(plugin) {
                                @Override
                                public void onResult(Boolean committed) {
                                    if (!isActiveGeneration(generation)) {
                                        return;
                                    }
                                    try {
                                        if (Boolean.TRUE.equals(committed)) {
                                            // Durable terminal claim precedes best-effort external effects.
                                            // Never retry this request after this point: public ads are at-most-once.
                                            broadcast(rendered);
                                        }
                                    } finally {
                                        releaseDispatch(generation);
                                    }
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    failDispatch(generation, throwable);
                                }
                            });
                }, throwable -> failDispatch(generation, throwable));
            }

            @Override
            public void onError(Throwable throwable) {
                failDispatch(generation, throwable);
            }
        });
    }

    private void parkRequest(AdvertisementRequest request, Instant now, long generation) {
        if (!isActiveGeneration(generation)) {
            return;
        }
        repository().parkRequest(
                request,
                now.plus(Duration.ofMinutes(15)),
                new Callback<Void>(plugin) {
                    @Override
                    public void onResult(Void ignored) {
                        releaseDispatch(generation);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        failDispatch(generation, throwable);
                    }
                });
    }

    private void closeInvalid(
            AdvertisingPass pass,
            AdvertisementRequest request,
            Instant now,
            long generation
    ) {
        if (!isActiveGeneration(generation)) {
            return;
        }
        final AdvertisementTransition cancelled;
        try {
            cancelled = policy.cancel(pass, request, now);
        } catch (RuntimeException exception) {
            failDispatch(generation, exception);
            return;
        }
        if (!isActiveGeneration(generation)) {
            return;
        }
        repository().saveTransition(cancelled, new Callback<Void>(plugin) {
            @Override
            public void onResult(Void ignored) {
                releaseDispatch(generation);
            }

            @Override
            public void onError(Throwable throwable) {
                failDispatch(generation, throwable);
            }
        });
    }

    private void broadcast(AdvertisementPresentation presentation) {
        final String ownerName = Optional.ofNullable(
                Bukkit.getOfflinePlayer(presentation.ownerId()).getName())
                .orElse("A marketplace seller");
        final String customName = !presentation.profile().textHidden()
                ? presentation.profile().name() : null;
        final String storefrontName = customName == null || customName.isBlank()
                ? "Shops by " + ownerName : customName;
        final RuntimeCatalogueEntry primary = presentation.listings().getFirst().entry();
        final String fallback = primary.marketplaceLocation()
                ? "Fresh stock is waiting at /warp shops!"
                : "Fresh stock is waiting!";
        final String tagline = !presentation.profile().textHidden()
                ? firstNonBlank(
                        presentation.profile().tagline(),
                        presentation.profile().description(),
                        fallback)
                : fallback;
        final String primaryName = HologramTextFormatter.sanitizeItemName(
                plugin.getLanguageManager().getItemNameManager()
                        .getItemName(primary.productTemplate()),
                48);

        final Title title = Title.title(
                Component.text(storefrontName, NamedTextColor.GOLD),
                Component.text(
                        primary.bundleAmount() + "x " + primaryName + " • "
                                + plugin.getEconomy().format(primary.customerBuyPrice()),
                        NamedTextColor.AQUA));
        final Component profileLink = Component.text("[View storefront]", NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text("Browse this seller's shops")))
                .clickEvent(ClickEvent.runCommand(
                        "/" + Config.mainCommandName + " profile " + presentation.ownerId()));
        Component chat = Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text(storefrontName, NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(" — " + tagline + " ", NamedTextColor.WHITE))
                .append(profileLink);
        if (presentation.listings().size() > 1) {
            final String supportingOffers = presentation.listings().stream()
                    .skip(1)
                    .limit(2)
                    .map(listing -> listing.entry().bundleAmount() + "x "
                            + HologramTextFormatter.sanitizeItemName(
                                    plugin.getLanguageManager().getItemNameManager().getItemName(
                                            listing.entry().productTemplate()),
                                    40)
                            + " for " + plugin.getEconomy().format(
                                    listing.entry().customerBuyPrice()))
                    .collect(java.util.stream.Collectors.joining(", "));
            chat = chat.append(Component.text(
                    " • Also featured: " + supportingOffers,
                    NamedTextColor.AQUA));
        }
        if (primary.marketplaceLocation()) {
            final Component warpLink = Component.text("[/warp shops]", NamedTextColor.GREEN)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand("/warp shops"));
            chat = chat.append(Component.space()).append(warpLink);
        }
        final Sound sound = resolveSound();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.sendMessage(chat);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 0.65F, 1.0F);
            }
        }
    }

    private Sound resolveSound() {
        final NamespacedKey key = NamespacedKey.fromString(Config.advertisingSound);
        return key == null ? null : Registry.SOUND_EVENT.get(key);
    }

    private void reloadPolicy() {
        policy = new AdvertisingLifecyclePolicy(new AdvertisingPassTerms(
                Duration.ofDays(Config.advertisingPassDays),
                Config.advertisingBroadcastsPerPass,
                Duration.ofHours(Config.advertisingOwnerCooldownHours)));
    }

    private boolean isActiveGeneration(long generation) {
        return started && lifecycleGeneration.get() == generation && plugin.isEnabled();
    }

    private void releaseDispatch(long generation) {
        if (lifecycleGeneration.get() == generation) {
            dispatching.set(false);
        }
    }

    private void failDispatch(long generation, Throwable throwable) {
        if (lifecycleGeneration.get() != generation) {
            return;
        }
        releaseDispatch(generation);
        plugin.getLogger().warning("A queued storefront advertisement could not be processed");
        if (throwable != null) {
            plugin.debug(throwable);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public record AdvertisementPresentation(
            UUID ownerId,
            StorefrontProfile profile,
            List<RuntimeCatalogueListing> listings,
            ListingAvailability blockingAvailability,
            boolean ready
    ) {
        public AdvertisementPresentation {
            listings = List.copyOf(listings);
        }

        static AdvertisementPresentation ready(
                UUID ownerId,
                StorefrontProfile profile,
                List<RuntimeCatalogueListing> listings
        ) {
            return new AdvertisementPresentation(
                    ownerId, profile, listings, null, true);
        }

        static AdvertisementPresentation transientlyUnavailable(
                UUID ownerId,
                ListingAvailability availability
        ) {
            return new AdvertisementPresentation(
                    ownerId,
                    StorefrontProfile.empty(ownerId, 0L),
                    List.of(),
                    availability,
                    false);
        }
    }
}
