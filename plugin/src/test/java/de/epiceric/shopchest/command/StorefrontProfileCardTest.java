package de.epiceric.shopchest.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorefrontProfileCardTest {

    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();
    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void rendersAReadableColoredCardWithoutDroppingProfileOrShopState() {
        final List<Component> lines = StorefrontProfileCard.render(
                new StorefrontProfileCard.Content(
                        "The Mud Shop",
                        "mrfloris",
                        "Need items? Check /warp shops",
                        "Mangrove biome items and mud",
                        "Below the mangrove trees",
                        false),
                new StorefrontProfileCard.Metrics(
                        34, 34, 23,
                        6, 4, 24, 0,
                        5, 2, 16, 0),
                "shops",
                OWNER,
                true);

        final String output = plain(lines);
        assertTrue(output.contains("STOREFRONT"));
        assertTrue(output.contains("◆ The Mud Shop"));
        assertTrue(output.contains("OWNER  mrfloris"));
        assertTrue(output.contains("✦ Need items? Check /warp shops"));
        assertTrue(output.contains("ABOUT  Mangrove biome items and mud"));
        assertTrue(output.contains("FIND US  Below the mangrove trees"));
        assertTrue(output.contains("SHOPS  34 total  •  34 selling  •  23 buying"));
        assertTrue(output.contains("STOCK  6 ready  •  4 empty  •  24 unchecked"));
        assertTrue(output.contains("SPACE  5 ready  •  2 full  •  16 unchecked"));
        assertTrue(output.contains("[ BROWSE 34 SHOPS ]  [ VISIT MARKETPLACE ]"));

        assertEquals(StorefrontProfileCard.LABEL, lines.get(2).color());
        assertEquals(StorefrontProfileCard.PRIMARY, lines.get(2).children().get(0).color());
        assertFalse(lines.get(2).color().equals(lines.get(2).children().get(0).color()));

        final List<ClickEvent> clicks = new ArrayList<>();
        collectClicks(lines.get(12), clicks);
        assertTrue(clicks.stream().anyMatch(click -> command(click).equals(
                "/shops profile shopowner " + OWNER + " shops 1")));
        assertTrue(clicks.stream().anyMatch(click -> command(click).equals("/warp shops")));
    }

    @Test
    void hiddenProfilesSuppressAuthoredTextButKeepTheOperationalSummary() {
        final List<Component> lines = StorefrontProfileCard.render(
                new StorefrontProfileCard.Content(
                        "Shops by mrfloris",
                        "mrfloris",
                        "secret tagline",
                        "secret description",
                        "secret location",
                        true),
                new StorefrontProfileCard.Metrics(
                        1, 1, 0,
                        1, 0, 0, 0,
                        0, 0, 0, 0),
                "shops",
                OWNER,
                false);

        final String output = plain(lines);
        assertTrue(output.contains("Storefront details are temporarily hidden by staff."));
        assertTrue(output.contains("SHOPS  1 total  •  1 selling  •  0 buying"));
        assertTrue(output.contains("STOCK  1 ready"));
        assertFalse(output.contains("secret"));
        assertFalse(output.contains("VISIT MARKETPLACE"));
    }

    private static String plain(List<Component> lines) {
        return lines.stream().map(PLAIN::serialize).reduce((left, right) ->
                left + "\n" + right).orElse("");
    }

    private static void collectClicks(Component component, List<ClickEvent> clicks) {
        if (component.clickEvent() != null) {
            clicks.add(component.clickEvent());
        }
        component.children().forEach(child -> collectClicks(child, clicks));
    }

    private static String command(ClickEvent click) {
        return ((ClickEvent.Payload.Text) click.payload()).value();
    }
}
