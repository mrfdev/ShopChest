package de.epiceric.shopchest.nms;

import de.epiceric.shopchest.ShopChest;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ArmorStandWrapper {

    private final UUID uuid = UUID.randomUUID();
    private final FakeArmorStand fakeArmorStand;

    private Location location;
    private TextDisplayData displayData;

    public ArmorStandWrapper(ShopChest plugin, Location location, String customName) {
        this(plugin, location, new TextDisplayData(customName));
    }

    public ArmorStandWrapper(ShopChest plugin, Location location, Component customName) {
        this(plugin, location, new TextDisplayData(customName));
    }

    public ArmorStandWrapper(ShopChest plugin, Location location, TextDisplayData displayData) {
        this.location = location;
        this.displayData = displayData;
        this.fakeArmorStand = plugin.getPlatform().createFakeArmorStand();
    }

    public void setVisible(Player player, boolean visible) {
        final List<Player> receiver = Collections.singletonList(player);
        if(visible){
            fakeArmorStand.spawn(uuid, location, receiver);
            fakeArmorStand.sendData(displayData, receiver);
        }
        else if(fakeArmorStand.getEntityId() != -1){
            fakeArmorStand.remove(receiver);
        }
    }

    public void setLocation(Location location) {
        this.location = location;
        fakeArmorStand.setLocation(location, Objects.requireNonNull(location.getWorld()).getPlayers());
    }

    public void setCustomName(String customName) {
        setDisplayData(new TextDisplayData(customName, displayData.lineWidth(), displayData.backgroundColor(),
                displayData.fixedFacing(), displayData.scale()));
    }

    public void setDisplayData(TextDisplayData displayData) {
        this.displayData = displayData;
        fakeArmorStand.sendData(displayData, Objects.requireNonNull(location.getWorld()).getPlayers());
    }

    public void remove() {
        for (Player player : Objects.requireNonNull(location.getWorld()).getPlayers()) {
            setVisible(player, false);
        }
    }

    public int getEntityId() {
        return fakeArmorStand.getEntityId();
    }

    public UUID getUuid() {
        return uuid;
    }

    public Location getLocation() {
        return location.clone();
    }

    public String getCustomName() {
        return TextComponentHelper.LEGACY_COMPONENT_SERIALIZER.serialize(displayData.text());
    }
}
