package de.epiceric.shopchest.storefront;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record MarketplaceScopePolicy(
        Mode mode,
        String marketplaceWorld,
        String marketplaceRegion
) {
    public MarketplaceScopePolicy {
        Objects.requireNonNull(mode, "mode");
        marketplaceWorld = normalize(marketplaceWorld);
        marketplaceRegion = normalize(marketplaceRegion);
        if (mode == Mode.MARKETPLACE
                && (marketplaceWorld.isEmpty() || marketplaceRegion.isEmpty())) {
            throw new IllegalArgumentException(
                    "Marketplace scope needs a world and WorldGuard region");
        }
    }

    public static MarketplaceScopePolicy marketplace(String world, String region) {
        return new MarketplaceScopePolicy(Mode.MARKETPLACE, world, region);
    }

    public static MarketplaceScopePolicy global() {
        return new MarketplaceScopePolicy(Mode.GLOBAL, "", "");
    }

    public boolean includes(String worldName, Set<String> regionIds) {
        if (mode == Mode.GLOBAL) {
            return true;
        }
        if (!marketplaceWorld.equals(normalize(worldName)) || regionIds == null) {
            return false;
        }
        return regionIds.stream()
                .filter(Objects::nonNull)
                .map(MarketplaceScopePolicy::normalize)
                .anyMatch(marketplaceRegion::equals);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public enum Mode {
        MARKETPLACE,
        GLOBAL
    }
}
