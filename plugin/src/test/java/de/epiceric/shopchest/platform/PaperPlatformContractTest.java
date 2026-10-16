package de.epiceric.shopchest.platform;

import io.papermc.paper.command.brigadier.BasicCommand;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPlatformContractTest {

    @Test
    void embeddedTargetRequiresAReproducibleStableBuild() throws IOException {
        Properties metadata = new Properties();
        try (InputStream stream = getClass().getResourceAsStream("/shopchest-build.properties")) {
            assertNotNull(stream);
            metadata.load(stream);
        }
        org.junit.jupiter.api.Assertions.assertEquals("STABLE", metadata.getProperty("paper-channel"));
        org.junit.jupiter.api.Assertions.assertEquals("26.3.build."
                + metadata.getProperty("paper-build") + "-stable", metadata.getProperty("paper-api-version"));
    }

    @Test
    void requiredCompleteItemPersistenceApisArePresent() throws ReflectiveOperationException {
        assertNotNull(ItemStack.class.getMethod("serializeAsBytes"));
        assertNotNull(ItemStack.class.getMethod("deserializeBytes", byte[].class));
        assertNotNull(ItemStack.class.getMethod("isSimilar", ItemStack.class));
        assertNotNull(ItemStack.class.getMethod("of", org.bukkit.Material.class, int.class));
    }

    @Test
    void pluginDescriptorRequiresPaper263Api() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(descriptor.contains("api-version: '26.3'"));
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
        assertNotNull(JavaPlugin.class.getMethod(
                "registerCommand", String.class, String.class, BasicCommand.class));
    }

    @Test
    void removedVersionSpecificPlatformCannotReturn() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("de.epiceric.shopchest.nms.paper.v1_21_7.PlatformImpl"));
    }

    @Test
    void requiredLocalIdentityApisArePresent() throws ReflectiveOperationException {
        assertNotNull(org.bukkit.Server.class.getMethod("createProfileExact", java.util.UUID.class, String.class));
        assertNotNull(com.destroystokyo.paper.profile.PlayerProfile.class.getMethod("completeFromCache"));
        assertNotNull(org.bukkit.inventory.meta.SkullMeta.class.getMethod("getPlayerProfile"));
    }

    @Test
    void requiredSnapshotFreeInventoryApisArePresent() throws ReflectiveOperationException {
        assertNotNull(org.bukkit.inventory.Inventory.class.getMethod("getLocation"));
        assertNotNull(org.bukkit.inventory.Inventory.class.getMethod("getHolder", boolean.class));
        assertNotNull(org.bukkit.inventory.DoubleChestInventory.class.getMethod("getLeftSide"));
        assertNotNull(org.bukkit.inventory.DoubleChestInventory.class.getMethod("getRightSide"));
    }
}
