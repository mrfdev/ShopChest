package de.epiceric.shopchest.language;

import de.epiceric.shopchest.config.Placeholder;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageRegistryTest {

    @Test
    void formatsNumericShopEditPricesExactlyOnce() {
        final String[] messages = new String[Message.values().length];
        messages[Message.SHOP_EDITED.ordinal()] =
                "%AMOUNT%x %ITEMNAME% | Buy %BUY-PRICE% | Sell %SELL-PRICE%";
        messages[Message.SHOP_INFO_DISABLED.ordinal()] = "Disabled";
        final MessageRegistry registry = new MessageRegistry(
                messages,
                value -> String.format(Locale.ROOT, "%.2f\u20AC", value));

        assertEquals(
                "50x Potion | Buy 100.00\u20AC | Sell 0.00\u20AC",
                registry.getMessage(
                        Message.SHOP_EDITED,
                        new Replacement(Placeholder.AMOUNT, 50),
                        new Replacement(Placeholder.ITEM_NAME, "Potion"),
                        new Replacement(Placeholder.BUY_PRICE, 100.0D),
                        new Replacement(Placeholder.SELL_PRICE, 0.0D)));
    }
}
