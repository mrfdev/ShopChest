package de.epiceric.shopchest.diagnostics;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopContainer;
import de.epiceric.shopchest.sql.ShopAuditRecord;
import de.epiceric.shopchest.utils.ChunkCoordinates;
import de.epiceric.shopchest.utils.ShopUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Performs a bounded, read-only inspection of persisted shop records.
 *
 * <p>The database snapshot is supplied by the caller. Bukkit-backed product,
 * world, block, and runtime-registry checks run on the primary server thread
 * in small batches. No method in this service loads chunks or mutates data.</p>
 */
public final class ShopAuditService {

    private static final int RECORDS_PER_TICK = 25;
    private static final long MAX_NANOS_PER_TICK = 4_000_000L;
    private static final int MAX_ENCODED_PRODUCT_LENGTH = 8 * 1024 * 1024;
    private static final int MAX_WORLD_NAME_LENGTH = 255;

    private final ShopChest plugin;
    private final ShopUtils shopUtils;

    public ShopAuditService(ShopChest plugin) {
        this.plugin = plugin;
        this.shopUtils = plugin.getShopUtils();
    }

    public void inspect(
            List<ShopAuditRecord> records,
            UUID ownerFilter,
            Consumer<ShopAuditReport> completion,
            Consumer<Throwable> failure
    ) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    final List<ShopAuditRecord> snapshot = records.stream()
                            .sorted(Comparator.comparingLong(ShopAuditRecord::rowNumber))
                            .toList();
                    final Set<Long> storedLocationConflicts =
                            storedLocationConflicts(snapshot);
                    Bukkit.getScheduler().runTask(plugin, () -> inspectPrepared(
                            snapshot,
                            storedLocationConflicts,
                            ownerFilter,
                            completion,
                            failure));
                } catch (RuntimeException exception) {
                    Bukkit.getScheduler().runTask(plugin, () -> failure.accept(exception));
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void inspectPrepared(
            List<ShopAuditRecord> snapshot,
            Set<Long> storedLocationConflicts,
            UUID ownerFilter,
            Consumer<ShopAuditReport> completion,
            Consumer<Throwable> failure
    ) {
        final List<MutableFinding> inspected = new ArrayList<>(snapshot.size());
        final Map<PhysicalContainerKey, MutableFinding> firstByContainer = new HashMap<>();
        final List<ShopAuditFinding> selected = new ArrayList<>();
        final List<ShopAuditFinding> selectedReview = new ArrayList<>();
        final ShopAuditSummary.Accumulator summary = new ShopAuditSummary.Accumulator();

        new BukkitRunnable() {
            private int index;
            private boolean finalizing;

            @Override
            public void run() {
                try {
                    int processed = 0;
                    final long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
                    if (!finalizing) {
                        while (index < snapshot.size()
                                && processed < RECORDS_PER_TICK
                                && (processed == 0 || System.nanoTime() < deadline)) {
                            processed++;
                            final ShopAuditRecord record = snapshot.get(index++);
                            final MutableFinding finding = inspectRecord(
                                    record,
                                    storedLocationConflicts.contains(record.rowNumber()));
                            inspected.add(finding);
                            registerPhysicalContainer(finding, firstByContainer);
                        }
                        if (index < snapshot.size()) {
                            return;
                        }
                        finalizing = true;
                        index = 0;
                    }

                    while (index < inspected.size()
                            && processed < RECORDS_PER_TICK
                            && (processed == 0 || System.nanoTime() < deadline)) {
                        processed++;
                        final MutableFinding mutableFinding = inspected.get(index++);
                        if (ownerFilter == null
                                || ownerMatches(mutableFinding.record.vendor(), ownerFilter)) {
                            final ShopAuditFinding finding = mutableFinding.toFinding();
                            selected.add(finding);
                            if (finding.needsReview()) {
                                selectedReview.add(finding);
                            }
                            summary.accept(finding);
                        }
                    }
                    if (index < inspected.size()) {
                        return;
                    }

                    cancel();
                    completion.accept(new ShopAuditReport(
                            selected,
                            selectedReview,
                            summary.toSummary()));
                } catch (RuntimeException exception) {
                    cancel();
                    failure.accept(exception);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private MutableFinding inspectRecord(
            ShopAuditRecord record,
            boolean storedLocationConflict
    ) {
        final EnumSet<ShopAuditIssue> issues = intrinsicIssues(record);
        if (storedLocationConflict) {
            issues.add(ShopAuditIssue.CONFLICTING_RECORD);
        }

        try {
            if (record.world() == null
                    || record.world().isBlank()
                    || record.world().length() > MAX_WORLD_NAME_LENGTH) {
                issues.add(ShopAuditIssue.INVALID_RECORD);
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.UNAVAILABLE,
                        issues,
                        null);
            }

            final Integer x = record.parsedX();
            final Integer y = record.parsedY();
            final Integer z = record.parsedZ();
            if (x == null || y == null || z == null) {
                issues.add(ShopAuditIssue.INVALID_LOCATION);
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.UNAVAILABLE,
                        issues,
                        null);
            }

            final World world = plugin.getServer().getWorld(record.world());
            if (world == null) {
                issues.add(ShopAuditIssue.WORLD_UNAVAILABLE);
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.UNAVAILABLE,
                        issues,
                        null);
            }
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                issues.add(ShopAuditIssue.INVALID_LOCATION);
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.UNAVAILABLE,
                        issues,
                        null);
            }
            if (!world.isChunkLoaded(
                    ChunkCoordinates.fromBlock(x),
                    ChunkCoordinates.fromBlock(z))) {
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.UNCHECKED,
                        issues,
                        null);
            }

            final Block block = world.getBlockAt(x, y, z);
            if (block.getType().isAir()) {
                issues.add(ShopAuditIssue.MISSING_CONTAINER);
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.CHECKED,
                        issues,
                        null);
            }
            if (!ShopContainer.isSupported(block.getType())) {
                issues.add(ShopAuditIssue.UNSUPPORTED_CONTAINER);
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.CHECKED,
                        issues,
                        null);
            }
            if (!hasLoadedContainerPartner(world, block)) {
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.UNCHECKED,
                        issues,
                        null);
            }

            final ShopContainer container = ShopContainer.resolve(plugin, block);
            if (container == null || !hasCompleteContainer(block, container)) {
                issues.add(ShopAuditIssue.INCOMPLETE_CONTAINER);
                return new MutableFinding(
                        record,
                        ShopAuditFinding.Inspection.CHECKED,
                        issues,
                        null);
            }

            final PhysicalContainerKey physicalKey = physicalContainerKey(world, container);
            if (!hasDisplaySpace(world, container)) {
                issues.add(ShopAuditIssue.BLOCKED_DISPLAY);
            }

            final Shop loadedShop = shopUtils.getShop(new Location(world, x, y, z));
            final Integer recordId = record.parsedId();
            if (loadedShop == null) {
                issues.add(ShopAuditIssue.INACTIVE_RECORD);
            } else if (recordId == null || loadedShop.getID() != recordId) {
                issues.add(ShopAuditIssue.SHADOWED_RECORD);
            }

            return new MutableFinding(
                    record,
                    ShopAuditFinding.Inspection.CHECKED,
                    issues,
                    physicalKey);
        } catch (RuntimeException exception) {
            issues.add(ShopAuditIssue.INVALID_RECORD);
            plugin.debug("Failed to inspect shop record #" + record.rawId());
            plugin.debug(exception);
            return new MutableFinding(
                    record,
                    ShopAuditFinding.Inspection.UNAVAILABLE,
                    issues,
                    null);
        }
    }

    private EnumSet<ShopAuditIssue> intrinsicIssues(ShopAuditRecord record) {
        final EnumSet<ShopAuditIssue> issues = EnumSet.noneOf(ShopAuditIssue.class);
        final Integer id = record.parsedId();
        if (id == null || id <= 0) {
            issues.add(ShopAuditIssue.INVALID_RECORD);
        }
        if (!isCanonicalUuid(record.vendor())) {
            issues.add(ShopAuditIssue.INVALID_OWNER);
        }
        if (!"NORMAL".equals(record.shopType()) && !"ADMIN".equals(record.shopType())) {
            issues.add(ShopAuditIssue.INVALID_SHOP_TYPE);
        }
        if (!hasValidTerms(record)) {
            issues.add(ShopAuditIssue.INVALID_TERMS);
        }
        if (!hasValidProduct(record.product())) {
            issues.add(ShopAuditIssue.INVALID_PRODUCT);
        }
        return issues;
    }

    private boolean hasValidTerms(ShopAuditRecord record) {
        final Integer amount = record.parsedAmount();
        final Double buyPrice = record.parsedBuyPrice();
        final Double sellPrice = record.parsedSellPrice();
        return amount != null
                && buyPrice != null
                && sellPrice != null
                && amount > 0
                && Double.isFinite(buyPrice)
                && Double.isFinite(sellPrice)
                && buyPrice >= 0
                && sellPrice >= 0
                && (buyPrice > 0 || sellPrice > 0);
    }

    private boolean hasValidProduct(String encodedProduct) {
        if (encodedProduct == null
                || encodedProduct.isBlank()
                || encodedProduct.length() > MAX_ENCODED_PRODUCT_LENGTH) {
            return false;
        }
        try {
            final byte[] decoded = Base64.getDecoder().decode(encodedProduct);
            final YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(new String(decoded, StandardCharsets.UTF_8));
            final ItemStack itemStack = configuration.getItemStack("i", null);
            return itemStack != null
                    && !itemStack.getType().isAir()
                    && itemStack.getType().isItem();
        } catch (InvalidConfigurationException | RuntimeException ignored) {
            return false;
        }
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

        return world.isChunkLoaded(
                ChunkCoordinates.fromBlock(block.getX() + partnerFace.getModX()),
                ChunkCoordinates.fromBlock(block.getZ() + partnerFace.getModZ()));
    }

    private boolean hasCompleteContainer(Block block, ShopContainer container) {
        return !(block.getBlockData() instanceof Chest chest)
                || chest.getType() == Chest.Type.SINGLE
                || container.getLocations().size() == 2;
    }

    private boolean hasDisplaySpace(World world, ShopContainer container) {
        return container.getLocations().stream()
                .allMatch(location -> location.getBlockY() + 1 < world.getMaxHeight())
                && container.hasDisplaySpace();
    }

    private PhysicalContainerKey physicalContainerKey(
            World world,
            ShopContainer container
    ) {
        final List<BlockPosition> positions = container.getLocations().stream()
                .map(location -> new BlockPosition(
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ()))
                .sorted(Comparator
                        .comparingInt(BlockPosition::x)
                        .thenComparingInt(BlockPosition::y)
                        .thenComparingInt(BlockPosition::z))
                .toList();
        return new PhysicalContainerKey(world.getUID(), positions);
    }

    private Set<Long> storedLocationConflicts(List<ShopAuditRecord> records) {
        final Map<StoredLocationKey, List<Long>> rowsByLocation = new HashMap<>();
        for (ShopAuditRecord record : records) {
            final Integer x = record.parsedX();
            final Integer y = record.parsedY();
            final Integer z = record.parsedZ();
            if (record.world() == null || record.world().isBlank()
                    || x == null || y == null || z == null) {
                continue;
            }
            final StoredLocationKey key = new StoredLocationKey(
                    record.world(),
                    x,
                    y,
                    z);
            rowsByLocation.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(record.rowNumber());
        }

        final Set<Long> conflicts = new HashSet<>();
        for (List<Long> rows : rowsByLocation.values()) {
            if (rows.size() > 1) {
                conflicts.addAll(rows);
            }
        }
        return conflicts;
    }

    private void registerPhysicalContainer(
            MutableFinding finding,
            Map<PhysicalContainerKey, MutableFinding> firstByContainer
    ) {
        if (finding.physicalKey == null) {
            return;
        }
        final MutableFinding first = firstByContainer.putIfAbsent(
                finding.physicalKey,
                finding);
        if (first != null) {
            first.issues.add(ShopAuditIssue.CONFLICTING_RECORD);
            finding.issues.add(ShopAuditIssue.CONFLICTING_RECORD);
        }
    }

    private boolean ownerMatches(String storedOwner, UUID ownerFilter) {
        return storedOwner != null
                && storedOwner.equalsIgnoreCase(ownerFilter.toString());
    }

    private boolean isCanonicalUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private record StoredLocationKey(String world, int x, int y, int z) {
    }

    private record BlockPosition(int x, int y, int z) {
    }

    private record PhysicalContainerKey(UUID worldId, List<BlockPosition> positions) {

        private PhysicalContainerKey {
            positions = List.copyOf(positions);
        }
    }

    private static final class MutableFinding {
        private final ShopAuditRecord record;
        private final ShopAuditFinding.Inspection inspection;
        private final EnumSet<ShopAuditIssue> issues;
        private final PhysicalContainerKey physicalKey;

        private MutableFinding(
                ShopAuditRecord record,
                ShopAuditFinding.Inspection inspection,
                EnumSet<ShopAuditIssue> issues,
                PhysicalContainerKey physicalKey
        ) {
            this.record = record;
            this.inspection = inspection;
            this.issues = issues;
            this.physicalKey = physicalKey;
        }

        private ShopAuditFinding toFinding() {
            return new ShopAuditFinding(record, inspection, issues);
        }
    }
}
