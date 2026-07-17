package de.epiceric.shopchest.shop;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.config.hologram.HologramColorPalette;
import de.epiceric.shopchest.config.hologram.HologramFormat;
import de.epiceric.shopchest.config.hologram.HologramItemDetails;
import de.epiceric.shopchest.config.hologram.HologramTradeAvailability;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.Replacement;
import de.epiceric.shopchest.exceptions.ChestNotFoundException;
import de.epiceric.shopchest.exceptions.NotEnoughSpaceException;
import de.epiceric.shopchest.display.Hologram;
import de.epiceric.shopchest.display.HologramOrientation;
import de.epiceric.shopchest.display.HologramTextFormatter;
import de.epiceric.shopchest.utils.ChunkCoordinates;
import de.epiceric.shopchest.utils.ItemUtils;
import de.epiceric.shopchest.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Shop {

    public enum ShopType {
        NORMAL,
        ADMIN,
    }

    private static class PreCreateResult {
        private final Inventory inventory;
        private final Chest[] chests;
        private final BlockFace face;

        private PreCreateResult(Inventory inventory, Chest[] chests, BlockFace face) {
            this.inventory = inventory;
            this.chests = chests;
            this.face = face;
        }
    }

    private final ShopChest plugin;
    private final OfflinePlayer vendor;
    private final ShopProduct product;
    private final Location location;
    private final double buyPrice;
    private final double sellPrice;
    private final ShopType shopType;

    private boolean created;
    private boolean creationQueued;
    private int id;
    private Hologram hologram;
    private Location holoLocation;
    private ShopItem item;

    public Shop(int id, ShopChest plugin, OfflinePlayer vendor, ShopProduct product, Location location, double buyPrice, double sellPrice, ShopType shopType) {
        this.id = id;
        this.plugin = plugin;
        this.vendor = vendor;
        this.product = product;
        this.location = location;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.shopType = shopType;
    }

    public Shop(ShopChest plugin, OfflinePlayer vendor, ShopProduct product, Location location, double buyPrice, double sellPrice, ShopType shopType) {
        this(-1, plugin, vendor, product, location, buyPrice, sellPrice, shopType);
    }

    /**
     * Test if this shop is equals to another
     *
     * @param o Other object to test against
     * @return true if we are sure they are the same, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Shop shop = (Shop) o;

        // id = -1 means temp shop
        return id != -1 && id == shop.id;
    }

    @Override
    public int hashCode() {
        return id != -1 ? id : super.hashCode();
    }

    /**
     * Create the shop
     *
     * @param showConsoleMessages to log exceptions to console
     * @return Whether is was created or not
     */
    public synchronized boolean create(boolean showConsoleMessages) {
        if (created || creationQueued) return false;

        if (!Bukkit.isPrimaryThread()) {
            creationQueued = true;
            Bukkit.getScheduler().runTask(plugin, () -> {
                synchronized (Shop.this) {
                    creationQueued = false;
                }
                create(showConsoleMessages);
            });
            return true;
        }

        plugin.debug("Creating shop (#" + id + ")");

        Block b = location.getBlock();
        if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST) {
            ChestNotFoundException ex = new ChestNotFoundException(String.format("No Chest found in world '%s' at location: %d; %d; %d",
                    b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
            plugin.getShopUtils().removeShop(this, Config.removeShopOnError);
            if (showConsoleMessages) plugin.getLogger().severe(ex.getMessage());
            plugin.debug("Failed to create shop (#" + id + ")");
            plugin.debug(ex);
            return false;
        } else if ((!ItemUtils.isAir(b.getRelative(BlockFace.UP).getType()))) {
            NotEnoughSpaceException ex = new NotEnoughSpaceException(String.format("No space above chest in world '%s' at location: %d; %d; %d",
                    b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
            plugin.getShopUtils().removeShop(this, Config.removeShopOnError);
            if (showConsoleMessages) plugin.getLogger().severe(ex.getMessage());
            plugin.debug("Failed to create shop (#" + id + ")");
            plugin.debug(ex);
            return false;
        }

        PreCreateResult preResult = preCreateHologram();

        if (preResult == null) {
            return false;
        }

        if (hologram == null || !hologram.exists()) createHologram(preResult);
        if (item == null) createItem();

        queueDisplayRefresh();

        created = true;
        return true;
    }

    /**
     * Removes the hologram of the shop
     */
    public void removeHologram() {
        if (hologram != null) {
            plugin.debug("Removing hologram (#" + id + ")");
            hologram.remove();
            hologram = null;
        }
    }

    /**
     * Removes the floating item of the shop
     */
    public void removeItem() {
        if (item != null) {
            plugin.debug("Removing shop item (#" + id + ")");
            item.remove();
            item = null;
        }
    }

    /**
     * <p>Creates the floating item of the shop</p>
     * <b>Call this after {@link #createHologram()}, because it depends on the hologram's location</b>
     */
    private void createItem() {
        plugin.debug("Creating item (#" + id + ")");

        Location itemLocation;

        itemLocation = holoLocation.clone();
        itemLocation.setY(location.getY() + 1.15);
        item = new ShopItem(plugin, product.getItemStack(), itemLocation);
    }

    /**
     * Recreates display entities that were discarded when their chunk unloaded.
     *
     * @return whether either display entity was recreated
     */
    public synchronized boolean restoreDisplays() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::restoreDisplays);
            return true;
        }

        final World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(
                ChunkCoordinates.fromBlock(location.getBlockX()),
                ChunkCoordinates.fromBlock(location.getBlockZ()))) {
            return false;
        }

        final boolean restoreHologram = hologram == null || !hologram.exists();
        final boolean restoreItem = item == null || !item.exists();
        if (!restoreHologram && !restoreItem) {
            return false;
        }

        final PreCreateResult preResult = preCreateHologram();
        if (preResult == null) {
            return false;
        }

        if (restoreHologram) {
            removeHologram();
            createHologram(preResult);
        } else {
            holoLocation = getHologramLocation(preResult.chests, preResult.face);
        }
        if (restoreItem) {
            removeItem();
            createItem();
        }

        queueDisplayRefresh();
        return true;
    }

    /**
     * Removes transient displays while retaining the registered shop record.
     */
    public synchronized void unloadDisplays() {
        removeItem();
        removeHologram();
    }

    private void queueDisplayRefresh() {
        plugin.getUpdater().queue(() -> {
            for (Player player : location.getWorld().getPlayers()) {
                plugin.getShopUtils().resetPlayerLocation(player);
            }
        });
        plugin.getUpdater().updateShops(location.getWorld());
    }

    /**
     * Runs everything that needs to be called synchronously in order 
     * to prepare creating the hologram.
     */
    private PreCreateResult preCreateHologram() {
        plugin.debug("Creating hologram (#" + id + ")");

        InventoryHolder ih = getInventoryHolder();

        if (ih == null) return null;

        Chest[] chests = new Chest[2];
        BlockFace face;

        if (ih instanceof DoubleChest) {
            DoubleChest dc = (DoubleChest) ih;
            Chest r = (Chest) dc.getRightSide();
            Chest l = (Chest) dc.getLeftSide();

            chests[0] = r;
            chests[1] = l;
        } else {
            chests[0] = (Chest) ih;
        }

        face = ((Directional) chests[0].getBlockData()).getFacing();

        return new PreCreateResult(ih.getInventory(), chests, face);
    }

    /**
     * Creates the hologram on the server thread.
     */
    private void createHologram(PreCreateResult preResult) {
        Component[] holoText = getHologramText(preResult.inventory);
        holoLocation = getHologramLocation(preResult.chests, preResult.face);
        hologram = new Hologram(plugin, holoText, holoLocation);
    }

    /**
     * Keep hologram text up to date.
     * <p><b>Has to be called synchronously!</b></p>
     */
    public void updateHologramText() {
        if (hologram == null || !hologram.exists()) return;

        final InventoryHolder inventoryHolder = getInventoryHolder();
        if (inventoryHolder == null) return;

        Component[] lines = getHologramText(inventoryHolder.getInventory());
        hologram.setLines(lines);
    }

    /**
     * Recalculate and move the hologram without recreating the shop item.
     * <p><b>Has to be called synchronously!</b></p>
     */
    public void updateHologramLocation() {
        if (hologram == null || !hologram.exists()) return;

        PreCreateResult preResult = preCreateHologram();
        if (preResult == null) return;

        holoLocation = getHologramLocation(preResult.chests, preResult.face);
        hologram.setLocation(holoLocation);
    }

    private Component[] getHologramText(Inventory inventory) {
        List<Component> lines = new ArrayList<>();

        ItemStack itemStack = getProduct().getItemStack();
        final HologramItemDetails itemDetails = HologramItemDetails.from(
                itemStack,
                Config.hologramColors.textColor(HologramColorPalette.Role.DETAILS),
                Config.hologramColors.textColor(HologramColorPalette.Role.SEPARATOR));
        final java.util.function.IntFunction<Component> overflowFactory = hiddenCount ->
                HologramTextFormatter.fromLegacy(plugin.getLanguageManager().getMessageRegistry().getMessage(
                                Message.HOLOGRAM_MORE_ITEM_DETAILS,
                                new Replacement(Placeholder.DETAIL_COUNT, hiddenCount)))
                        .color(Config.hologramColors.textColor(HologramColorPalette.Role.SEPARATOR));
        final Component enchantmentDetails = itemDetails.enchantments(
                Config.hologramMaxItemDetailEntries,
                Config.hologramItemDetailsPerLine,
                overflowFactory);
        final Component potionDetails = itemDetails.potionEffects(
                Config.hologramMaxItemDetailEntries,
                Config.hologramItemDetailsPerLine,
                overflowFactory);
        final Component combinedItemDetails = itemDetails.combined(
                Config.hologramMaxItemDetailEntries,
                Config.hologramItemDetailsPerLine,
                overflowFactory);
        final Component itemName = HologramTextFormatter.sanitizeItemName(
                        getProduct().getLocalizedNameComponent(),
                        Config.hologramMaxItemNameLength)
                .applyFallbackStyle(Style.style(
                        Config.hologramColors.textColor(HologramColorPalette.Role.ITEM)));

        // Create requirements base on the shop value
        // (As requirements are always the same, only set requirements to the shop value)
        Map<HologramFormat.Requirement, Object> requirements = new EnumMap<>(HologramFormat.Requirement.class);
        final int damage = ItemUtils.getDamage(itemStack);
        final int stock = Utils.getAmount(inventory, itemStack);
        final int chestSpace = Utils.getFreeSpaceForItem(inventory, itemStack);
        final boolean buyOutOfStock = HologramTradeAvailability.isBuyOutOfStock(
                getBuyPrice(), getShopType() == ShopType.ADMIN, stock, getProduct().getAmount());
        requirements.put(HologramFormat.Requirement.VENDOR, getVendor().getName());
        requirements.put(HologramFormat.Requirement.AMOUNT, getProduct().getAmount());
        requirements.put(HologramFormat.Requirement.ITEM_TYPE, itemStack.getType() + (damage > 0 ? ":" + damage : ""));
        requirements.put(HologramFormat.Requirement.ITEM_NAME, getLegacyDisplayName(itemStack));
        requirements.put(HologramFormat.Requirement.HAS_ENCHANTMENT, itemDetails.hasEnchantments());
        requirements.put(HologramFormat.Requirement.HAS_ITEM_DETAILS, !itemDetails.isEmpty());
        requirements.put(HologramFormat.Requirement.BUY_PRICE, getBuyPrice());
        requirements.put(HologramFormat.Requirement.SELL_PRICE, getSellPrice());
        requirements.put(HologramFormat.Requirement.HAS_POTION_EFFECT, itemDetails.hasPotionEffects());
        requirements.put(HologramFormat.Requirement.IS_MUSIC_DISC, itemStack.getType().isRecord());
        requirements.put(HologramFormat.Requirement.IS_POTION_EXTENDED, ItemUtils.isExtendedPotion(itemStack));
        requirements.put(HologramFormat.Requirement.IS_WRITTEN_BOOK, itemStack.getType() == Material.WRITTEN_BOOK);
        requirements.put(HologramFormat.Requirement.IS_BANNER_PATTERN, ItemUtils.isBannerPattern(itemStack));
        requirements.put(HologramFormat.Requirement.ADMIN_SHOP, getShopType() == ShopType.ADMIN);
        requirements.put(HologramFormat.Requirement.NORMAL_SHOP, getShopType() == ShopType.NORMAL);
        requirements.put(HologramFormat.Requirement.IN_STOCK, stock);
        requirements.put(HologramFormat.Requirement.OUT_OF_STOCK, buyOutOfStock);
        requirements.put(HologramFormat.Requirement.MAX_STACK, itemStack.getMaxStackSize());
        requirements.put(HologramFormat.Requirement.CHEST_SPACE, chestSpace);
        requirements.put(HologramFormat.Requirement.DURABILITY, damage);

        // Same as requirements
        Map<Placeholder, Object> placeholders = new EnumMap<>(Placeholder.class);
        placeholders.put(Placeholder.VENDOR, getVendor().getName());
        placeholders.put(Placeholder.AMOUNT, getProduct().getAmount());
        placeholders.put(Placeholder.ITEM_NAME, Placeholder.ITEM_NAME.toString());
        placeholders.put(Placeholder.ENCHANTMENT, Placeholder.ENCHANTMENT.toString());
        placeholders.put(Placeholder.ITEM_DETAILS, Placeholder.ITEM_DETAILS.toString());
        placeholders.put(Placeholder.BUY_PRICE, getBuyPrice());
        placeholders.put(Placeholder.SELL_PRICE, getSellPrice());
        placeholders.put(Placeholder.POTION_EFFECT, Placeholder.POTION_EFFECT.toString());
        /*
        placeholders.put(Placeholder.MUSIC_TITLE, LanguageUtils.getMusicDiscName(itemStack.getType()));
        placeholders.put(Placeholder.BANNER_PATTERN_NAME, LanguageUtils.getBannerPatternName(itemStack.getType()));
        placeholders.put(Placeholder.GENERATION, LanguageUtils.getBookGenerationName(itemStack));
        */
        placeholders.put(Placeholder.STOCK, stock);
        placeholders.put(Placeholder.MAX_STACK, itemStack.getMaxStackSize());
        placeholders.put(Placeholder.CHEST_SPACE, chestSpace);
        placeholders.put(Placeholder.DURABILITY, damage);
        placeholders.put(Placeholder.COLOR_OWNER, Config.hologramColors.color(HologramColorPalette.Role.OWNER));
        placeholders.put(Placeholder.COLOR_QUANTITY, Config.hologramColors.color(HologramColorPalette.Role.QUANTITY));
        placeholders.put(Placeholder.COLOR_ITEM, Config.hologramColors.color(HologramColorPalette.Role.ITEM));
        placeholders.put(Placeholder.COLOR_LABEL, Config.hologramColors.color(HologramColorPalette.Role.LABEL));
        placeholders.put(Placeholder.COLOR_BUY_VALUE, Config.hologramColors.color(HologramColorPalette.Role.BUY_VALUE));
        placeholders.put(Placeholder.COLOR_SELL_VALUE, Config.hologramColors.color(HologramColorPalette.Role.SELL_VALUE));
        placeholders.put(Placeholder.COLOR_SEPARATOR, Config.hologramColors.color(HologramColorPalette.Role.SEPARATOR));
        placeholders.put(Placeholder.COLOR_ADMIN, Config.hologramColors.color(HologramColorPalette.Role.ADMIN));
        placeholders.put(Placeholder.COLOR_UNAVAILABLE, Config.hologramColors.color(HologramColorPalette.Role.UNAVAILABLE));
        placeholders.put(Placeholder.COLOR_RESET, HologramColorPalette.RESET);
        final Map<String, Component> componentReplacements = Map.of(
                Placeholder.ITEM_NAME.toString(), itemName,
                Placeholder.ENCHANTMENT.toString(), enchantmentDetails,
                Placeholder.POTION_EFFECT.toString(), potionDetails,
                Placeholder.ITEM_DETAILS.toString(), combinedItemDetails);

        int lineCount = plugin.getHologramFormat().getLineCount();

        for (int i = 0; i < lineCount; i++) {
            String format = plugin.getHologramFormat().getFormat(i, requirements, placeholders);
            for (Placeholder placeholder : placeholders.keySet()) {
                String replace;

                switch (placeholder) {
                    case BUY_PRICE:
                        replace = HologramTradeAvailability.formatBuyValue(
                                plugin.getEconomy().format(getBuyPrice()),
                                buyOutOfStock,
                                plugin.getLanguageManager().getMessageRegistry()
                                        .getMessage(Message.HOLOGRAM_OUT_OF_STOCK),
                                Config.hologramColors.color(HologramColorPalette.Role.UNAVAILABLE));
                        break;
                    case SELL_PRICE:
                        replace = plugin.getEconomy().format(getSellPrice());
                        break;
                    default:
                        replace = String.valueOf(placeholders.get(placeholder));
                }

                format = format.replace(placeholder.toString(), replace);
            }

            if (!format.isEmpty()) {
                lines.add(HologramTextFormatter.replaceComponents(format, componentReplacements));
            }
        }

        return lines.toArray(new Component[0]);
    }

    private Location getHologramLocation(Chest[] chests, BlockFace face) {
        World w = location.getWorld();
        int x = location.getBlockX();
        int y  = location.getBlockY();
        int z = location.getBlockZ();

        Location holoLocation = new Location(w, x, y, z);

        double deltaY = -0.6;

        if (Config.hologramFixedBottom) deltaY = -0.85;

        if (chests[1] != null) {
            Chest c1 = (face == BlockFace.NORTH || face == BlockFace.EAST) ? chests[1] : chests[0];
            Chest c2 = (face == BlockFace.NORTH || face == BlockFace.EAST) ? chests[0] : chests[1];

            if (holoLocation.equals(c1.getLocation())) {
                if (c1.getX() != c2.getX()) {
                    holoLocation.add(0, deltaY, 0.5);
                } else if (c1.getZ() != c2.getZ()) {
                    holoLocation.add(0.5, deltaY, 0);
                } else {
                    holoLocation.add(0.5, deltaY, 0.5);
                }
            } else {
                if (c1.getX() != c2.getX()) {
                    holoLocation.add(1, deltaY, 0.5);
                } else if (c1.getZ() != c2.getZ()) {
                    holoLocation.add(0.5, deltaY, 1);
                } else {
                    holoLocation.add(0.5, deltaY, 0.5);
                }
            }
        } else {
            holoLocation.add(0.5, deltaY, 0.5);
        }

        holoLocation.add(0, Config.hologramLift, 0);
        holoLocation.setYaw(HologramOrientation.yawForDirection(face.getModX(), face.getModZ()));
        holoLocation.setPitch(0f);

        return holoLocation;
    }

    private static String getLegacyDisplayName(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }

        final ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta == null ? null : ItemUtils.serializePlainly(itemMeta.displayName());
    }

    /**
     * @return Whether an ID has been assigned to the shop
     */
    public boolean hasId() {
        return id != -1;
    }

    /**
     * <p>Assign an ID to the shop.</p>
     * Only works for the first time!
     * @param id ID to set for this shop
     */
    public void setId(int id) {
        if (this.id == -1) {
            this.id = id;
        }
    }

    /**
     * @return Whether the shop has already been created
     */
    public boolean isCreated() {
        return created;
    }

    /**
     * @return The ID of the shop
     */
    public int getID() {
        return id;
    }

    /**
     * @return Vendor of the shop; probably the creator of it
     */
    public OfflinePlayer getVendor() {
        return vendor;
    }

    /**
     * @return Product the shop sells (or buys)
     */
    public ShopProduct getProduct() {
        return product;
    }

    /**
     * @return Location of (one of) the shop's chest
     */
    public Location getLocation() {
        return location;
    }

    /**
     * @return Buy price of the shop
     */
    public double getBuyPrice() {
        return buyPrice;
    }

    /**
     * @return Sell price of the shop
     */
    public double getSellPrice() {
        return sellPrice;
    }

    /**
     * @return Type of the shop
     */
    public ShopType getShopType() {
        return shopType;
    }

    /**
     * @return Hologram of the shop
     */
    public Hologram getHologram() {
        return hologram;
    }

    /**
     * @return Floating {@link ShopItem} of the shop
     */
    public ShopItem getItem() {
        return item;
    }

    public boolean hasHologram() {
        return hologram != null && hologram.exists();
    }

    public boolean hasItem() {
        return item != null && item.exists();
    }

    /**
     * @return {@link InventoryHolder} of the shop or <b>null</b> if the shop has no chest.
     */
    public InventoryHolder getInventoryHolder() {
        Block b = getLocation().getBlock();

        if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) {
            Chest chest = (Chest) b.getState();
            return chest.getInventory().getHolder();
        }

        return null;
    }

}
