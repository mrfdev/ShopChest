package de.epiceric.shopchest.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Orders organic results so every matching owner receives one row before duplicates. */
public final class OwnerListingInterleaver {

    private static final Comparator<PublicShopListing> ORGANIC_ORDER = Comparator
            .comparingDouble(OwnerListingInterleaver::unitPrice)
            .thenComparingInt(listing -> listing.candidate().shopId())
            .thenComparing(listing -> listing.candidate().ownerId(), UUID::compareTo);

    private OwnerListingInterleaver() {
    }

    public static List<PublicShopListing> interleave(
            Collection<PublicShopListing> listings) {
        Objects.requireNonNull(listings, "listings");

        final List<PublicShopListing> sorted = new ArrayList<>(listings);
        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("listings cannot contain null");
        }
        sorted.sort(ORGANIC_ORDER);

        final Set<UUID> representedOwners = new HashSet<>();
        final List<PublicShopListing> firstOwnerListings = new ArrayList<>();
        final List<PublicShopListing> remainingListings = new ArrayList<>();
        for (PublicShopListing listing : sorted) {
            if (representedOwners.add(listing.candidate().ownerId())) {
                firstOwnerListings.add(listing);
            } else {
                remainingListings.add(listing);
            }
        }

        firstOwnerListings.addAll(remainingListings);
        return List.copyOf(firstOwnerListings);
    }

    private static double unitPrice(PublicShopListing listing) {
        final PublicShopCandidate candidate = listing.candidate();
        if (candidate.bundleAmount() <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return candidate.customerBuyPrice() / candidate.bundleAmount();
    }
}
