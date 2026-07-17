package de.epiceric.shopchest.language;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import de.epiceric.shopchest.config.Placeholder;
import org.junit.jupiter.api.Test;

class MessageRegistryLoaderTest {

    @Test
    void registersFallbackTextForEveryMessage() {
        final MessageRegistry registry = createRegistry();

        for (Message message : Message.values()) {
            assertFalse(
                    registry.getMessage(message).contains("Message not found"),
                    () -> "Missing fallback for " + message);
        }
    }

    @Test
    void infoExplainsTheCreationCommandUsingTheConfiguredRootCommand() {
        final MessageRegistry registry = createRegistry();
        final String step = registry.getMessage(
                Message.INFO_STEP_CREATE,
                new Replacement(Placeholder.COMMAND, "shops"));

        assertTrue(step.contains("/shops create <amount> <buy-price> <sell-price>"));
    }

    @Test
    void staffHelpIncludesCompleteSyntaxAndPermission() {
        final MessageRegistry registry = createRegistry();
        final String help = registry.getMessage(
                Message.HELP_COMMAND_REMOVEALL,
                new Replacement(Placeholder.COMMAND, "shops"));

        assertTrue(help.contains("/shops removeall <player>"));
        assertTrue(help.contains("shopchest.remove.other"));
    }

    @Test
    void adminHelpIntroducesNestedAdminActions() {
        final MessageRegistry registry = createRegistry();
        final String help = registry.getMessage(
                Message.HELP_COMMAND_ADMIN,
                new Replacement(Placeholder.COMMAND, "shops"));

        assertTrue(help.contains("/shops admin <list|debug>"));
        assertTrue(registry.getMessage(
                Message.ADMIN_HELP_LIST,
                new Replacement(Placeholder.COMMAND, "shops"))
                .contains("/shops admin list <player> [page]"));
        final String debugHelp = registry.getMessage(
                Message.ADMIN_HELP_DEBUG,
                new Replacement(Placeholder.COMMAND, "shops"));
        assertTrue(debugHelp.contains("/shops admin debug"));
        assertTrue(debugHelp.contains("shopchest.admin.debug"));
    }

    @Test
    void providesACompactOutOfStockHologramFallback() {
        assertEquals("[Out of stock]", createRegistry().getMessage(Message.HOLOGRAM_OUT_OF_STOCK));
    }

    @Test
    void oldRecentEntryKeysDoNotPreventTheCompactUpgrade() {
        final MessageRegistry registry = new MessageRegistry(
                new MessageRegistryLoader(Map.of(
                        "message.recent.entry.shop-buy",
                        "legacy verbose transaction row")).getMessages(),
                price -> "$" + price);

        final String row = registry.getMessage(
                Message.RECENT_ENTRY_SHOP_BUY,
                new Replacement(Placeholder.PRICE, "+$17.00"),
                new Replacement(Placeholder.COUNTERPARTY, "Alex"),
                new Replacement(Placeholder.AMOUNT, 17),
                new Replacement(Placeholder.ITEM_NAME, "Potion"));

        assertFalse(row.contains("legacy verbose"));
        assertTrue(row.contains("Alex"));
        assertTrue(row.contains("17x Potion"));
        assertTrue(row.contains("+$17.00"));
    }

    @Test
    void oldShopListEntryDoesNotPreventTheCompactUpgrade() {
        final MessageRegistry registry = new MessageRegistry(
                new MessageRegistryLoader(Map.of(
                        "message.shopList.entry",
                        "legacy row with coordinates")).getMessages(),
                price -> "$" + price);

        final String row = registry.getMessage(
                Message.SHOP_LIST_ENTRY,
                new Replacement(Placeholder.SHOP_ID, 12),
                new Replacement(Placeholder.AMOUNT, 5),
                new Replacement(Placeholder.ITEM_NAME, "Oak Log"),
                new Replacement(Placeholder.STOCK, ""));

        assertFalse(row.contains("legacy row"));
        assertTrue(row.contains("#12"));
        assertTrue(row.contains("5x Oak Log"));
        assertFalse(row.contains("%STOCK%"));
    }

    private static MessageRegistry createRegistry() {
        return new MessageRegistry(
                new MessageRegistryLoader(Map.of()).getMessages(),
                price -> "$" + price);
    }
}
