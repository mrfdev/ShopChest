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
    void adminHelpUsesTheNestedPermissionNode() {
        final MessageRegistry registry = createRegistry();
        final String help = registry.getMessage(
                Message.HELP_COMMAND_ADMIN,
                new Replacement(Placeholder.COMMAND, "shops"));

        assertTrue(help.contains("/shops admin list <player> [page]"));
        assertTrue(help.contains("shopchest.admin.list"));
    }

    @Test
    void providesACompactOutOfStockHologramFallback() {
        assertEquals("[Out of stock]", createRegistry().getMessage(Message.HOLOGRAM_OUT_OF_STOCK));
    }

    private static MessageRegistry createRegistry() {
        return new MessageRegistry(
                new MessageRegistryLoader(Map.of()).getMessages(),
                price -> "$" + price);
    }
}
