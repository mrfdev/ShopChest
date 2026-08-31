package de.epiceric.shopchest.catalog.export;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.catalog.PublicCatalogueEligibility;
import de.epiceric.shopchest.catalog.RuntimeCatalogueEntry;
import de.epiceric.shopchest.catalog.RuntimeCatalogueListing;
import de.epiceric.shopchest.catalog.RuntimePublicCatalogueService;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.storefront.StorefrontProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Builds a reviewable website snapshot from the same marketplace projection
 * used by in-game discovery. Inventory inspection is bounded and never loads a
 * chunk; JSON and CSV filesystem work runs away from the server thread.
 */
public final class MarketplaceSnapshotExportService {

    private static final int ROWS_PER_TICK = 50;
    private static final long MAX_NANOS_PER_TICK = 4_000_000L;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Amsterdam");
    private static final DateTimeFormatter CAPTURE_FORMAT = DateTimeFormatter
            .ofPattern("d MMMM uuuu 'at' HH:mm z", Locale.ENGLISH)
            .withZone(DISPLAY_ZONE);

    private final ShopChest plugin;
    private final AtomicBoolean exporting = new AtomicBoolean();

    public MarketplaceSnapshotExportService(ShopChest plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public StartResult export(
            Consumer<ExportResult> completion,
            Consumer<Throwable> failure
    ) {
        Objects.requireNonNull(completion, "completion");
        Objects.requireNonNull(failure, "failure");
        if (!plugin.getServer().isPrimaryThread()) {
            throw new IllegalStateException("Marketplace exports must start on the server thread");
        }

        final RuntimePublicCatalogueService catalogue = plugin.getPublicCatalogue();
        if (catalogue == null || !catalogue.isReady()) {
            return StartResult.CATALOGUE_NOT_READY;
        }
        if (!exporting.compareAndSet(false, true)) {
            return StartResult.ALREADY_RUNNING;
        }

        try {
            final List<RuntimeCatalogueEntry> candidates = catalogue.entries().stream()
                    .filter(RuntimeCatalogueEntry::marketplaceLocation)
                    .filter(entry -> PublicCatalogueEligibility.isEligible(entry.candidate()))
                    .toList();
            catalogue.inspectAll(candidates, inspected -> assemble(
                    catalogue,
                    candidates.size(),
                    inspected,
                    completion,
                    failure));
            return StartResult.STARTED;
        } catch (RuntimeException exception) {
            exporting.set(false);
            throw exception;
        }
    }

    public boolean isExporting() {
        return exporting.get();
    }

    private void assemble(
            RuntimePublicCatalogueService catalogue,
            int candidateCount,
            List<RuntimeCatalogueListing> inspected,
            Consumer<ExportResult> completion,
            Consumer<Throwable> failure
    ) {
        final List<MarketplaceListing> publicListings = new ArrayList<>(inspected.size());
        final Map<UUID, String> ownerNames = new HashMap<>();
        new BukkitRunnable() {
            private int index;
            private int unavailable;
            private int rejected;

            @Override
            public void run() {
                try {
                    int processed = 0;
                    final long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
                    while (index < inspected.size()
                            && processed < ROWS_PER_TICK
                            && (processed == 0 || System.nanoTime() < deadline)) {
                        final RuntimeCatalogueListing listing = inspected.get(index++);
                        processed++;
                        if (listing.stock().availability()
                                == de.epiceric.shopchest.catalog.ListingAvailability.UNAVAILABLE) {
                            unavailable++;
                            continue;
                        }
                        if (appendPublicListing(catalogue, listing, ownerNames, publicListings)) {
                            continue;
                        }
                        rejected++;
                    }

                    if (index >= inspected.size()) {
                        cancel();
                        writeSnapshot(
                                candidateCount,
                                unavailable,
                                rejected,
                                publicListings,
                                completion,
                                failure);
                    }
                } catch (RuntimeException exception) {
                    cancel();
                    failOnPrimaryThread(failure, exception);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean appendPublicListing(
            RuntimePublicCatalogueService catalogue,
            RuntimeCatalogueListing listing,
            Map<UUID, String> ownerNames,
            List<MarketplaceListing> destination
    ) {
        try {
            final RuntimeCatalogueEntry entry = listing.entry();
            final StorefrontProfile profile = catalogue.profile(entry.ownerId()).orElse(null);
            if (profile != null && profile.suspended()) {
                return false;
            }

            final String ownerName = ownerNames.computeIfAbsent(entry.ownerId(), ownerId -> {
                final OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
                return owner.getName() == null ? "" : owner.getName();
            });
            final ItemStack product = entry.productTemplate();
            final String itemName = plugin.getLanguageManager()
                    .getItemNameManager()
                    .getItemName(product);
            final boolean profileTextVisible = profile != null && !profile.textHidden();

            return MarketplaceListingFactory.create(
                            ownerName,
                            profileTextVisible ? profile.name() : null,
                            profileTextVisible ? profile.directions() : null,
                            product.getType().name(),
                            itemName,
                            entry.bundleAmount(),
                            entry.customerBuyPrice(),
                            listing.stock().availability(),
                            null)
                    .map(publicListing -> {
                        destination.add(publicListing);
                        return true;
                    })
                    .orElse(false);
        } catch (RuntimeException exception) {
            plugin.debug(exception);
            return false;
        }
    }

    private void writeSnapshot(
            int candidateCount,
            int unavailable,
            int rejected,
            List<MarketplaceListing> publicListings,
            Consumer<ExportResult> completion,
            Consumer<Throwable> failure
    ) {
        publicListings.sort(Comparator
                .comparing(MarketplaceListing::material)
                .thenComparing(MarketplaceListing::ownerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MarketplaceListing::customerBuyPrice)
                .thenComparingInt(MarketplaceListing::bundleAmount)
                .thenComparing(MarketplaceListing::itemName));

        final Instant capturedAt = Instant.now();
        final String sourceVersion = plugin.getPluginMeta().getVersion();
        final ExportCounts counts = ExportCounts.from(
                candidateCount, unavailable, rejected, publicListings);
        final MarketplaceSnapshot snapshot = new MarketplaceSnapshot(
                new MarketplaceSnapshotMetadata(
                        capturedAt,
                        DISPLAY_ZONE,
                        sourceVersion,
                        captureBanner(capturedAt),
                        "/warp shops"),
                counts.toSnapshotCounts(),
                publicListings);
        final Path outputDirectory = plugin.getDataFolder().toPath()
                .resolve("exports")
                .resolve("marketplace");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final MarketplaceSnapshotFiles files =
                        MarketplaceSnapshotWriter.write(outputDirectory, snapshot);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    exporting.set(false);
                    completion.accept(new ExportResult(
                            capturedAt, sourceVersion, counts, files));
                });
            } catch (IOException | RuntimeException exception) {
                plugin.getServer().getScheduler().runTask(
                        plugin, () -> failOnPrimaryThread(failure, exception));
            }
        });
    }

