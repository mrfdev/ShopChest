package de.epiceric.shopchest.advertising;

/** Determines whether enough players are present for a public advertisement. */
final class AdvertisementAudiencePolicy {

    static final int DEFAULT_MINIMUM_ONLINE_PLAYERS = 6;

    private final int minimumOnlinePlayers;

    private AdvertisementAudiencePolicy(int minimumOnlinePlayers) {
        if (minimumOnlinePlayers < 1) {
            throw new IllegalArgumentException("minimumOnlinePlayers must be positive");
        }
        this.minimumOnlinePlayers = minimumOnlinePlayers;
    }

    static AdvertisementAudiencePolicy standard() {
        return requiring(DEFAULT_MINIMUM_ONLINE_PLAYERS);
    }

    static AdvertisementAudiencePolicy requiring(int minimumOnlinePlayers) {
        return new AdvertisementAudiencePolicy(minimumOnlinePlayers);
    }

    int minimumOnlinePlayers() {
        return minimumOnlinePlayers;
    }

    boolean canBroadcastTo(int onlinePlayers) {
        if (onlinePlayers < 0) {
            throw new IllegalArgumentException("onlinePlayers cannot be negative");
        }
        return onlinePlayers >= minimumOnlinePlayers;
    }
}
