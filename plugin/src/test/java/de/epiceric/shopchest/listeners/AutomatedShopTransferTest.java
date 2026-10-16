package de.epiceric.shopchest.listeners;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.hologram.HologramFormat;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopContainer;
import de.epiceric.shopchest.utils.ShopUtils;
import de.epiceric.shopchest.sql.Database;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Dispatches real events through both real listeners, using the real shop registry. */
class AutomatedShopTransferTest {
    private ShopChest plugin;
    private ShopUtils shops;
    private World world;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<RegistryAccess> registries;
    private List<Listener> listeners;
    private BukkitScheduler scheduler;
    private Database database;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        // InventoryType's menu constants need Paper's runtime registry at initialization.
        registries = mockStatic(RegistryAccess.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        RegistryAccess registryAccess = mock(RegistryAccess.class, invocation -> mock(Registry.class, lookup -> {
            if (lookup.getMethod().getName().equals("get") || lookup.getMethod().getName().equals("getOrThrow")) {
                return mock(MenuType.Typed.class, RETURNS_SELF);
            }
            return RETURNS_DEFAULTS.answer(lookup);
        }));
        registries.when(RegistryAccess::registryAccess).thenReturn(registryAccess);
        world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        plugin = mock(ShopChest.class);
        shops = new ShopUtils(plugin);
        when(plugin.getShopUtils()).thenReturn(shops);
        database = mock(Database.class);
        when(plugin.getShopDatabase()).thenReturn(database);
        HologramFormat format = mock(HologramFormat.class);
        when(format.isDynamic()).thenReturn(true);
        when(plugin.getHologramFormat()).thenReturn(format);
        scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        listeners = List.of(new ChestProtectListener(plugin), new ShopUpdateListener(plugin));
    }

    @AfterEach
    void tearDown() {
        registries.close();
        bukkit.close();
    }