    private void failOnPrimaryThread(Consumer<Throwable> failure, Throwable throwable) {
        exporting.set(false);
        failure.accept(throwable);
    }

    private static String captureBanner(Instant capturedAt) {
        return "Captured on " + CAPTURE_FORMAT.format(capturedAt)
                + ". Prices, stock, storefronts, and directions may have changed."
                + " Use /" + Config.mainCommandName
                + " search for current availability.";
    }

    public enum StartResult {
        STARTED,
        CATALOGUE_NOT_READY,
        ALREADY_RUNNING
    }

    public record ExportCounts(
            int candidates,
            int published,
            int inStock,
            int outOfStock,
            int unchecked,
            int excludedUnavailable,
            int excludedInvalid
    ) {
        private static ExportCounts from(
                int candidates,
                int unavailable,
                int rejected,
                List<MarketplaceListing> listings
        ) {
            int inStock = 0;
            int outOfStock = 0;
            int unchecked = 0;
            for (MarketplaceListing listing : listings) {
                switch (listing.availabilityAtCapture()) {
                    case IN_STOCK -> inStock++;
                    case OUT_OF_STOCK -> outOfStock++;
                    case UNCHECKED -> unchecked++;
                }
            }
            return new ExportCounts(
                    candidates,
                    listings.size(),
                    inStock,
                    outOfStock,
                    unchecked,
                    unavailable,
                    rejected);
        }

        private MarketplaceSnapshotCounts toSnapshotCounts() {
            return new MarketplaceSnapshotCounts(
                    candidates,
                    published,
                    inStock,
                    outOfStock,
                    unchecked,
                    excludedUnavailable,
                    excludedInvalid);
        }
    }

    public record ExportResult(
            Instant capturedAt,
            String sourceVersion,
            ExportCounts counts,
            MarketplaceSnapshotFiles files
    ) {
        public ExportResult {
            Objects.requireNonNull(capturedAt, "capturedAt");
            Objects.requireNonNull(sourceVersion, "sourceVersion");
            Objects.requireNonNull(counts, "counts");
            Objects.requireNonNull(files, "files");
        }
    }
}
