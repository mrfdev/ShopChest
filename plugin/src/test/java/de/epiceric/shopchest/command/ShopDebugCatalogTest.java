package de.epiceric.shopchest.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopDebugCatalogTest {

    @Test
    void commandsUseTheConfiguredRootAndDocumentEveryDebugSection() {
        final var commands = ShopDebugCatalog.commands("market");

        assertTrue(commands.stream().allMatch(entry -> entry.usage().startsWith("/market")));
        assertTrue(commands.stream().anyMatch(entry ->
                entry.usage().equals(
                        "/market debug [status|commands|permissions|placeholders] [page]")));
        assertTrue(commands.stream().anyMatch(entry ->
                entry.usage().equals("/market admin debug")
                        && entry.description().contains("Compatibility alias")));
        assertTrue(commands.stream().anyMatch(entry ->
                entry.usage().equals("/market admin audit [player|all] [page]")
                        && entry.permission().equals("shopchest.admin.audit")));
    }

    @Test
    void permissionCatalogIncludesRuntimePatterns() {
        final var permissions = ShopDebugCatalog.dynamicPermissions();

        assertTrue(permissions.stream().anyMatch(entry ->
                entry.node().equals("shopchest.limit.<number>")));
        assertTrue(permissions.stream().anyMatch(entry ->
                entry.node().equals("shopchest.create.buy.<MATERIAL>[.<durability>]")));
        assertTrue(permissions.stream().allMatch(entry ->
                entry.defaultValue().equals("dynamic")));
    }

    @Test
    void placeholderCatalogIsCompleteAndLabelsReservedTokens() {
        final var placeholders = ShopDebugCatalog.placeholders();

        assertEquals(25, placeholders.size());
        assertTrue(placeholders.stream().anyMatch(entry ->
                entry.token().equals("%ITEM-DETAILS%")));
        assertTrue(placeholders.stream().anyMatch(entry ->
                entry.token().equals("%GENERATION%")
                        && entry.description().startsWith("Reserved")));
    }
}
