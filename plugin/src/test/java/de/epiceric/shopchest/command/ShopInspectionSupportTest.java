package de.epiceric.shopchest.command;

import de.epiceric.shopchest.shop.Shop;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopInspectionSupportTest {

    @Test
    void lookingAtAShopInspectsImmediatelyWithoutStartingSelection() {
        final Location location = new Location(null, 12, 64, -8);
        final Block block = blockAt(location);
        final AtomicInteger requestedDistance = new AtomicInteger();
        final Player player = playerLookingAt(block, requestedDistance);
        final Shop shop = shopAt(31, location);
        final AtomicReference<Shop> inspected = new AtomicReference<>();
        final AtomicInteger selections = new AtomicInteger();

        ShopInspectionSupport.inspectOrSelect(
                player,
                target -> {
                    assertSame(location, target);
                    return shop;
                },
                (viewer, target) -> {
                    assertSame(player, viewer);
                    inspected.set(target);
                },
                viewer -> selections.incrementAndGet());

        assertSame(shop, inspected.get());
        assertEquals(0, selections.get());
        assertEquals(ShopInspectionSupport.TARGET_DISTANCE, requestedDistance.get());
    }

    @Test
    void notLookingAtAShopStartsSelectionExactlyOnce() {
        final Player player = playerLookingAt(null, new AtomicInteger());
        final AtomicInteger inspections = new AtomicInteger();
        final AtomicInteger selections = new AtomicInteger();

        ShopInspectionSupport.inspectOrSelect(
                player,
                target -> null,
                (viewer, target) -> inspections.incrementAndGet(),
                viewer -> selections.incrementAndGet());

        assertEquals(0, inspections.get());
        assertEquals(1, selections.get());
    }

    @Test
    void onlyTheOwnerOrAnAdminCanSeeTheUniqueShopId() {
        final UUID ownerId = UUID.randomUUID();

        assertTrue(ShopInspectionSupport.canViewShopId(ownerId, ownerId, false));
        assertTrue(ShopInspectionSupport.canViewShopId(
                UUID.randomUUID(), ownerId, true));
        assertFalse(ShopInspectionSupport.canViewShopId(
                UUID.randomUUID(), ownerId, false));
    }

    private static Player playerLookingAt(Block block, AtomicInteger requestedDistance) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getTargetBlockExact")
                            && args != null
                            && args.length == 1) {
                        requestedDistance.set((Integer) args[0]);
                        return block;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static Block blockAt(Location location) {
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getLocation")
                            && (args == null || args.length == 0)) {
                        return location;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static Shop shopAt(int id, Location location) {
        return new Shop(
                id,
                null,
                null,
                null,
                location,
                0.0D,
                0.0D,
                Shop.ShopType.NORMAL);
    }
}
