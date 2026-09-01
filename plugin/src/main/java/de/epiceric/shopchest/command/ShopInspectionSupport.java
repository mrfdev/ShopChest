package de.epiceric.shopchest.command;

import de.epiceric.shopchest.shop.Shop;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

final class ShopInspectionSupport {

    static final int TARGET_DISTANCE = 5;

    private ShopInspectionSupport() {
    }

    static void inspectOrSelect(
            Player player,
            Function<Location, Shop> shopLookup,
            BiConsumer<Player, Shop> inspector,
            Consumer<Player> selectionStarter
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(shopLookup, "shopLookup");
        Objects.requireNonNull(inspector, "inspector");
        Objects.requireNonNull(selectionStarter, "selectionStarter");

        final Block targetBlock = player.getTargetBlockExact(TARGET_DISTANCE);
        final Shop targetShop = targetBlock == null
                ? null
                : shopLookup.apply(targetBlock.getLocation());
        if (targetShop != null) {
            inspector.accept(player, targetShop);
            return;
        }
        selectionStarter.accept(player);
    }

    static boolean canViewShopId(UUID viewerId, UUID ownerId, boolean administrator) {
        return administrator || Objects.equals(viewerId, ownerId);
    }
}
