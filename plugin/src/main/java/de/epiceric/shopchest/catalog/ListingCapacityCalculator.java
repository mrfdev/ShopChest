package de.epiceric.shopchest.catalog;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.ToIntFunction;

/** Computes exact-product room for a complete Customer-Sell Offer bundle. */
public final class ListingCapacityCalculator {

    private ListingCapacityCalculator() {
    }

    public static ListingCapacity inspect(
            ItemStack productTemplate,
            int configuredBundleAmount,
            Iterable<? extends ItemStack> contents,
            ExactItemMatcher exactItemMatcher
    ) {
        return inspect(
                productTemplate,
                configuredBundleAmount,
                contents,
                exactItemMatcher,
                ItemStack::getMaxStackSize);
    }

    static ListingCapacity inspect(
            ItemStack productTemplate,
            int configuredBundleAmount,
            Iterable<? extends ItemStack> contents,
            ExactItemMatcher exactItemMatcher,
            ToIntFunction<ItemStack> maximumStackSizeResolver
    ) {
        Objects.requireNonNull(exactItemMatcher, "exactItemMatcher");
        Objects.requireNonNull(maximumStackSizeResolver, "maximumStackSizeResolver");
        if (productTemplate == null
                || productTemplate.getType() == org.bukkit.Material.AIR
                || configuredBundleAmount <= 0
                || contents == null) {
            return ListingCapacity.unavailable();
        }

        final ItemStack normalizedTemplate = productTemplate.clone();
        normalizedTemplate.setAmount(1);
        long matchingCapacity = 0L;

        try {
            final int maximumStackSize = maximumStackSizeResolver.applyAsInt(
                    normalizedTemplate);
            if (maximumStackSize <= 0) {
                return ListingCapacity.unavailable();
            }
            for (ItemStack candidate : contents) {
                if (candidate == null || candidate.getType() == org.bukkit.Material.AIR
                        || candidate.getAmount() <= 0) {
                    matchingCapacity = Math.min(
                            Integer.MAX_VALUE,
                            matchingCapacity + maximumStackSize);
                    continue;
                }

                final int amount = candidate.getAmount();
                final ItemStack normalizedCandidate = candidate.clone();
                normalizedCandidate.setAmount(1);
                if (exactItemMatcher.matches(
                        normalizedCandidate,
                        normalizedTemplate.clone())) {
                    matchingCapacity = Math.min(
                            Integer.MAX_VALUE,
                            matchingCapacity + Math.max(0, maximumStackSize - amount));
                }
            }
        } catch (RuntimeException ignored) {
            return ListingCapacity.unavailable();
        }

        final int itemCapacity = (int) matchingCapacity;
        final int completeBundles = itemCapacity / configuredBundleAmount;
        return new ListingCapacity(
                completeBundles > 0
                        ? ListingCapacityState.CAN_ACCEPT
                        : ListingCapacityState.FULL,
                itemCapacity,
                completeBundles);
    }
}
