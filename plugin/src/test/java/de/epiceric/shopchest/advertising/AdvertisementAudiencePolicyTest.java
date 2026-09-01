package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvertisementAudiencePolicyTest {

    private final AdvertisementAudiencePolicy policy =
            AdvertisementAudiencePolicy.standard();

    @Test
    void requiresSixOnlinePlayersBeforeBroadcasting() {
        assertEquals(6, policy.minimumOnlinePlayers());
        assertFalse(policy.canBroadcastTo(0));
        assertFalse(policy.canBroadcastTo(5));
        assertTrue(policy.canBroadcastTo(6));
        assertTrue(policy.canBroadcastTo(20));
    }

    @Test
    void supportsAConfiguredMinimum() {
        final AdvertisementAudiencePolicy configured =
                AdvertisementAudiencePolicy.requiring(10);

        assertFalse(configured.canBroadcastTo(9));
        assertTrue(configured.canBroadcastTo(10));
    }
}
