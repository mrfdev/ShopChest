package de.epiceric.shopchest.catalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable candidate ordering and summary reused while a player paginates. */
public final class ShopSearchSnapshot {

    public static final int PAGE_SIZE = 4;

    private final ResolvedMaterial material;
    private final Instant capturedAt;
    private final List<PublicShopListing> orderedListings;
    private final ShopSearchSummary summary;

    private ShopSearchSnapshot(
            ResolvedMaterial material,
            Instant capturedAt,
            List<PublicShopListing> orderedListings,
            ShopSearchSummary summary) {
        this.material = material;
        this.capturedAt = capturedAt;
        this.orderedListings = List.copyOf(orderedListings);
        this.summary = summary;
    }

    public static ShopSearchSnapshot capture(
            ResolvedMaterial material,
            Instant capturedAt,
            Collection<PublicShopListing> candidateListings) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(candidateListings, "candidateListings");

        final List<PublicShopListing> inStock = new ArrayList<>();
        final List<PublicShopListing> uncheckedListings = new ArrayList<>();
        final Set<UUID> inStockOwners = new HashSet<>();
        int outOfStock = 0;
        int unchecked = 0;

        for (PublicShopListing listing : candidateListings) {
            Objects.requireNonNull(listing, "candidateListings cannot contain null");
            final PublicShopCandidate candidate = listing.candidate();
            if (!PublicCatalogueEligibility.isEligible(candidate)
                    || candidate.baseMaterial() != material.material()) {
                continue;
            }

            switch (listing.stock().availability()) {
                case IN_STOCK -> {
                    inStock.add(listing);
                    inStockOwners.add(candidate.ownerId());
                }
                case OUT_OF_STOCK -> outOfStock++;
                case UNCHECKED -> {
                    uncheckedListings.add(listing);
                    unchecked++;
                }
                case UNAVAILABLE -> {
                    // Unavailable records are intentionally omitted from public totals.
                }
            }
        }

        final List<PublicShopListing> ordered = new ArrayList<>(
                inStock.size() + uncheckedListings.size());
        ordered.addAll(OwnerListingInterleaver.interleave(inStock));
        ordered.addAll(OwnerListingInterleaver.interleave(uncheckedListings));
        return new ShopSearchSnapshot(
                material,
                capturedAt,
                ordered,
                new ShopSearchSummary(
                        inStock.size(),
                        inStockOwners.size(),
                        outOfStock,
                        unchecked));
    }

    public ResolvedMaterial material() {
        return material;
    }

    public Instant capturedAt() {
        return capturedAt;
    }

    public List<PublicShopListing> orderedInStockListings() {
        return orderedListings.stream()
                .filter(listing -> listing.stock().availability()
                        == ListingAvailability.IN_STOCK)
                .toList();
    }

    public List<PublicShopListing> orderedListings() {
        return orderedListings;
    }

    public ShopSearchSummary summary() {
        return summary;
    }

    public ShopSearchPage page(int requestedPage) {
        if (requestedPage < 1) {
            throw new IllegalArgumentException("requestedPage must be positive");
        }

        final int pageCount = Math.max(
                1,
                (orderedListings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.min(requestedPage, pageCount);
        final int fromIndex = Math.min((page - 1) * PAGE_SIZE, orderedListings.size());
        final int toIndex = Math.min(fromIndex + PAGE_SIZE, orderedListings.size());
        return new ShopSearchPage(
                material,
                capturedAt,
                orderedListings.subList(fromIndex, toIndex),
                page,
                pageCount,
                summary);
    }
}
