package de.epiceric.shopchest.display;

import de.epiceric.shopchest.ShopChest;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Paper API-backed text panel.
 */
public final class TextDisplayWrapper {

    private static final double DISPLAY_Y_OFFSET = 2.075D;
    private static final float DISPLAY_WIDTH = 4.0F;
    private static final float DISPLAY_HEIGHT = 2.0F;

    private final ShopChest plugin;
    private final TextDisplay display;

    public TextDisplayWrapper(ShopChest plugin, Location location, TextDisplayData displayData) {
        this.plugin = plugin;
        this.display = Objects.requireNonNull(location.getWorld()).spawn(
                displayLocation(location),
                TextDisplay.class,
                entity -> {
                    entity.setPersistent(false);
                    entity.setVisibleByDefault(false);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setSilent(true);
                    entity.setDisplayWidth(DISPLAY_WIDTH);
                    entity.setDisplayHeight(DISPLAY_HEIGHT);
                    applyDisplayData(entity, displayData);
                });
    }

    public void setVisible(Player player, boolean visible) {
        if (visible) {
            player.showEntity(plugin, display);
        } else {
            player.hideEntity(plugin, display);
        }
    }

    public void setLocation(Location location) {
        display.teleport(displayLocation(location));
    }

    public void setDisplayData(TextDisplayData displayData) {
        applyDisplayData(display, displayData);
    }

    public void remove() {
        display.remove();
    }

    public boolean exists() {
        return display.isValid();
    }

    private static Location displayLocation(Location location) {
        return location.clone().add(0.0D, DISPLAY_Y_OFFSET, 0.0D);
    }

    private static void applyDisplayData(TextDisplay display, TextDisplayData data) {
        display.text(data.text());
        display.setLineWidth(data.lineWidth());
        display.setBackgroundColor(Color.fromARGB(data.backgroundColor()));
        display.setBillboard(data.fixedFacing() ? Display.Billboard.FIXED : Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setShadowed(false);
        display.setDefaultBackground(false);
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(data.scale()),
                new Quaternionf()));
    }
}