    @Test
    void ordinaryTransfersNeverResolveSnapshotHolders() throws Exception {
        Inventory source = inventory(0, InventoryType.CHEST);
        Inventory destination = inventory(2, InventoryType.HOPPER);
        for (int i = 0; i < 100; i++) {
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, mock(ItemStack.class), destination, false);
            dispatch(event);
            assertFalse(event.isCancelled());
        }
        verify(source, never()).getHolder();
        verify(destination, never()).getHolder();
    }

    @Test
    void incomingAutomatedRestockIsCancelled() throws Exception {
        Inventory source = inventory(0, InventoryType.HOPPER);
        Inventory destination = inventory(2, InventoryType.CHEST);
        register(new Location(world, 2, 64, 0));
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, mock(ItemStack.class), destination, true);
        dispatch(event);
        assertTrue(event.isCancelled());
    }

    static Stream<Material> containers() {
        return ShopContainer.supportedMaterials().stream();
    }

    @ParameterizedTest
    @MethodSource("containers")
    void everySupportedContainerBlocksBothDirectionsAndInitiators(Material material) throws Exception {
        Inventory container = inventory(0, InventoryType.CHEST);
        Container holder = mock(Container.class);
        when(holder.getType()).thenReturn(material);
        when(container.getHolder(false)).thenReturn(holder);
        Shop shop = register(new Location(world, 0, 64, 0));
        // Even a PLAYER initiator from another plugin does not bypass automation protection.
        for (InventoryType type : List.of(InventoryType.HOPPER, InventoryType.DROPPER, InventoryType.PLAYER)) {
            Inventory other = inventory(2, type);
            for (boolean sourceInitiates : List.of(true, false)) {
                assertBlocked(container, other, sourceInitiates);
                assertBlocked(other, container, sourceInitiates);
            }
        }
        verify(shop, never()).getVendor();
        verify(shop, never()).updateHologramText();
        verifyNoInteractions(scheduler);
    }

    @Test
    void eitherDoubleChestHalfIsSufficientIncludingNegativeCoordinates() throws Exception {
        Inventory left = inventory(-2, InventoryType.CHEST);
        Inventory right = inventory(-1, InventoryType.CHEST);
        DoubleChestInventory doubleChest = doubleChest(left, right);
        Inventory hopper = inventory(2, InventoryType.HOPPER);
        for (int x : List.of(-2, -1)) {
            Shop shop = register(new Location(world, x, 64, 0));
            assertBlocked(doubleChest, hopper, false);
            assertBlocked(hopper, doubleChest, true);
            assertSnapshotFree(doubleChest, left, right);
            verify(doubleChest, never()).getLocation(); // The midpoint can round to the wrong half.
            shops.removeShop(shop, false);
        }
    }

    @Test
    void repeatedOrdinaryDoubleTransfersUseConstantCoordinateWorkWithManyShops() throws Exception {
        for (int x = 10; x < 1_010; x++) register(new Location(world, x, 64, 0));
        Inventory left = inventory(0, InventoryType.CHEST);
        Inventory right = inventory(1, InventoryType.CHEST);
        DoubleChestInventory doubleChest = doubleChest(left, right);
        Inventory hopper = inventory(2, InventoryType.HOPPER);
        for (int i = 0; i < 1_000; i++) {
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(doubleChest, mock(ItemStack.class), hopper, false);
            dispatch(event);
            assertFalse(event.isCancelled());
        }
        verify(left, times(1_000)).getLocation();
        verify(right, times(1_000)).getLocation();
        verify(hopper, times(1_000)).getLocation();
        assertSnapshotFree(doubleChest, left, right, hopper);
        verifyNoInteractions(scheduler);
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void minecartTransfersAreBlockedAtShopEndpointsButNotByCoincidentalPosition() throws Exception {
        Inventory shopInventory = inventory(0, InventoryType.BARREL);
        register(new Location(world, 0, 64, 0));
        Inventory minecart = inventory(2, InventoryType.HOPPER);
        when(minecart.getHolder(false)).thenReturn(mock(HopperMinecart.class));
        assertBlocked(shopInventory, minecart, false);
        assertBlocked(minecart, shopInventory, true);
        when(minecart.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        Inventory ordinary = inventory(4, InventoryType.CHEST);
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(minecart, mock(ItemStack.class), ordinary, true);
        dispatch(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void cancelledAndLocationlessEventsDoNoExtraWork() throws Exception {
        Inventory source = mock(Inventory.class);
        Inventory destination = mock(Inventory.class);
        InventoryMoveItemEvent cancelled = new InventoryMoveItemEvent(source, mock(ItemStack.class), destination, false);
        cancelled.setCancelled(true);
        dispatch(cancelled);
        verifyNoInteractions(source, destination, scheduler);
        InventoryMoveItemEvent unknown = new InventoryMoveItemEvent(source, mock(ItemStack.class), destination, true);
        dispatch(unknown);
        assertFalse(unknown.isCancelled());
        verify(source, never()).getHolder(false);
        verify(destination, never()).getHolder(false);
    }

    @Test
    void removalChunkUnloadAndShutdownUseTheAuthoritativeRegistry() throws Exception {
        Inventory left = inventory(0, InventoryType.CHEST);
        Inventory right = inventory(1, InventoryType.CHEST);
        Inventory hopper = inventory(2, InventoryType.HOPPER);
        Shop shop = register(left.getLocation(), right.getLocation());
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        shops.unloadDisplays(chunk);
        assertBlocked(left, hopper, false);
        assertBlocked(right, hopper, false);
        verify(shop).unloadDisplays();
        // Even a record without created displays must be evicted.
        when(shop.isCreated()).thenReturn(false);
        shops.removeShop(shop, false);
        for (Inventory half : List.of(left, right)) {
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(half, mock(ItemStack.class), hopper, false);
            dispatch(event);
            assertFalse(event.isCancelled());
        }
        register(left.getLocation(), right.getLocation());
        shops.removeShops();
        assertNull(shops.getShopByInventory(left));
        assertNull(shops.getShopByInventory(right));
    }

    @Test
    void pickupEventsCannotRestockShopsAndNeverScheduleARefresh() throws Exception {
        Inventory shopInventory = inventory(0, InventoryType.CHEST);
        register(shopInventory.getLocation());
        Item item = mock(Item.class);
        InventoryPickupItemEvent event = new InventoryPickupItemEvent(shopInventory, item);
        dispatch(event);
        assertTrue(event.isCancelled());
        InventoryPickupItemEvent ordinary = new InventoryPickupItemEvent(inventory(2, InventoryType.HOPPER), item);
        dispatch(ordinary);
        assertFalse(ordinary.isCancelled());
        verifyNoInteractions(item, scheduler);
    }

    @Test
    void manualClicksDragsAndClosingStillScheduleTheRealShopRefresh() throws Exception {
        listeners = List.of(new ShopInteractListener(plugin));
        Inventory inventory = inventory(0, InventoryType.BARREL);
        Shop shop = register(inventory.getLocation());
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(click.getView()).thenReturn(view);
        when(drag.getView()).thenReturn(view);
        when(close.getView()).thenReturn(view);
        doAnswer(invocation -> { invocation.<Runnable>getArgument(1).run(); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        dispatch(click);
        dispatch(drag);
        dispatch(close);
        verify(shop, times(3)).updateHologramText();
    }

    @Test
    void extendingAndSplittingHaveNoProtectionGapOrStaleRemovedHalf() throws Exception {
        Inventory left = inventory(0, InventoryType.CHEST);
        Inventory right = inventory(1, InventoryType.CHEST);
        Inventory hopper = inventory(2, InventoryType.HOPPER);
        List<Runnable> displayTasks = new ArrayList<>();
        doAnswer(invocation -> { displayTasks.add(invocation.getArgument(1)); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        Shop original = register(left.getLocation());
        Shop extended = mock(Shop.class);
        doReturn(left.getLocation()).when(extended).getLocation();
        shops.replaceShopContainer(original, extended, Set.of(left.getLocation(), right.getLocation()));
        assertBlocked(left, hopper, false);
        assertBlocked(right, hopper, false);
        verifyNoInteractions(database); // Same anchor needs no persistence work.

        Shop remaining = mock(Shop.class);
        doReturn(right.getLocation()).when(remaining).getLocation();
        shops.replaceShopContainer(extended, remaining, Set.of(right.getLocation()));
        assertBlocked(right, hopper, false);
        assertNull(shops.getShopByInventory(left));
        verify(database).moveShop(eq(0), eq(new Location(world, 1, 64, 0)), any());
        displayTasks.getFirst().run();
        verify(extended, never()).create(anyBoolean());
        // Removal/shutdown before the pending display task cannot revive protection.
        shops.removeShops();
        displayTasks.getLast().run();
        verify(remaining, never()).create(anyBoolean());
        assertNull(shops.getShopByInventory(right));
    }

    private void assertBlocked(Inventory source, Inventory destination, boolean sourceInitiates) throws Exception {
        ItemStack item = mock(ItemStack.class);
        InventoryMoveItemEvent event = spy(new InventoryMoveItemEvent(source, item, destination, sourceInitiates));
        dispatch(event);
        assertTrue(event.isCancelled());
        verify(event, never()).getItem();
        verify(event, never()).setItem(any());
        verifyNoInteractions(item);
        for (Inventory inventory : List.of(source, destination)) {
            verify(inventory, never()).getContents();
            verify(inventory, never()).setItem(anyInt(), any());
            verify(inventory, never()).setContents(any());
        }
    }

    private DoubleChestInventory doubleChest(Inventory left, Inventory right) {
        DoubleChestInventory inventory = mock(DoubleChestInventory.class);
        when(inventory.getLeftSide()).thenReturn(left);
        when(inventory.getRightSide()).thenReturn(right);
        return inventory;
    }

    private void assertSnapshotFree(Inventory... inventories) {
        for (Inventory inventory : inventories) {
            verify(inventory, never()).getHolder();
            verify(inventory, never()).getHolder(true);
            verify(inventory, never()).getContents();
            verify(inventory, never()).getStorageContents();
        }
    }

    private Shop register(Location... locations) {
        Shop shop = mock(Shop.class);
        when(shop.getContainerLocations()).thenReturn(Set.of(locations));
        when(shop.getLocation()).thenReturn(locations[0]);
        when(shop.isCreated()).thenReturn(true);
        shops.addShop(shop, false);
        return shop;
    }

    private Inventory inventory(int x, InventoryType type) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getType()).thenReturn(type);
        when(inventory.getLocation()).thenReturn(new Location(world, x, 64, 0));
        when(inventory.getHolder(false)).thenReturn(mock(BlockInventoryHolder.class));
        return inventory;
    }

    private void dispatch(Event event) throws Exception {
        record Handler(Listener listener, Method method, EventHandler annotation) {}
        List<Handler> handlers = new ArrayList<>();
        for (Listener listener : listeners) {
            for (Method method : listener.getClass().getMethods()) {
                EventHandler annotation = method.getAnnotation(EventHandler.class);
                if (annotation != null && method.getParameterTypes()[0].isInstance(event)) {
                    handlers.add(new Handler(listener, method, annotation));
                }
            }
        }
        handlers.sort(Comparator.comparing(h -> h.annotation().priority()));
        for (Handler handler : handlers) {
            if (handler.annotation().ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.isCancelled()) continue;
            handler.method().invoke(handler.listener(), event);
        }
    }
}
