package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdvertisingCommandTest {

    private static final String NONCE = "d7d004ee-29ad-4ad7-a11a-cbaf631f65e6";

    @Test
    void parsesThePlayerAdvertisingDashboardAndExplicitConfirmations() {
        assertEquals(new AdvertisingCommand.Dashboard(),
                AdvertisingCommand.parse(new String[]{"advertise"}));
        assertEquals(new AdvertisingCommand.PassPreview(),
                AdvertisingCommand.parse(new String[]{"advertise", "pass"}));
        assertEquals(new AdvertisingCommand.ConfirmPass(NONCE),
                AdvertisingCommand.parse(
                        new String[]{"advertise", "pass", "confirm", NONCE}));
        assertEquals(new AdvertisingCommand.ConfirmRequest(NONCE),
                AdvertisingCommand.parse(new String[]{"advertise", "confirm", NONCE}));
        assertEquals(new AdvertisingCommand.Status(),
                AdvertisingCommand.parse(new String[]{"advertise", "status"}));
        assertEquals(new AdvertisingCommand.Cancel(),
                AdvertisingCommand.parse(new String[]{"advertise", "cancel"}));
    }

    @Test
    void parsesOnlyTheSupportedAdminCurrencyActions() {
        assertEquals(new AdvertisingCommand.AdminCurrencyStatus(),
                AdvertisingCommand.parse(
                        new String[]{"admin", "advertise", "currency", "status"}));
        assertEquals(new AdvertisingCommand.AdminCurrencyCapture(),
                AdvertisingCommand.parse(
                        new String[]{"admin", "advertise", "currency", "capture"}));
        assertEquals(new AdvertisingCommand.AdminCurrencyClear(),
                AdvertisingCommand.parse(
                        new String[]{"admin", "advertise", "currency", "clear"}));
    }

    @Test
    void rejectsUnknownShapesExtraArgumentsAndUnsafeConfirmationTokens() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvertisingCommand.parse(new String[]{}));
        assertThrows(IllegalArgumentException.class,
                () -> AdvertisingCommand.parse(
                        new String[]{"advertise", "status", "unexpected"}));
        assertThrows(IllegalArgumentException.class,
                () -> AdvertisingCommand.parse(new String[]{
                        "advertise", "confirm", "aaaaaaaaaaaaaaaa;op"}));
        assertThrows(IllegalArgumentException.class,
                () -> AdvertisingCommand.parse(new String[]{
                        "advertise", "confirm", "aaaaaaaaaaaaaaaé"}));
        assertThrows(IllegalArgumentException.class,
                () -> AdvertisingCommand.parse(new String[]{
                        "admin", "advertise", "currency", "delete"}));
    }
}
