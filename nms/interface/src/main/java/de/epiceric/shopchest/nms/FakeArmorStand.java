package de.epiceric.shopchest.nms;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface FakeArmorStand extends FakeEntity {

    void sendData(String name, Iterable<Player> receivers);

    default void sendData(TextDisplayData data, Iterable<Player> receivers) {
        sendData(TextComponentHelper.LEGACY_COMPONENT_SERIALIZER.serialize(data.text()), receivers);
    }

    void setLocation(Location location, Iterable<Player> receivers);

}
