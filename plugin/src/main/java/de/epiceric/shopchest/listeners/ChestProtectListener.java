package de.epiceric.shopchest.listeners;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.event.ShopExtendEvent;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.MessageRegistry;
import de.epiceric.shopchest.language.Replacement;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopContainer;
import de.epiceric.shopchest.shop.Shop.ShopType;
import de.epiceric.shopchest.utils.*;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

import java.util.ArrayList;
import java.util.Set;

public class ChestProtectListener implements Listener {

    private ShopChest plugin;
    private ShopUtils shopUtils;

    public ChestProtectListener(ShopChest plugin) {
        this.plugin = plugin;
        this.shopUtils = plugin.getShopUtils();
    }

    private void remove(final Shop shop, final Block b, final Player p) {
        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
        Set<Location> containerLocations = shop.getContainerLocations();
        if (containerLocations.size() > 1) {
            Location loc = containerLocations.stream()
                    .filter(location -> !location.equals(b.getLocation()))
                    .findFirst()
                    .orElse(null);
            if (loc == null) {
                plugin.debug("Could not find the remaining container block for shop #" + shop.getID());
                return;
            }
            final Shop newShop = new Shop(shop.getID(), plugin, shop.getVendor(), shop.getProduct(), loc, shop.getBuyPrice(), shop.getSellPrice(), shop.getShopType());

            shopUtils.replaceShopContainer(shop, newShop, Set.of(loc));
            return;
        }

        double creationPrice = shop.getShopType() == ShopType.ADMIN ? Config.shopCreationPriceAdmin : Config.shopCreationPriceNormal;
        if (creationPrice > 0 && Config.refundShopCreation && p.getUniqueId().equals(shop.getVendor().getUniqueId())) {
            EconomyResponse r = plugin.getEconomy().depositPlayer(p, shop.getLocation().getWorld().getName(), creationPrice);
            if (!r.transactionSuccess()) {
                plugin.debug("Economy transaction failed: " + r.errorMessage);
                p.sendMessage(messageRegistry.getMessage(Message.ERROR_OCCURRED,
                        new Replacement(Placeholder.ERROR, r.errorMessage)));
                p.sendMessage(messageRegistry.getMessage(Message.SHOP_REMOVED_REFUND,
                        new Replacement(Placeholder.CREATION_PRICE, 0)));
            } else {
                p.sendMessage(messageRegistry.getMessage(Message.SHOP_REMOVED_REFUND,
                        new Replacement(Placeholder.CREATION_PRICE, creationPrice)));
            }
        } else {
            p.sendMessage(messageRegistry.getMessage(Message.SHOP_REMOVED));
        }

        shopUtils.removeShop(shop, true);
        plugin.debug(String.format("%s broke %s's shop (#%d)", p.getName(), shop.getVendor().getName(), shop.getID()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        final Block b = e.getBlock();

        if (shopUtils.isShop(b.getLocation())) {
            final Shop shop = shopUtils.getShop(e.getBlock().getLocation());
            Player p = e.getPlayer();

            if (shop.hasId() && p.isSneaking() && Utils.hasAxeInHand(p)) {
                plugin.debug(String.format("%s tries to break %s's shop (#%d)", p.getName(), shop.getVendor().getName(), shop.getID()));

                if (shop.getShopType() == Shop.ShopType.ADMIN) {
                    if (p.hasPermission(Permissions.REMOVE_ADMIN)) {
                        return;
                    }
                } else {
                    if (shop.getVendor().getUniqueId().equals(p.getUniqueId()) || p.hasPermission(Permissions.REMOVE_OTHER)) {
                        return;
                    }
                }
            }

            if (shop.getItem() != null) {
                shop.getItem().resetForPlayer(p);
            }

            e.setCancelled(true);
            final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();
            e.getPlayer().sendMessage(messageRegistry.getMessage(Message.CANNOT_BREAK_SHOP));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopBlockBroken(BlockBreakEvent event) {
        Shop shop = shopUtils.getShop(event.getBlock().getLocation());
        if (shop != null) remove(shop, event.getBlock(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        ArrayList<Block> bl = new ArrayList<>(e.blockList());
        for (Block b : bl) {
            if (ShopContainer.isSupported(b.getType())) {
                if (shopUtils.isShop(b.getLocation())) e.blockList().remove(b);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        final Player p = e.getPlayer();
        final Block b = e.getBlockPlaced();

        if (!ShopContainer.isSupported(b.getType())
                || !(b.getBlockData() instanceof org.bukkit.block.data.type.Chest data)) {
            return;
        }

        // Can't use Utils::getChestLocations since inventory holder
        // has not been updated yet in this event (for 1.13+)

        if (data.getType() == Type.SINGLE) {
            return;
        }

        final Shop shop = shopUtils.getShop(b.getRelative(connectedSide(data)).getLocation());
        if (shop == null)
            return;
        if (!shop.hasId()) {
            e.setCancelled(true);
            return;
        }

        plugin.debug(String.format("%s tries to extend %s's shop (#%d)", p.getName(), shop.getVendor().getName(), shop.getID()));

        ShopExtendEvent event = new ShopExtendEvent(p, shop, b.getLocation());
        Bukkit.getPluginManager().callEvent(event);

        final MessageRegistry messageRegistry = plugin.getLanguageManager().getMessageRegistry();

        if (event.isCancelled() && !p.hasPermission(Permissions.EXTEND_PROTECTED)) {
            e.setCancelled(true);
            p.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_EXTEND_PROTECTED));
            return;
        }

        if (!p.getUniqueId().equals(shop.getVendor().getUniqueId()) && !p.hasPermission(Permissions.EXTEND_OTHER)) {
            e.setCancelled(true);
            p.sendMessage(messageRegistry.getMessage(Message.NO_PERMISSION_EXTEND_OTHERS));
            return;
        }

        ShopContainer container = ShopContainer.resolve(plugin, b);
        if (container == null || !container.hasDisplaySpace()) {
            e.setCancelled(true);
            p.sendMessage(messageRegistry.getMessage(Message.CHEST_BLOCKED));
            return;
        }

        // Commit membership only after every protection listener has accepted placement.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopBlockPlaced(BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        if (!ShopContainer.isSupported(block.getType())
                || !(block.getBlockData() instanceof org.bukkit.block.data.type.Chest data)
                || data.getType() == Type.SINGLE) return;
        Shop shop = shopUtils.getShop(block.getRelative(connectedSide(data)).getLocation());
        if (shop == null) return;
        Shop replacement = new Shop(shop.getID(), plugin, shop.getVendor(), shop.getProduct(),
                shop.getLocation(), shop.getBuyPrice(), shop.getSellPrice(), shop.getShopType());
        shopUtils.replaceShopContainer(shop, replacement, Set.of(shop.getLocation(), block.getLocation()));
    }

    private static BlockFace connectedSide(org.bukkit.block.data.type.Chest data) {
        return switch (data.getFacing()) {
            case NORTH -> data.getType() == Type.LEFT ? BlockFace.EAST : BlockFace.WEST;
            case EAST -> data.getType() == Type.LEFT ? BlockFace.SOUTH : BlockFace.NORTH;
            case SOUTH -> data.getType() == Type.LEFT ? BlockFace.WEST : BlockFace.EAST;
            case WEST -> data.getType() == Type.LEFT ? BlockFace.NORTH : BlockFace.SOUTH;
            default -> throw new IllegalStateException("Unsupported chest facing: " + data.getFacing());
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent e) {
        if (shopUtils.getShopByInventory(e.getSource()) != null
                || shopUtils.getShopByInventory(e.getDestination()) != null) {
            // Let Paper restore the source item. Never read or replace the event item.
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(InventoryPickupItemEvent e) {
        if (shopUtils.getShopByInventory(e.getInventory()) != null) {
            e.setCancelled(true);
        }
    }

}
