import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopContainer;
import de.epiceric.shopchest.shop.ShopProduct;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Temporary test plugin, built separately and never packaged in ShopChest. */
public final class ShopTransferSmoke extends JavaPlugin implements Listener {
    private ShopChest shopChest;
    private World world;
    private final List<CountingShop> shops = new ArrayList<>();
    private final List<Runnable> delayedChecks = new ArrayList<>();
    private final List<HopperMinecart> carts = new ArrayList<>();
    private int assertions;
    private int blockedEngineEvents;
    private int blockedMinecartEvents;
    private int cursor;
    private boolean dispatching;

    @Override
    public void onEnable() {
        shopChest = (ShopChest) getServer().getPluginManager().getPlugin("ShopChest");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("transfersmoke")).setExecutor((sender, command, label, args) -> {
            if (world != null) throw new IllegalStateException("Smoke already started");
            try { start(); } catch (Throwable error) { failed(error); }
            return true;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void countEngineEvent(InventoryMoveItemEvent event) {
        if (!dispatching && event.isCancelled()
                && (shopChest.getShopUtils().getShopByInventory(event.getSource()) != null
                || shopChest.getShopUtils().getShopByInventory(event.getDestination()) != null)) {
            blockedEngineEvents++;
            if (event.getInitiator().getHolder(false) instanceof HopperMinecart) blockedMinecartEvents++;
        }
    }

    private void start() {
        world = new WorldCreator("shopchest-transfer-smoke").type(WorldType.FLAT)
                .generateStructures(false).createWorld();
        require(world != null, "Temporary world available");
        Inventory hopper = container(Material.HOPPER, 0, 122);
        ItemStack heavy = heavyItem();
        for (Material material : ShopContainer.supportedMaterials()) {
            int x = nextX();
            Inventory inventory = container(material, x, 122);
            CountingShop shop = register(x, 122);
            inventory.setItem(0, heavy.clone());
            for (boolean initiates : List.of(true, false)) {
                checkEvent(inventory, hopper, initiates, true, heavy);
                checkEvent(hopper, inventory, initiates, true, heavy);
            }
            require(shop.refreshes == 0, material + " causes no refresh");
            shopChest.getShopUtils().removeShop(shop, false);
            checkEvent(inventory, hopper, false, false, heavy);
        }

        for (Material material : ShopContainer.supportedMaterials()) {
            if (!(material.createBlockData() instanceof Chest)) continue;
            int x = nextX();
            Inventory inventory = doubleChest(material, x);
            require(inventory instanceof DoubleChestInventory, material + " double inventory");
            inventory.setItem(0, heavy.clone());
            inventory.setItem(40, heavy.clone());
            // Real component-heavy ordinary double chest through all registered listeners.
            for (int i = 0; i < 20; i++) checkEvent(inventory, hopper, false, false, heavy);
            CountingShop shop = register(x + 1, 122);
            DoubleChestInventory doubleInventory = (DoubleChestInventory) inventory;
            for (Inventory endpoint : List.of(inventory, doubleInventory.getLeftSide(), doubleInventory.getRightSide())) {
                checkEvent(endpoint, hopper, false, true, heavy);
                checkEvent(hopper, endpoint, true, true, heavy);
            }
            shopChest.getShopUtils().removeShop(shop, false);
            checkEvent(inventory, hopper, false, false, heavy);
        }

        // Observe actual engine moves, including Paper's cancellation restoration.
        enginePair(true, true, heavy);
        enginePair(false, true, heavy);
        enginePair(false, false, heavy);
        engineMinecart(heavy);
        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                delayedChecks.forEach(Runnable::run);
                require(blockedEngineEvents > 0, "Real hopper engine raised blocked events");
                require(blockedMinecartEvents > 0, "Real minecart engine raised blocked events");
                require(shops.stream().allMatch(shop -> shop.refreshes == 0), "Blocked moves never refresh");
                cleanup();
                getLogger().info("TRANSFER_SMOKE_PASS assertions=" + assertions
                        + " blockedEngineEvents=" + blockedEngineEvents
                        + " blockedMinecartEvents=" + blockedMinecartEvents
                        + " transferRefreshes=0 periodicRefreshes=" + shops.stream().mapToInt(shop -> shop.periodicRefreshes).sum()
                        + " materials=28 doubleVariants=10");
            } catch (Throwable error) { failed(error); }
        }, 120L);
    }

    private void enginePair(boolean incoming, boolean registered, ItemStack heavy) {
        int x = nextX();
        Inventory chest = container(Material.CHEST, x, incoming ? 121 : 123);
        Inventory hopper = container(Material.HOPPER, x, 122);
        if (registered) register(x, incoming ? 121 : 123);
        Inventory source = incoming ? hopper : chest;
        Inventory destination = incoming ? chest : hopper;
        source.setItem(0, heavy.clone());
        ItemStack[] beforeSource = snapshot(source);
        ItemStack[] beforeDestination = snapshot(destination);
        delayedChecks.add(() -> {
            if (registered) {
                require(Arrays.equals(beforeSource, source.getContents()), "Engine blocked source counts/components");
                require(Arrays.equals(beforeDestination, destination.getContents()), "Engine blocked destination counts/components");
            } else {
                require(destination.getItem(0) != null, "Ordinary engine hopper moved items");
                require(count(source) + count(destination) == heavy.getAmount(), "Ordinary engine conserved item count");
                require(destination.getItem(0).isSimilar(heavy), "Ordinary engine preserved item components");
            }
        });
    }

    private void engineMinecart(ItemStack heavy) {
        int x = nextX();
        Inventory chest = container(Material.CHEST, x, 123);
        register(x, 123);
        chest.setItem(0, heavy.clone());
        world.getBlockAt(x, 121, 0).setType(Material.STONE, false);
        world.getBlockAt(x, 122, 0).setType(Material.RAIL, false);
        HopperMinecart cart = world.spawn(new Location(world, x + 0.5, 122.1, 0.5), HopperMinecart.class);
        cart.setGravity(false);
        carts.add(cart);
        ItemStack[] before = snapshot(chest);
        delayedChecks.add(() -> {
            require(Arrays.equals(before, chest.getContents()), "Minecart cannot extract shop stock/components");
            require(cart.getInventory().isEmpty(), "Minecart remains empty");
        });
    }

    private Inventory doubleChest(Material material, int x) {
        container(material, x, 122);
        container(material, x + 1, 122);
        for (int side = 0; side < 2; side++) {
            Chest data = (Chest) material.createBlockData();
            data.setFacing(org.bukkit.block.BlockFace.NORTH);
            data.setType(side == 0 ? Chest.Type.LEFT : Chest.Type.RIGHT);
            world.getBlockAt(x + side, 122, 0).setBlockData(data, false);
        }
        return ((Container) world.getBlockAt(x, 122, 0).getState(false)).getInventory();
    }

    private Inventory container(Material material, int x, int y) {
        world.setChunkForceLoaded(Math.floorDiv(x, 16), 0, true);
        world.getBlockAt(x, y, 0).setType(material, false);
        return ((Container) world.getBlockAt(x, y, 0).getState(false)).getInventory();
    }

    private CountingShop register(int x, int y) {
        CountingShop shop = new CountingShop(new Location(world, x, y, 0), -10_000 - shops.size());
        shops.add(shop);
        shopChest.getShopUtils().addShop(shop, false);
        return shop;
    }

    private void checkEvent(Inventory source, Inventory destination, boolean initiates,
                            boolean blocked, ItemStack heavy) {
        ItemStack[] beforeSource = snapshot(source), beforeDestination = snapshot(destination);
        ItemStack item = heavy.clone(), beforeItem = item.clone();
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, item, destination, initiates);
        dispatching = true;
        try { getServer().getPluginManager().callEvent(event); } finally { dispatching = false; }
        require(event.isCancelled() == blocked, "Expected cancellation=" + blocked);
        require(item.equals(beforeItem), "Event item unchanged");
        require(Arrays.equals(beforeSource, source.getContents()), "Source counts/components unchanged");
        require(Arrays.equals(beforeDestination, destination.getContents()), "Destination counts/components unchanged");
    }

    private ItemStack heavyItem() {
        ItemStack item = new ItemStack(Material.DIAMOND, 16);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Transfer regression item"));
            List<Component> lore = new ArrayList<>();
            for (int i = 0; i < 40; i++) lore.add(Component.text("Component fixture " + i + "x".repeat(80)));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(this, "payload"),
                    PersistentDataType.STRING, "metadata".repeat(512));
        });
        return item;
    }

    private ItemStack[] snapshot(Inventory inventory) {
        return Arrays.stream(inventory.getContents()).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private int count(Inventory inventory) {
        return Arrays.stream(inventory.getContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();
    }

    private int nextX() { return cursor += 4; }
    private void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private void failed(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE, "TRANSFER_SMOKE_FAIL", error);
        cleanup();
    }
    private void cleanup() {
        carts.forEach(HopperMinecart::remove);
        shops.forEach(shop -> shopChest.getShopUtils().removeShop(shop, false));
        if (world != null) {
            for (Chunk chunk : world.getForceLoadedChunks()) chunk.setForceLoaded(false);
            Bukkit.unloadWorld(world, false);
            world = null;
        }
    }
    @Override public void onDisable() { if (world != null) cleanup(); }

    private final class CountingShop extends Shop {
        int refreshes;
        int periodicRefreshes;
        CountingShop(Location location, int id) {
            super(id, shopChest, Bukkit.getOfflinePlayer(UUID.fromString("00000000-0000-0000-0009-01faeaf01111")),
                    new ShopProduct(new ItemStack(Material.DIAMOND)), location, 5, 2, ShopType.NORMAL);
        }
        @Override public void updateHologramText() {
            // The regular reconciliation sweep remains legitimate. This test detects
            // additional direct or scheduled refreshes caused by transfer events.
            boolean periodic = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals("de.epiceric.shopchest.utils.ShopUpdater")
                            && frame.getMethodName().equals("refreshDynamicShopText")));
            if (periodic) periodicRefreshes++; else refreshes++;
        }
    }
}
