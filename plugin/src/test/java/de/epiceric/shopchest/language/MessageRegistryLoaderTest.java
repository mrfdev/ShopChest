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

        assertTrue(help.contains("/shops admin <list|audit|debug>"));
        assertTrue(registry.getMessage(
                Message.ADMIN_HELP_LIST,
                new Replacement(Placeholder.COMMAND, "shops"))
                .contains("/shops admin list <player> [page]"));
        final String debugHelp = registry.getMessage(
                Message.ADMIN_HELP_DEBUG,
                new Replacement(Placeholder.COMMAND, "shops"));
        assertTrue(debugHelp.contains("/shops admin debug"));
        assertTrue(debugHelp.contains("shopchest.admin.debug"));
        final String auditHelp = registry.getMessage(
                Message.ADMIN_HELP_AUDIT,
                new Replacement(Placeholder.COMMAND, "shops"));
        assertTrue(auditHelp.contains("/shops admin audit [player|all] [page]"));
        assertTrue(auditHelp.contains("shopchest.admin.audit"));
    }

    @Test
    void providesACompactOutOfStockHologramFallback() {
        assertEquals("[Out of stock]", createRegistry().getMessage(Message.HOLOGRAM_OUT_OF_STOCK));
    }

    @Test
    void inspectionDetailsFallbackRetainsItsRichComponentPlaceholder() {
        assertTrue(createRegistry()
                .getMessage(Message.SHOP_INFO_ITEM_DETAILS)
                .contains(Placeholder.ITEM_DETAILS.toString()));
    }

    @Test
    void offlineRevenueDefaultsProvideCompactSummaryActionAndConfiguredCommandHover() {
        final MessageRegistry registry = createRegistry();

        final String summary = registry.getMessage(
                Message.REVENUE_WHILE_OFFLINE,
                new Replacement(Placeholder.REVENUE, 25));
        assertTrue(summary.contains("\u00a7a$25"));
        assertTrue(registry.getMessage(Message.REVENUE_WHILE_OFFLINE_ACTION)
                .contains("View recent trades"));
        assertTrue(registry.getMessage(
                Message.REVENUE_WHILE_OFFLINE_HOVER,
                new Replacement(Placeholder.COMMAND, "shops"))
                .contains("/shops recent"));
    }

    @Test
    void upgradesThePreviousWhiteOfflineRevenueDefaultWithoutOverridingCustomColors() {
        final MessageRegistry upgradedRegistry = new MessageRegistry(
                new MessageRegistryLoader(Map.of(
                        "message.revenue-while-offline.summary",
                        "&6While you were offline &8- &7Shop revenue: &f%REVENUE%"))
                        .getMessages(),
                price -> "$" + price);
        final MessageRegistry customizedRegistry = new MessageRegistry(
                new MessageRegistryLoader(Map.of(
                        "message.revenue-while-offline.summary",
                        "&6While you were offline: &d%REVENUE%"))
                        .getMessages(),
                price -> "$" + price);

        assertTrue(upgradedRegistry.getMessage(
                Message.REVENUE_WHILE_OFFLINE,
                new Replacement(Placeholder.REVENUE, 25))
                .contains("\u00a7a$25"));
        assertTrue(customizedRegistry.getMessage(
                Message.REVENUE_WHILE_OFFLINE,
                new Replacement(Placeholder.REVENUE, 25))
                .contains("\u00a7d$25"));
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

    @Test
    void shopHealthFallbackExplainsAllCountsAndKeepsListDiscoverable() {
        final MessageRegistry registry = createRegistry();
        final String health = registry.getMessage(
                Message.SHOP_LIST_HEALTH,
                new Replacement(Placeholder.HEALTHY, 3),
                new Replacement(Placeholder.ATTENTION, 2),
                new Replacement(Placeholder.OUT_OF_STOCK, 1),
                new Replacement(Placeholder.FULL, 1),
                new Replacement(Placeholder.BLOCKED, 1),
                new Replacement(Placeholder.UNAVAILABLE, 0),
                new Replacement(Placeholder.UNCHECKED, 4));

        assertTrue(health.contains("3 ready"));
        assertTrue(health.contains("2 needing attention"));
        assertTrue(health.contains("1 out of stock"));
        assertTrue(health.contains("1 full"));
        assertTrue(health.contains("1 blocked"));
        assertTrue(health.contains("0 unavailable"));
        assertTrue(health.contains("4 unchecked"));
        assertTrue(registry.getMessage(
                Message.INFO_SHOP_HEALTH,
                new Replacement(Placeholder.COMMAND, "shops"))
                .contains("/shops list"));
    }

    @Test
    void shopAuditFallbackIsExplicitlyReadOnlyAndLabelsEveryFinding() {
        final MessageRegistry registry = createRegistry();
        final String dryRun = registry.getMessage(Message.ADMIN_AUDIT_DRY_RUN);

        assertTrue(dryRun.contains("Dry run only"));
        assertTrue(dryRun.contains("no records, chunks, blocks, inventories, PDC"));
        assertTrue(registry.getMessage(Message.ADMIN_AUDIT_REMOVE_ON_ERROR_WARNING)
                .contains("audit is read-only"));
        assertEquals("World unavailable (missing or unloaded)", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_MISSING_WORLD));
        assertEquals("Missing container", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_MISSING_CONTAINER));
        assertEquals("Unsupported container", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_UNSUPPORTED_CONTAINER));
        assertEquals("Incomplete container", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_INCOMPLETE_CONTAINER));
        assertEquals("Blocked display space", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_BLOCKED_DISPLAY));
        assertEquals("Invalid product", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_INVALID_PRODUCT));
        assertEquals("Invalid owner", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_INVALID_OWNER));
        assertEquals("Invalid shop type", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_INVALID_SHOP_TYPE));
        assertEquals("Invalid trade terms", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_INVALID_TERMS));
        assertEquals("Invalid location", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_INVALID_LOCATION));
        assertEquals("Invalid record", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_INVALID_RECORD));
        assertEquals("Conflicting records", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_CONFLICTING_RECORD));
        assertEquals("Shadowed by a different loaded shop", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_SHADOWED_RECORD));
        assertEquals("Not active in loaded runtime", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_INACTIVE_RECORD));
        assertEquals("Unchecked chunk", registry.getMessage(
                Message.ADMIN_AUDIT_REASON_UNCHECKED));
        assertTrue(registry.getMessage(Message.ADMIN_AUDIT_BUSY)
                .contains("already running"));
        assertTrue(registry.getMessage(Message.ADMIN_AUDIT_SENSITIVE)
                .contains("before sharing"));
    }

    private static MessageRegistry createRegistry() {
        return new MessageRegistry(
                new MessageRegistryLoader(Map.of()).getMessages(),
                price -> "$" + price);
    }
}
