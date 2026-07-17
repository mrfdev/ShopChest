package de.epiceric.shopchest.config.hologram;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramFormatMigrationTest {

    @Test
    void recognizesTheExistingBundledThreeLineLayout() throws Exception {
        assertTrue(HologramFormat.shouldInsertDefaultItemDetails(lines(defaultLayout())));
    }

    @Test
    void leavesCustomLayoutsUntouched() throws Exception {
        final String customLayout = defaultLayout().replace(
                "%COLOR-QUANTITY%%AMOUNT% x %COLOR-ITEM%%ITEMNAME%%COLOR-RESET%",
                "Custom %ITEMNAME%");
        assertFalse(HologramFormat.shouldInsertDefaultItemDetails(lines(customLayout)));
    }

    @Test
    void doesNotInsertASecondDetailsLine() throws Exception {
        final String fourLineLayout = defaultLayout()
                .replace("  2:\n", "  2:\n    options:\n      item-details:\n"
                        + "        format: \"%ITEM-DETAILS%\"\n        requirements:\n"
                        + "          - HAS_ITEM_DETAILS\n  3:\n");
        assertFalse(HologramFormat.shouldInsertDefaultItemDetails(lines(fourLineLayout)));
    }

    private static ConfigurationSection lines(String yaml) throws Exception {
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(yaml);
        return configuration.getConfigurationSection("lines");
    }

    private static String defaultLayout() {
        return """
                lines:
                  0:
                    options:
                      normal-shop:
                        format: "%COLOR-OWNER%%VENDOR%%COLOR-RESET%"
                      admin-shop:
                        format: "%COLOR-ADMIN%Admin Shop%COLOR-RESET%"
                  1:
                    options:
                      default:
                        format: "%COLOR-QUANTITY%%AMOUNT% x %COLOR-ITEM%%ITEMNAME%%COLOR-RESET%"
                  2:
                    options:
                      buy-and-sell:
                        format: "%COLOR-LABEL%Buy: %COLOR-BUY-VALUE%%BUY-PRICE% %COLOR-SEPARATOR%| %COLOR-LABEL%Sell: %COLOR-SELL-VALUE%%SELL-PRICE%%COLOR-RESET%"
                      only-buy:
                        format: "%COLOR-LABEL%Buy: %COLOR-BUY-VALUE%%BUY-PRICE%%COLOR-RESET%"
                      only-sell:
                        format: "%COLOR-LABEL%Sell: %COLOR-SELL-VALUE%%SELL-PRICE%%COLOR-RESET%"
                """;
    }
}
