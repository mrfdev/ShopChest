package de.epiceric.shopchest.catalog;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Computes full-bundle availability from an already-loaded inventory view. */
public final class ListingAvailabilityCalculator {

    private ListingAvailabilityCalculator() {
    }

    public static ListingStock inspect(
            ItemStack productTemplate,
            int configuredBundleAmount,
            Iterable<? extends ItemStack> contents,
            ExactItemMatcher exactItemMatcher) {
        Objects.requireNonNull(exactItemMatcher, "exactItemMatcher");
        if (productTemplate == null
                || productTemplate.getType() == org.bukkit.Material.AIR
                || configuredBundleAmount <= 0
                || contents == null) {
            return ListingStock.unavailable();
        }

        final ItemStack normalizedTemplate = productTemplate.clone();
        normalizedTemplate.setAmount(1);
        long matchingItems = 0L;

        try {
            for (ItemStack candidate : contents) {
                if (candidate == null || candidate.getAmount() <= 0) {
                    continue;
                }

                final int amount = candidate.getAmount();
                final ItemStack normalizedCandidate = candidate.clone();
                normalizedCandidate.setAmount(1);
                if (exactItemMatcher.matches(normalizedCandidate, normalizedTemplate.clone())) {
                    matchingItems = Math.min(Integer.MAX_VALUE, matchingItems + amount);
                }
            }
        } catch (RuntimeException ignored) {
            return ListingStock.unavailable();
        }

        final int matchingItemCount = (int) matchingItems;
        final int completeBundles = matchingItemCount / configuredBundleAmount;
        final ListingAvailability availability = completeBundles > 0
                ? ListingAvailability.IN_STOCK
                : ListingAvailability.OUT_OF_STOCK;
        return new ListingStock(availability, matchingItemCount, completeBundles);
    }
}
