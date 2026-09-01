package de.epiceric.shopchest.catalog;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopContainer;
import de.epiceric.shopchest.sql.ShopAuditRecord;
import de.epiceric.shopchest.storefront.MarketplaceScopeResolver;
import de.epiceric.shopchest.storefront.StorefrontProfile;
import de.epiceric.shopchest.utils.Callback;
import de.epiceric.shopchest.utils.ChunkCoordinates;
import de.epiceric.shopchest.utils.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Maintains a bounded, immutable public catalogue projection and inspects live
 * stock only on the server thread. No discovery operation force-loads chunks.
 */
public final class RuntimePublicCatalogueService {

    private static final int RECORDS_PER_TICK = 25;
    private static final int MAX_RECORDS = 20_000;
    private static final int MAX_ENCODED_PRODUCT_LENGTH = 8 * 1024 * 1024;
    private static final long MAX_NANOS_PER_TICK = 4_000_000L;
    private final ShopChest plugin;
    private final MarketplaceScopeResolver scopeResolver;
    private final CatalogueRefreshPolicy refreshPolicy = CatalogueRefreshPolicy.standard();
    private final AtomicReference<List<RuntimeCatalogueEntry>> entries =
            new AtomicReference<>(List.of());
    private final AtomicReference<Map<Integer, RuntimeCatalogueEntry>> entriesById =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Map<Material, List<RuntimeCatalogueEntry>>> customerBuyIndex =
            new AtomicReference<>(Map.of());
    private final AtomicReference<MaterialSuggestionIndex> materialSuggestions =
            new AtomicReference<>(MaterialSuggestionIndex.empty());
    private final AtomicReference<Map<UUID, StorefrontProfile>> profiles =
            new AtomicReference<>(Map.of());
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean refreshRequested = new AtomicBoolean();
    private final AtomicLong lifecycleGeneration = new AtomicLong();

    private volatile boolean started;
    private volatile boolean ready;
    private volatile long refreshedAt;
    private volatile Integer announcedEligibleListings;
    private BukkitTask periodicTask;
    private BukkitTask requestedTask;

    public RuntimePublicCatalogueService(ShopChest plugin) {
        this.plugin = plugin;
        this.scopeResolver = new MarketplaceScopeResolver(plugin);
    }

