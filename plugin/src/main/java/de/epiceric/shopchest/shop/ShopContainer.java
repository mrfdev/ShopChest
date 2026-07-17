package de.epiceric.shopchest.shop;

import de.epiceric.shopchest.ShopChest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves the explicitly supported shop blocks to their shared inventory,
 * physical block locations, and a stable display direction.
 */
public final class ShopContainer {

    private static final String DISPLAY_FACING_KEY = "display-facing";

    private static final Set<Material> SUPPORTED_MATERIALS = Collections.unmodifiableSet(EnumSet.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX,
            Material.COPPER_CHEST,
            Material.EXPOSED_COPPER_CHEST,
            Material.WEATHERED_COPPER_CHEST,
            Material.OXIDIZED_COPPER_CHEST,
            Material.WAXED_COPPER_CHEST,
            Material.WAXED_EXPOSED_COPPER_CHEST,
            Material.WAXED_WEATHERED_COPPER_CHEST,
            Material.WAXED_OXIDIZED_COPPER_CHEST
    ));

    private final InventoryHolder inventoryHolder;
    private final Set<Location> locations;
    private final BlockFace facing;

    private ShopContainer(InventoryHolder inventoryHolder, Set<Location> locations, BlockFace facing) {
        this.inventoryHolder = inventoryHolder;
        this.locations = locations;
        this.facing = facing;
    }

    public static boolean isSupported(Material material) {
        return SUPPORTED_MATERIALS.contains(material);
    }

    public static Set<Material> supportedMaterials() {
        return SUPPORTED_MATERIALS;
    }

    public static ShopContainer resolve(ShopChest plugin, Block block) {
        if (block == null || !isSupported(block.getType())) {
            return null;
        }

        BlockState state = block.getState();
        if (!(state instanceof Container container)) {
            return null;
        }

        InventoryHolder inventoryHolder = container.getInventory().getHolder();
        if (inventoryHolder == null) {
            inventoryHolder = container;
        }

        Set<Location> locations = locationsOf(inventoryHolder);
        if (locations.isEmpty()) {
            locations = Set.of(blockLocation(container.getLocation()));
        }

        return new ShopContainer(
                inventoryHolder,
                Collections.unmodifiableSet(new LinkedHashSet<>(locations)),
                resolveFacing(
                        directionalFacing(container.getBlockData()),
                        storedFacing(plugin, state)));
    }

    public static Set<Location> locationsOf(InventoryHolder inventoryHolder) {
        if (inventoryHolder == null) {
            return Set.of();
        }

        LinkedHashSet<Location> locations = new LinkedHashSet<>();
        if (inventoryHolder instanceof DoubleChest doubleChest) {
            addLocation(locations, doubleChest.getLeftSide());
            addLocation(locations, doubleChest.getRightSide());
        } else {
            addLocation(locations, inventoryHolder);
        }
        return Collections.unmodifiableSet(locations);
    }

    private static void addLocation(Set<Location> locations, InventoryHolder inventoryHolder) {
        if (inventoryHolder instanceof BlockState blockState && isSupported(blockState.getType())) {
            locations.add(blockLocation(blockState.getLocation()));
        }
    }

    private static Location blockLocation(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    private static BlockFace directionalFacing(BlockData blockData) {
        if (blockData instanceof Directional directional) {
            return directional.getFacing();
        }
        return BlockFace.SOUTH;
    }

    static BlockFace resolveFacing(BlockFace blockFacing, BlockFace storedFacing) {
        if (isHorizontal(blockFacing)) {
            return blockFacing;
        }
        return horizontalFacing(storedFacing);
    }

    static BlockFace horizontalFacing(BlockFace facing) {
        if (facing == null) {
            return BlockFace.SOUTH;
        }
        return switch (facing) {
            case NORTH, EAST, SOUTH, WEST -> facing;
            default -> BlockFace.SOUTH;
        };
    }

    private static boolean isHorizontal(BlockFace facing) {
        return facing == BlockFace.NORTH
                || facing == BlockFace.EAST
                || facing == BlockFace.SOUTH
                || facing == BlockFace.WEST;
    }

    private static BlockFace storedFacing(ShopChest plugin, BlockState state) {
        if (plugin == null || !(state instanceof TileState tileState)) {
            return null;
        }

        String stored = tileState.getPersistentDataContainer().get(
                new NamespacedKey(plugin, DISPLAY_FACING_KEY),
                PersistentDataType.STRING);
        if (stored == null) {
            return null;
        }

        try {
            return BlockFace.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Persists a horizontal display direction for containers whose native
     * facing is vertical, such as a shulker box placed on the floor.
     */
    public static void rememberVerticalDisplayFacing(ShopChest plugin, Block block, BlockFace facing) {
        if (plugin == null || block == null || !isSupported(block.getType())) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)
                || !(state.getBlockData() instanceof Directional directional)
                || isHorizontal(directional.getFacing())) {
            return;
        }

        tileState.getPersistentDataContainer().set(
                new NamespacedKey(plugin, DISPLAY_FACING_KEY),
                PersistentDataType.STRING,
                horizontalFacing(facing).name());
        tileState.update();
    }

    public Inventory getInventory() {
        return inventoryHolder.getInventory();
    }

    public InventoryHolder getInventoryHolder() {
        return inventoryHolder;
    }

    public Set<Location> getLocations() {
        return locations;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public boolean hasDisplaySpace() {
        return locations.stream()
                .map(Location::getBlock)
                .map(block -> block.getRelative(BlockFace.UP))
                .allMatch(block -> block.getType().isAir());
    }

    public Location getCenter() {
        Location first = locations.iterator().next();
        World world = first.getWorld();
        double x = locations.stream().mapToDouble(location -> location.getBlockX() + 0.5).average().orElse(first.getX());
        double y = locations.stream().mapToDouble(Location::getBlockY).average().orElse(first.getY());
        double z = locations.stream().mapToDouble(location -> location.getBlockZ() + 0.5).average().orElse(first.getZ());
        return new Location(world, x, y, z);
    }
}
