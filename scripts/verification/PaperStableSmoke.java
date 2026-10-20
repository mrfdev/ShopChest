import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.advertising.AdvertisingCurrencyMatcher;
import de.epiceric.shopchest.advertising.ItemStackBinaryCodec;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopProduct;
import de.epiceric.shopchest.storefront.StorefrontDisplay;
import de.epiceric.shopchest.storefront.StorefrontProfile;
import de.epiceric.shopchest.utils.Callback;
import de.epiceric.shopchest.utils.Utils;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Synthetic fixtures only. Never install on a shared or production server. */
public final class PaperStableSmoke extends JavaPlugin {
    private static final List<UUID> OWNERS = List.of(
            UUID.fromString("98e15927-4dd2-4b2b-91b8-928f402d9871"),
            UUID.fromString("00000000-0000-0000-0009-01faeaf01111"));
    private ShopChest shops;
    private World world;
    private int assertions;

    @Override
    public void onEnable() {
        shops = (ShopChest) Bukkit.getPluginManager().getPlugin("ShopChest");
        Objects.requireNonNull(getCommand("stablesmoke")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof ConsoleCommandSender)) return true;
            guarded(() -> {
                require(args.length == 1, "seed, verify or showcase argument required");
                world = Bukkit.getWorlds().getFirst();
                world.setChunkForceLoaded(0, 0, true);
                metadata();
                if (args[0].equals("seed")) seed(0);
                else if (args[0].equals("verify")) verify(0);
                else if (args[0].equals("showcase")) showcase();
                else throw new IllegalArgumentException("Unknown smoke action");
            });
            return true;
        });
    }

    private ItemStack product() {
        ItemStack nested = ItemStack.of(Material.DIAMOND, 7);
        nested.editMeta(meta -> {
            meta.displayName(Component.text("Stable metadata fixture"));
            meta.lore(List.of(Component.text("Preserve all foreign metadata")));
            meta.getPersistentDataContainer().set(new NamespacedKey("foreign_plugin", "identity"),
                    PersistentDataType.STRING, "opaque-fixture-v1");
        });
        nested.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        ItemStack item = ItemStack.of(Material.SHULKER_BOX);
        item.editMeta(BlockStateMeta.class, meta -> {
            ShulkerBox state = (ShulkerBox) meta.getBlockState();
            state.getInventory().setItem(0, nested);
            meta.setBlockState(state);
            meta.displayName(Component.text("Persistent container fixture"));
            meta.getPersistentDataContainer().set(new NamespacedKey("foreign_plugin", "payload"),
                    PersistentDataType.BYTE_ARRAY, new byte[] {0, 1, 2, 3, 127, -1});
        });
        return item;
    }

    private void metadata() {
        require(Bukkit.isPrimaryThread(), "Item and world checks run on server thread");
        ItemStack original = product();
        ItemStackBinaryCodec codec = new ItemStackBinaryCodec();
        require(original.equals(codec.decode(codec.encode(original))), "Binary nested item round trip");
        require(original.equals(Utils.decode(Utils.encode(original))), "Existing shop product format round trip");
        var matcher = AdvertisingCurrencyMatcher.itemStacks();
        require(!matcher.matches(original, null), "Absent currency authority fails closed");
        require(matcher.matches(original.asQuantity(2), original), "Currency normalizes amount only");
        ItemStack forged = original.clone();
        forged.editMeta(meta -> meta.getPersistentDataContainer().remove(
                new NamespacedKey("foreign_plugin", "payload")));
        require(!matcher.matches(forged, original), "Matching appearance cannot replace complete metadata");
        var inventory = Bukkit.createInventory(null, 9);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, original.clone());
        require(!inventory.addItem(original.clone()).isEmpty(), "Full inventory returns undelivered items");
        require(inventory.getItem(0).equals(original), "Failed insertion preserves components");
        boolean rejected = false;
        try { codec.decode("paper-item-v1:!"); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Corrupt persistence payload rejected");
    }

    private Location location(int index) { return new Location(world, 4 + index * 4, 100, 4); }

    private void seed(int index) {
        if (index == OWNERS.size()) { verify(0); return; }
        Location at = location(index);
        at.getBlock().setType(index == 0 ? Material.CHEST : Material.BARREL, false);
        Container container = (Container) at.getBlock().getState(false);
        container.getInventory().setItem(0, product());
        Shop shop = new Shop(shops, Bukkit.getOfflinePlayer(OWNERS.get(index)),
                new ShopProduct(product()), at, 5, 2, Shop.ShopType.NORMAL);
        require(shop.create(true), "Create shop displays");
        shops.getShopUtils().addShop(shop, true, callback(id -> {
            require(id > 0, "Shop receives persisted id");
            StorefrontProfile profile = profile(index);
            shops.getStorefrontRepository().saveProfile(profile,
                    callback(ignored -> seed(index + 1)));
        }));
    }

    private StorefrontProfile profile(int index) {
        return new StorefrontProfile(OWNERS.get(index), index == 0 ? "Java Fixture" : ".BedrockFixture",
                "Stable compatibility", null, null, false, false, 1791720000000L);
    }

    private void verify(int index) {
        if (index == OWNERS.size()) {
            getLogger().info("STABLE_SMOKE_PASS assertions=" + assertions
                    + " shops=2 profiles=2 nestedItems=2 asyncCallbacks=main-thread");
            return;
        }
        shops.getShopDatabase().getShops(OWNERS.get(index), callback((Collection<Shop> rows) -> {
            require(rows.size() == 1, "Exactly one persisted shop for UUID " + OWNERS.get(index));
            Shop row = rows.iterator().next();
            require(row.getVendor().getUniqueId().equals(OWNERS.get(index)), "Authoritative owner UUID retained");
            require(row.getProduct().getItemStack().equals(product()), "Stored nested components retained");
            require(row.getBuyPrice() == 5 && row.getSellPrice() == 2, "Trade terms retained");
            require(row.getLocation().equals(location(index)), "Shop location retained");
            Shop loaded = shops.getShopUtils().getShop(location(index));
            require(loaded != null, "Persisted shop loaded into registry");
            loaded.restoreDisplays();
            require(loaded.getHologram() != null && loaded.getHologram().exists(), "TextDisplay present");
            require(loaded.getItem() != null && loaded.getItem().exists(), "ItemDisplay present");
            require(loaded.getInventory().getItem(0).equals(product()), "World inventory persisted");
            shops.getStorefrontRepository().findProfile(OWNERS.get(index),
                    callback((Optional<StorefrontProfile> found) -> {
                        require(found.isPresent() && found.get().equals(profile(index)), "Profile persisted by UUID");
                        verify(index + 1);
                    }));
        }));
    }

    /** Optional player-review area, created only after the synthetic shops exist. */
    private void showcase() {
        require(world.getName().equals("stable-fixture"), "Showcase requires the disposable fixture world");
        for (int index = 0; index < OWNERS.size(); index++) {
            Shop shop = shops.getShopUtils().getShop(location(index));
            require(shop != null && shop.getVendor().getUniqueId().equals(OWNERS.get(index)),
                    "Showcase requires the seeded synthetic shops");
        }
        StorefrontDisplay candidate = new StorefrontDisplay(OWNERS.getFirst(), world.getUID(),
                world.getName(), 12, 100, 4, 1792497600000L);
        shops.getStorefrontRepository().findDisplay(candidate.ownerId(),
                callback((Optional<StorefrontDisplay> existing) -> {
                    require(existing.isEmpty() || existing.get().anchor().equals(candidate.anchor()),
                            "Do not replace another Storefront Display");
                    preflightShowcase(existing.isPresent());
                    if (existing.isPresent()) {
                        buildShowcase();
                    } else {
                        shops.getStorefrontRepository().createDisplay(candidate,
                                callback(ignored -> buildShowcase()));
                    }
                }));
    }

    private void preflightShowcase(boolean existingDisplay) {
        for (int x = -8; x <= 20; x++) {
            for (int z = -8; z <= 20; z++) {
                Material floor = world.getBlockAt(x, 99, z).getType();
                require(floor.isAir() || floor == showcaseFloor(x, z),
                        "Do not overwrite unrelated floor blocks");
                if (showcaseEdge(x, z)) {
                    for (int y = 100; y <= 101; y++) {
                        Material rail = world.getBlockAt(x, y, z).getType();
                        require(rail.isAir() || rail == Material.GLASS,
                                "Do not overwrite unrelated edge blocks");
                    }
                }
            }
        }
        Material anchor = world.getBlockAt(12, 100, 4).getType();
        require(anchor.isAir() || existingDisplay && anchor == Material.ENDER_CHEST,
                "Do not overwrite an unrelated display anchor");
        require(world.getBlockAt(12, 101, 4).getType().isAir(), "Display has clear space above it");
        require(world.getBlockAt(6, 100, -2).getType().isAir()
                && world.getBlockAt(6, 101, -2).getType().isAir(), "Showcase spawn has clear space");
    }

    private Material showcaseFloor(int x, int z) {
        return Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0
                ? Material.SEA_LANTERN : Material.STONE_BRICKS;
    }

    private boolean showcaseEdge(int x, int z) {
        return x == -8 || x == 20 || z == -8 || z == 20;
    }

    private void buildShowcase() {
        preflightShowcase(true);
        for (int x = -8; x <= 20; x++) {
            for (int z = -8; z <= 20; z++) {
                world.getBlockAt(x, 99, z).setType(showcaseFloor(x, z), false);
                if (showcaseEdge(x, z)) {
                    for (int y = 100; y <= 101; y++) {
                        world.getBlockAt(x, y, z).setType(Material.GLASS, false);
                    }
                }
            }
        }
        world.getBlockAt(12, 100, 4).setType(Material.ENDER_CHEST, false);
        require(world.setSpawnLocation(new Location(world, 6.5, 100, -2, 0, 0)),
                "Set a safe showcase spawn facing the fixtures");
        require(world.setGameRule(GameRules.RESPAWN_RADIUS, 0), "Keep joins on the showcase platform");
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(1_728_000);
        world.setThunderDuration(1_728_000);
        for (int index = 0; index < OWNERS.size(); index++) {
            shops.getShopUtils().getShop(location(index)).restoreDisplays();
        }
        shops.getStorefrontDisplayManager().start();
        awaitShowcase(20);
    }

    private void awaitShowcase(int attempts) {
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            Collection<org.bukkit.entity.Entity> entities = world.getNearbyEntities(
                    new Location(world, 12.5, 102, 4.5), 1, 4, 1);
            boolean ready = entities.stream().anyMatch(entity -> entity instanceof TextDisplay)
                    && entities.stream().anyMatch(entity -> entity instanceof ItemDisplay)
                    && entities.stream().anyMatch(entity -> entity instanceof Interaction);
            if (!ready && attempts > 1) {
                awaitShowcase(attempts - 1);
                return;
            }
            require(ready, "Storefront text, icon and click target are ready");
            getLogger().info("SHOWCASE_READY shops=(4,100,4),(8,100,4)"
                    + " storefront=(12,100,4) spawn=(6.5,100,-2) facing=+Z");
        }), 10L);
    }

    private <T> Callback<T> callback(Consumer<T> next) {
        return new Callback<>(shops) {
            @Override public void onResult(T result) {
                guarded(() -> {
                    require(Bukkit.isPrimaryThread(), "Asynchronous SQL returns to server thread");
                    next.accept(result);
                });
            }
            @Override public void onError(Throwable error) { failed(error); }
        };
    }

    private void require(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }
    private void guarded(Runnable action) {
        try { action.run(); } catch (Throwable error) { failed(error); }
    }
    private void failed(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE, "STABLE_SMOKE_FAIL", error);
    }
}