    public void start() {
        stop();
        started = true;
        final long generation = lifecycleGeneration.incrementAndGet();
        refresh(generation);
        periodicTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> refresh(generation),
                periodicRefreshTicks(),
                periodicRefreshTicks());
    }

    public void stop() {
        started = false;
        lifecycleGeneration.incrementAndGet();
        ready = false;
        announcedEligibleListings = null;
        entries.set(List.of());
        entriesById.set(Map.of());
        customerBuyIndex.set(Map.of());
        materialSuggestions.set(MaterialSuggestionIndex.empty());
        profiles.set(Map.of());
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        if (requestedTask != null) {
            requestedTask.cancel();
            requestedTask = null;
        }
        refreshing.set(false);
        refreshRequested.set(false);
    }

    public void requestRefresh() {
        if (!started) {
            return;
        }
        final long generation = lifecycleGeneration.get();
        if (requestedTask != null) {
            requestedTask.cancel();
        }
        requestedTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            requestedTask = null;
            refresh(generation);
        }, 40L);
    }

    public void refresh() {
        refresh(lifecycleGeneration.get());
    }

    private void refresh(long generation) {
        if (!started || lifecycleGeneration.get() != generation) {
            return;
        }
        if (!plugin.getShopDatabase().isInitialized()) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            refreshRequested.set(true);
            return;
        }
        refreshRequested.set(false);
        plugin.getShopDatabase().getShopAuditRecords(new Callback<List<ShopAuditRecord>>(plugin) {
            @Override
            public void onResult(List<ShopAuditRecord> result) {
                if (!isCurrent(generation)) {
                    return;
                }
                plugin.getStorefrontRepository().findProfiles(
                        new Callback<Map<UUID, StorefrontProfile>>(plugin) {
                            @Override
                            public void onResult(Map<UUID, StorefrontProfile> resultProfiles) {
                                if (isCurrent(generation)) {
                                    buildSnapshot(result, resultProfiles, generation);
                                }
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                failRefresh(throwable, generation);
                            }
                        });
            }

            @Override
            public void onError(Throwable throwable) {
                failRefresh(throwable, generation);
            }
        });
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isRefreshing() {
        return refreshing.get();
    }

    public long refreshedAt() {
        return refreshedAt;
    }

    public List<RuntimeCatalogueEntry> entries() {
        return entries.get();
    }

    public java.util.Optional<StorefrontProfile> profile(UUID ownerId) {
        return java.util.Optional.ofNullable(profiles.get().get(ownerId));
    }

    public void applyProfileUpdate(StorefrontProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile");
        if (!plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(
                    plugin, () -> applyProfileUpdate(profile));
            return;
        }
        final Map<UUID, StorefrontProfile> updatedProfiles =
                new java.util.HashMap<>(profiles.get());
        updatedProfiles.put(profile.ownerId(), profile);
        profiles.set(Map.copyOf(updatedProfiles));
        if (profile.suspended()) {
            publishEntries(entries.get().stream()
                    .filter(entry -> !entry.ownerId().equals(profile.ownerId()))
                    .toList());
        }
    }

    public List<RuntimeCatalogueEntry> customerBuyEntries(Material material) {
        if (material == null) {
            return List.of();
        }
        return customerBuyIndex.get().getOrDefault(material, List.of());
    }

    public List<ResolvedMaterial> suggestMaterials(String input, int limit) {
        return materialSuggestions.get().suggest(input, limit);
    }

    public List<RuntimeCatalogueEntry> ownerEntries(UUID ownerId) {
        return entries.get().stream()
                .filter(entry -> entry.ownerId().equals(ownerId))
                .filter(entry -> entry.bundleAmount() > 0)
                .filter(entry -> (entry.customerBuyPrice() > 0.0D
                        && Double.isFinite(entry.customerBuyPrice()))
                        || (entry.customerSellPrice() > 0.0D
                        && Double.isFinite(entry.customerSellPrice())))
                .toList();
    }

    public RuntimeCatalogueListing inspect(RuntimeCatalogueEntry entry) {
        if (!plugin.getServer().isPrimaryThread()) {
            throw new IllegalStateException("Catalogue stock must be inspected on the server thread");
        }
        if (!entriesById.get().containsKey(entry.shopId())) {
            return unavailable(entry);
        }
        final Location location = entry.location();
        final World world = location.getWorld();
        if (world == null) {
            return unavailable(entry);
        }
        if (!world.isChunkLoaded(
                ChunkCoordinates.fromBlock(location.getBlockX()),
                ChunkCoordinates.fromBlock(location.getBlockZ()))) {
            return unchecked(entry);
        }

        final Block block = world.getBlockAt(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (!hasLoadedContainerPartner(world, block)) {
            return unchecked(entry);
        }
        final ShopContainer container = ShopContainer.resolve(plugin, block);
        if (container == null || !hasCompleteContainer(block, container)) {
            return unavailable(entry);
        }
        final Shop activeShop = plugin.getShopUtils().getShop(location);
        if (!matchesAuthoritativeEntry(activeShop, entry)) {
            return unavailable(entry);
        }

        final List<ItemStack> contents = java.util.Arrays.asList(
                container.getInventory().getStorageContents());
        return new RuntimeCatalogueListing(
                entry,
                ListingAvailabilityCalculator.inspect(
                        entry.productTemplate(),
                        entry.bundleAmount(),
                        contents,
                        Utils::isItemSimilar),
                ListingCapacityCalculator.inspect(
                        entry.productTemplate(),
                        entry.bundleAmount(),
                        contents,
                        Utils::isItemSimilar));
    }

    public void inspectAll(
            List<RuntimeCatalogueEntry> source,
            Consumer<List<RuntimeCatalogueListing>> completion
    ) {
        if (!plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(
                    plugin, () -> inspectAll(source, completion));
            return;
        }
        final List<RuntimeCatalogueListing> result = new ArrayList<>(source.size());
        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                int processed = 0;
                final long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
                while (index < source.size()
                        && processed < RECORDS_PER_TICK
                        && (processed == 0 || System.nanoTime() < deadline)) {
                    result.add(inspect(source.get(index++)));
                    processed++;
                }
                if (index >= source.size()) {
                    cancel();
                    completion.accept(List.copyOf(result));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void buildSnapshot(
            List<ShopAuditRecord> records,
            Map<UUID, StorefrontProfile> resultProfiles,
            long generation
    ) {
        final Set<UUID> suspendedOwners = resultProfiles.values().stream()
                .filter(StorefrontProfile::suspended)
                .map(StorefrontProfile::ownerId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        final List<ShopAuditRecord> bounded = records.stream()
                .sorted(Comparator.comparingLong(ShopAuditRecord::rowNumber))
                .limit(MAX_RECORDS)
                .toList();
        final List<RuntimeCatalogueEntry> built = new ArrayList<>(bounded.size());
        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (!isCurrent(generation)) {
                    cancel();
                    return;
                }
                try {
                    int processed = 0;
                    final long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
                    while (index < bounded.size()
                            && processed < RECORDS_PER_TICK
                            && (processed == 0 || System.nanoTime() < deadline)) {
                        decodeEntry(bounded.get(index++), suspendedOwners)
                                .ifPresent(built::add);
                        processed++;
                    }
                    if (index >= bounded.size()) {
                        cancel();
                        publishEntries(built);
                        profiles.set(Map.copyOf(resultProfiles));
                        refreshedAt = System.currentTimeMillis();
                        ready = true;
                        refreshing.set(false);
                        scheduleRequestedRefresh();
                        announceRefresh(built.size());
                    }
                } catch (RuntimeException exception) {
                    cancel();
                    failRefresh(exception, generation);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private java.util.Optional<RuntimeCatalogueEntry> decodeEntry(
            ShopAuditRecord record,
            Set<UUID> suspendedOwners
    ) {
        try {
            final Integer id = record.parsedId();
            final Integer amount = record.parsedAmount();
            final Integer x = record.parsedX();
            final Integer y = record.parsedY();
            final Integer z = record.parsedZ();
            final Double buyPrice = record.parsedBuyPrice();
            final Double sellPrice = record.parsedSellPrice();
            if (id == null || id <= 0 || amount == null || amount <= 0
                    || x == null || y == null || z == null
                    || buyPrice == null || sellPrice == null
                    || !Double.isFinite(buyPrice) || !Double.isFinite(sellPrice)
                    || buyPrice < 0.0D || sellPrice < 0.0D
                    || !"NORMAL".equals(record.shopType())
                    || record.product() == null
                    || record.product().length() > MAX_ENCODED_PRODUCT_LENGTH) {
                return java.util.Optional.empty();
            }
            final UUID ownerId = UUID.fromString(record.vendor());
            if (suspendedOwners.contains(ownerId)) {
                return java.util.Optional.empty();
            }
            final World world = plugin.getServer().getWorld(record.world());
            if (world == null || y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return java.util.Optional.empty();
            }
            final Location location = new Location(world, x, y, z);
            if (!scopeResolver.includes(location)) {
                return java.util.Optional.empty();
            }
            final ItemStack product = Utils.decode(record.product());
            if (product == null || product.getType().isAir() || !product.getType().isItem()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new RuntimeCatalogueEntry(
                    id,
                    ownerId,
                    product,
                    amount,
                    buyPrice,
                    sellPrice,
                    location,
                    scopeResolver.isMarketplace(location)));
        } catch (RuntimeException exception) {
            plugin.debug("Skipping invalid public shop record " + record.rawId());
            plugin.debug(exception);
            return java.util.Optional.empty();
        }
    }

    private void failRefresh(Throwable throwable, long generation) {
        if (!isCurrent(generation)) {
            return;
        }
        refreshing.set(false);
        scheduleRequestedRefresh();
        plugin.getLogger().warning("Public shop catalogue refresh failed; keeping the previous snapshot");
        if (throwable != null) {
            plugin.debug(throwable);
        }
    }

    private void publishEntries(List<RuntimeCatalogueEntry> source) {
        final List<RuntimeCatalogueEntry> immutableEntries = List.copyOf(source);
        final Map<Material, List<RuntimeCatalogueEntry>> mutableIndex =
                new java.util.EnumMap<>(Material.class);
        for (RuntimeCatalogueEntry entry : immutableEntries) {
            if (PublicCatalogueEligibility.isEligible(entry.candidate())) {
                mutableIndex.computeIfAbsent(
                        entry.productTemplate().getType(), ignored -> new ArrayList<>())
                        .add(entry);
            }
        }
        final Map<Material, List<RuntimeCatalogueEntry>> immutableIndex =
                new java.util.EnumMap<>(Material.class);
        final Map<Integer, RuntimeCatalogueEntry> immutableById = new java.util.HashMap<>();
        mutableIndex.forEach((material, indexedEntries) ->
                immutableIndex.put(material, List.copyOf(indexedEntries)));
        immutableEntries.forEach(entry -> immutableById.put(entry.shopId(), entry));

        entries.set(immutableEntries);
        entriesById.set(Map.copyOf(immutableById));
        customerBuyIndex.set(Map.copyOf(immutableIndex));
        materialSuggestions.set(MaterialSuggestionIndex.fromMaterials(
                immutableIndex.keySet()));
    }

    private boolean isCurrent(long generation) {
        return started && lifecycleGeneration.get() == generation && plugin.isEnabled();
    }

    private void scheduleRequestedRefresh() {
        if (refreshRequested.getAndSet(false) && started) {
            requestRefresh();
        }
    }

    private long periodicRefreshTicks() {
        return refreshPolicy.periodicRefreshInterval().toSeconds() * 20L;
    }

    private void announceRefresh(int eligibleListings) {
        final Integer previous = announcedEligibleListings;
        announcedEligibleListings = eligibleListings;
        if (!refreshPolicy.shouldAnnounce(previous, eligibleListings)) {
            return;
        }
        if (previous == null) {
            plugin.getLogger().info("Public shop catalogue is ready with "
                    + eligibleListings + " eligible scoped listings");
            return;
        }
        plugin.getLogger().info("Public shop catalogue refreshed with "
                + eligibleListings + " eligible scoped listings (was " + previous + ")");
    }

    private static boolean hasLoadedContainerPartner(World world, Block block) {
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
        return world.isChunkLoaded(
                ChunkCoordinates.fromBlock(block.getX() + partnerFace.getModX()),
                ChunkCoordinates.fromBlock(block.getZ() + partnerFace.getModZ()));
    }

    private static boolean hasCompleteContainer(Block block, ShopContainer container) {
        return !(block.getBlockData() instanceof Chest chest)
                || chest.getType() == Chest.Type.SINGLE
                || container.getLocations().size() == 2;
    }

    private static RuntimeCatalogueListing unchecked(RuntimeCatalogueEntry entry) {
        return new RuntimeCatalogueListing(
                entry,
                ListingStock.unchecked(),
                ListingCapacity.unchecked());
    }

    private static RuntimeCatalogueListing unavailable(RuntimeCatalogueEntry entry) {
        return new RuntimeCatalogueListing(
                entry,
                ListingStock.unavailable(),
                ListingCapacity.unavailable());
    }

    private static boolean matchesAuthoritativeEntry(
            Shop activeShop,
            RuntimeCatalogueEntry entry
    ) {
        if (activeShop == null
                || activeShop.getID() != entry.shopId()
                || activeShop.getShopType() != Shop.ShopType.NORMAL
                || activeShop.getVendor() == null
                || !activeShop.getVendor().getUniqueId().equals(entry.ownerId())
                || activeShop.getProduct() == null
                || activeShop.getProduct().getAmount() != entry.bundleAmount()
                || Double.compare(activeShop.getBuyPrice(), entry.customerBuyPrice()) != 0
                || Double.compare(activeShop.getSellPrice(), entry.customerSellPrice()) != 0) {
            return false;
        }
        return Utils.isItemSimilar(
                activeShop.getProduct().getItemStack().clone(),
                entry.productTemplate());
    }

}
