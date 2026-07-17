package de.epiceric.shopchest.platform;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.RegionAccessor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPlatformContractTest {

    @Test
    void pluginDescriptorRequiresPaper262Api() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(descriptor.contains("api-version: '26.2'"));
        }
    }

    @Test
    void requiredModernDisplayAndVisibilityApisArePresent() throws ReflectiveOperationException {
        assertNotNull(TextDisplay.class.getMethod("text", Component.class));
        assertNotNull(ItemDisplay.class.getMethod("setItemStack", ItemStack.class));
        assertNotNull(Display.class.getMethod("setTransformation", Transformation.class));
        assertNotNull(Display.class.getMethod("setInterpolationDuration", int.class));
        assertNotNull(Entity.class.getMethod("setVisibleByDefault", boolean.class));
        assertNotNull(Player.class.getMethod("showEntity", Plugin.class, Entity.class));
        assertNotNull(Player.class.getMethod("hideEntity", Plugin.class, Entity.class));
        assertNotNull(RegionAccessor.class.getMethod(
                "spawn", Location.class, Class.class, Consumer.class));
    }

    @Test
    void removedVersionSpecificPlatformCannotReturn() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("de.epiceric.shopchest.nms.paper.v1_21_7.PlatformImpl"));
    }
}
