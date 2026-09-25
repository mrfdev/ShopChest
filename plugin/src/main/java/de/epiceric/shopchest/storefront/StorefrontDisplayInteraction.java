package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.ShopChest;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/** Transient, invisible click target aligned with a Storefront text panel. */
final class StorefrontDisplayInteraction {

    private static final float FONT_PIXELS_PER_BLOCK = 40.0F;
    private static final float LINE_HEIGHT_PIXELS = 10.0F;
    private static final float HORIZONTAL_PADDING = 0.30F;
    private static final float VERTICAL_PADDING = 0.25F;
    private static final float MINIMUM_WIDTH = 0.75F;
    private static final float MAXIMUM_WIDTH = 6.0F;
    private static final float MINIMUM_HEIGHT = 0.40F;
    private static final float MAXIMUM_HEIGHT = 2.5F;

    private final ShopChest plugin;
    private final Interaction interaction;

    StorefrontDisplayInteraction(
            ShopChest plugin,
            Location location,
            int panelWidth,
            float panelScale,
            int lineCount
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.interaction = Objects.requireNonNull(location.getWorld(), "location.world")
                .spawn(location, Interaction.class, entity -> {
                    entity.setPersistent(false);
                    entity.setVisibleByDefault(false);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setSilent(true);
                    entity.setResponsive(false);
                    applySize(entity, panelWidth, panelScale, lineCount);
                });
    }

    void refresh(Location location, int panelWidth, float panelScale, int lineCount) {
        interaction.teleport(location);
        applySize(interaction, panelWidth, panelScale, lineCount);
    }

    void setVisible(Player player, boolean visible) {
        if (visible) {
            player.showEntity(plugin, interaction);
        } else {
            player.hideEntity(plugin, interaction);
        }
    }

    UUID entityId() {
        return interaction.getUniqueId();
    }

    boolean matches(Entity entity) {
        return entity != null && interaction.getUniqueId().equals(entity.getUniqueId());
    }

    boolean exists() {
        return interaction.isValid();
    }

    void remove() {
        interaction.remove();
    }

    static float hitboxWidth(int panelWidth, float panelScale) {
        final float calculated = panelWidth * panelScale / FONT_PIXELS_PER_BLOCK
                + HORIZONTAL_PADDING;
        return clamp(calculated, MINIMUM_WIDTH, MAXIMUM_WIDTH);
    }

    static float hitboxHeight(int lineCount, float panelScale) {
        final float calculated = Math.max(1, lineCount) * LINE_HEIGHT_PIXELS * panelScale
                / FONT_PIXELS_PER_BLOCK + VERTICAL_PADDING;
        return clamp(calculated, MINIMUM_HEIGHT, MAXIMUM_HEIGHT);
    }

    private static void applySize(
            Interaction interaction,
            int panelWidth,
            float panelScale,
            int lineCount
    ) {
        interaction.setInteractionWidth(hitboxWidth(panelWidth, panelScale));
        interaction.setInteractionHeight(hitboxHeight(lineCount, panelScale));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
