package de.epiceric.shopchest.nms.paper.v1_21_7;

import java.lang.reflect.Field;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;

import de.epiceric.shopchest.nms.FakeArmorStand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

public class FakeTextDisplayImpl extends FakeEntityImpl<String> implements FakeArmorStand {

    private final static byte BILLBOARD_CENTER = 3;
    private final static byte DEFAULT_TEXT_OPACITY = -1;
    private final static byte DEFAULT_STYLE_FLAGS = 0;
    private final static int DEFAULT_LINE_WIDTH = 200;
    private final static int TRANSPARENT_BACKGROUND = 0;
    private final static float MARKER_ARMOR_STAND_OFFSET = 1.975f;
    private final static float CULLING_WIDTH = 3f;
    private final static float CULLING_HEIGHT = 0.5f;
    private final static EntityDataAccessor<Byte> DATA_BILLBOARD_RENDER_CONSTRAINTS;
    private final static EntityDataAccessor<Float> DATA_WIDTH;
    private final static EntityDataAccessor<Float> DATA_HEIGHT;
    private final static EntityDataAccessor<Component> DATA_TEXT;
    private final static EntityDataAccessor<Integer> DATA_LINE_WIDTH;
    private final static EntityDataAccessor<Integer> DATA_BACKGROUND_COLOR;
    private final static EntityDataAccessor<Byte> DATA_TEXT_OPACITY;
    private final static EntityDataAccessor<Byte> DATA_STYLE_FLAGS;

    static {
        try {
            DATA_BILLBOARD_RENDER_CONSTRAINTS = dataAccessor(Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
            DATA_WIDTH = dataAccessor(Display.class, "DATA_WIDTH_ID");
            DATA_HEIGHT = dataAccessor(Display.class, "DATA_HEIGHT_ID");
            DATA_TEXT = dataAccessor(Display.TextDisplay.class, "DATA_TEXT_ID");
            DATA_LINE_WIDTH = dataAccessor(Display.TextDisplay.class, "DATA_LINE_WIDTH_ID");
            DATA_BACKGROUND_COLOR = dataAccessor(Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID");
            DATA_TEXT_OPACITY = dataAccessor(Display.TextDisplay.class, "DATA_TEXT_OPACITY_ID");
            DATA_STYLE_FLAGS = dataAccessor(Display.TextDisplay.class, "DATA_STYLE_FLAGS_ID");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> dataAccessor(Class<?> owner, String fieldName)
            throws ReflectiveOperationException {
        final Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (EntityDataAccessor<T>) field.get(null);
    }

    public FakeTextDisplayImpl() {
        super();
    }

    @Override
    public void sendData(String name, Iterable<Player> receivers) {
        sendData(receivers, name);
    }

    @Override
    protected EntityType<?> getEntityType() {
        return EntityType.TEXT_DISPLAY;
    }

    @Override
    protected float getSpawnOffSet() {
        return MARKER_ARMOR_STAND_OFFSET;
    }

    @Override
    protected int getDataItemCount() {
        return 8;
    }

    @Override
    protected void addSpecificData(List<SynchedEntityData.DataValue<?>> packedItems, String name) {
        packedItems.add(SynchedEntityData.DataValue.create(DATA_BILLBOARD_RENDER_CONSTRAINTS, BILLBOARD_CENTER));
        packedItems.add(SynchedEntityData.DataValue.create(DATA_WIDTH, CULLING_WIDTH));
        packedItems.add(SynchedEntityData.DataValue.create(DATA_HEIGHT, CULLING_HEIGHT));
        packedItems.add(SynchedEntityData.DataValue.create(DATA_TEXT, CraftChatMessage.fromStringOrNull(name)));
        packedItems.add(SynchedEntityData.DataValue.create(DATA_LINE_WIDTH, DEFAULT_LINE_WIDTH));
        packedItems.add(SynchedEntityData.DataValue.create(DATA_BACKGROUND_COLOR, TRANSPARENT_BACKGROUND));
        packedItems.add(SynchedEntityData.DataValue.create(DATA_TEXT_OPACITY, DEFAULT_TEXT_OPACITY));
        packedItems.add(SynchedEntityData.DataValue.create(DATA_STYLE_FLAGS, DEFAULT_STYLE_FLAGS));
    }

    @Override
    public void setLocation(Location location, Iterable<Player> receivers) {
        final Vec3 pos = new Vec3(location.getX(), location.getY() + MARKER_ARMOR_STAND_OFFSET, location.getZ());
        final PositionMoveRotation positionMoveRotation = new PositionMoveRotation(pos, Vec3.ZERO, 0f, 0f);
        final ClientboundEntityPositionSyncPacket positionPacket = new ClientboundEntityPositionSyncPacket(entityId,
                positionMoveRotation, false);
        sendPacket(positionPacket, receivers);
    }

}
