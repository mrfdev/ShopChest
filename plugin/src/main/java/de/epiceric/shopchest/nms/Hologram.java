package de.epiceric.shopchest.nms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.utils.LegacyColorUtils;

public class Hologram {

    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final List<Component> lines = new ArrayList<>();
    private final ArmorStandWrapper wrapper;
    private Location location;
    private boolean exists;

    public Hologram(ShopChest plugin, String[] lines, Location location) {
        this.location = location.clone();
        setStoredLines(lines);
        this.wrapper = new ArmorStandWrapper(plugin, this.location, createDisplayData());
        this.exists = true;
    }

    public Hologram(ShopChest plugin, Component[] lines, Location location) {
        this.location = location.clone();
        setStoredLines(lines);
        this.wrapper = new ArmorStandWrapper(plugin, this.location, createDisplayData());
        this.exists = true;
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
        wrapper.setLocation(this.location);
    }

    public boolean exists() {
        return exists;
    }

    public boolean contains(ArmorStand armorStand) {
        return exists && armorStand.getUniqueId().equals(wrapper.getUuid());
    }

    public List<ArmorStandWrapper> getArmorStandWrappers() {
        return exists ? Collections.singletonList(wrapper) : Collections.emptyList();
    }

    public boolean isVisible(Player player) {
        return viewers.contains(player.getUniqueId());
    }

    public void showPlayer(Player player) {
        showPlayer(player, false);
    }

    public void showPlayer(Player player, boolean force) {
        if (viewers.add(player.getUniqueId()) || force) {
            wrapper.setVisible(player, true);
        }
    }

    public void hidePlayer(Player player) {
        hidePlayer(player, false);
    }

    public void hidePlayer(Player player, boolean force) {
        if (viewers.remove(player.getUniqueId()) || force) {
            wrapper.setVisible(player, false);
        }
    }

    public void remove() {
        viewers.clear();
        wrapper.remove();
        lines.clear();
        exists = false;
    }

    public void resetVisible(Player player) {
        viewers.remove(player.getUniqueId());
    }

    public String[] getLines() {
        return lines.stream().map(HologramTextFormatter::toLegacy).toArray(String[]::new);
    }

    public void setLines(String[] lines) {
        setStoredLines(lines);
        refreshDisplay();
    }

    public void setLines(Component[] lines) {
        setStoredLines(lines);
        refreshDisplay();
    }

    public void addLine(int line, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final int insertionPoint = Math.max(0, Math.min(line, lines.size()));
        lines.add(insertionPoint, prepareLegacyLine(text));
        refreshDisplay();
    }

    public void setLine(int line, String text) {
        if (text == null || text.isEmpty()) {
            removeLine(line);
            return;
        }
        if (line >= lines.size()) {
            addLine(line, text);
            return;
        }
        if (line < 0) {
            return;
        }
        lines.set(line, prepareLegacyLine(text));
        refreshDisplay();
    }

    public void removeLine(int line) {
        if (line >= 0 && line < lines.size()) {
            lines.remove(line);
            refreshDisplay();
        }
    }

    public void refreshDisplay() {
        wrapper.setDisplayData(createDisplayData());
    }

    private void setStoredLines(String[] newLines) {
        lines.clear();
        for (String line : newLines) {
            if (line != null && !line.isEmpty()) {
                lines.add(prepareLegacyLine(line));
            }
        }
    }

    private void setStoredLines(Component[] newLines) {
        lines.clear();
        for (Component line : newLines) {
            if (line != null && !line.equals(Component.empty())) {
                lines.add(line);
            }
        }
    }

    private Component prepareLegacyLine(String line) {
        return HologramTextFormatter.fromLegacy(
                LegacyColorUtils.translateAlternateColorCodes('&', line));
    }

    private TextDisplayData createDisplayData() {
        return new TextDisplayData(
                HologramTextFormatter.toPanelComponent(lines),
                Config.hologramPanelWidth,
                Config.hologramBackgroundColor,
                Config.hologramFixedFacing,
                Config.hologramTextScale);
    }
}
