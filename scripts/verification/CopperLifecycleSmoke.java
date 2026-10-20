import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopProduct;
import de.epiceric.shopchest.utils.Callback;
import de.epiceric.shopchest.utils.Utils;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Real disposable-server regression. Never install on a shared or production server. */
public final class CopperLifecycleSmoke extends JavaPlugin implements Listener {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0009-01faeaf02222");
    private static final int X = 1032, Y = 100, Z = 1032, CHUNK_X = 64, CHUNK_Z = 64;
    private static final double BUY_PRICE = 13.5, SELL_PRICE = 4.25;
    private static final int STOCK = 12, BUNDLE = 2;
    private record Stage(String action, Material material) { }
    private static final List<Stage> STAGES = List.of(
            new Stage("baseline", Material.COPPER_CHEST),
            new Stage("wax", Material.WAXED_COPPER_CHEST),
            new Stage("remove-wax", Material.COPPER_CHEST),
            new Stage("oxidize", Material.EXPOSED_COPPER_CHEST),
            new Stage("wax", Material.WAXED_EXPOSED_COPPER_CHEST),
            new Stage("remove-wax", Material.EXPOSED_COPPER_CHEST),
            new Stage("oxidize", Material.WEATHERED_COPPER_CHEST),
            new Stage("wax", Material.WAXED_WEATHERED_COPPER_CHEST),
            new Stage("remove-wax", Material.WEATHERED_COPPER_CHEST),
            new Stage("oxidize", Material.OXIDIZED_COPPER_CHEST),
            new Stage("wax", Material.WAXED_OXIDIZED_COPPER_CHEST),
            new Stage("remove-wax", Material.OXIDIZED_COPPER_CHEST),
            new Stage("scrape", Material.WEATHERED_COPPER_CHEST),
            new Stage("scrape", Material.EXPOSED_COPPER_CHEST),
            new Stage("scrape", Material.COPPER_CHEST),
            new Stage("cold-boot-checkpoint", Material.EXPOSED_COPPER_CHEST));

    private ShopChest shops;
    private World world;
    private int shopId, assertions, chunkLoads, chunkUnloads, unloadCycles;
    private int incomingEvents, outgoingEvents, minecartEvents, engineCases, tradeRoundTrips, tradeProbes;
    private int caseIncoming, caseOutgoing, caseMinecart;
    private boolean busy;
    private String mode;
    private HopperMinecart cart;
    private final Set<Material> exercised = EnumSet.noneOf(Material.class);
    private final List<UUID> retiredDisplays = new ArrayList<>();

    @Override
    public void onEnable() {
        shops = (ShopChest) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ShopChest"));
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("coppersmoke")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof ConsoleCommandSender)) return true;
            if (busy) { sender.sendMessage("Copper lifecycle fixture already running"); return true; }
            guarded(() -> {
                require(args.length == 1 && List.of("seed", "verify").contains(args[0]), "seed or verify required");
                mode = args[0];
                busy = true;
                assertions = chunkLoads = chunkUnloads = unloadCycles = 0;
                incomingEvents = outgoingEvents = minecartEvents = engineCases = tradeRoundTrips = tradeProbes = 0;
                exercised.clear(); retiredDisplays.clear();
                world = Bukkit.getWorlds().getFirst();
                require(world.getName().equals("stable-fixture"), "Only the fresh stable-fixture world is allowed");
                require(Bukkit.getOnlinePlayers().isEmpty(), "No players may occupy the disposable lifecycle fixture");
                require(world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0), "Disable random oxidation for deterministic transitions");
                readCheckpoint();
            });
            return true;
        });
    }

    private Location at() { return new Location(world, X, Y, Z); }
    private Path checkpoint() { return getDataFolder().toPath().resolve("copper-checkpoint.txt"); }

    private void readCheckpoint() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                List<String> lines = Files.exists(checkpoint()) ? Files.readAllLines(checkpoint()) : List.of();
                sync(() -> {
                    if (mode.equals("seed")) {
                        require(lines.isEmpty(), "Seed must not replace an existing checkpoint");
                        seed();
                    } else {
                        require(lines.size() == 5, "Complete first-boot checkpoint required");
                        shopId = Integer.parseInt(lines.get(0));
                        require(shopId > 0, "Checkpoint has a persistent shop ID");
                        require(world.getUID().toString().equals(lines.get(1)), "Restart uses the same disposable world");
                        require(lines.get(2).equals(Material.EXPOSED_COPPER_CHEST.name()), "Cold-boot checkpoint is exposed copper");
                        retiredDisplays.add(UUID.fromString(lines.get(3)));
                        retiredDisplays.add(UUID.fromString(lines.get(4)));
                        world.loadChunk(CHUNK_X, CHUNK_Z);
                        world.setChunkForceLoaded(CHUNK_X, CHUNK_Z, true);
                        await("automatic cold-boot shop and real displays", this::displaysReady, 160, () -> {
                            verifyState(Material.EXPOSED_COPPER_CHEST);
                            assertRetiredDisplaysGone();
                            tradeProbe();
                            verifyPersisted(() -> {
                                getLogger().info("COPPER_COLD_BOOT_PASS shopId=" + shopId + " material=EXPOSED_COPPER_CHEST");
                                transition(0);
                            });
                        });
                    }
                });
            } catch (Throwable error) { sync(() -> failed(error)); }
        });
    }

    private void seed() {
        world.loadChunk(CHUNK_X, CHUNK_Z);
        world.setChunkForceLoaded(CHUNK_X, CHUNK_Z, true);
        require(at().getBlock().getType().isAir(), "Fixture must not replace a block");
        require(shops.getShopUtils().getShop(at()) == null, "Fixture must not replace a shop");
        for (int[] offset : helperOffsets()) {
            require(world.getBlockAt(X + offset[0], Y + offset[1], Z).getType().isAir(), "Helper position starts empty");
        }
        setCopper(Material.COPPER_CHEST);
        inventory().setContents(expectedContents());
        Shop shop = new Shop(shops, Bukkit.getOfflinePlayer(OWNER), new ShopProduct(product(), BUNDLE),
                at(), BUY_PRICE, SELL_PRICE, Shop.ShopType.NORMAL);
        require(shop.create(true), "Create the initial copper shop");
        shops.getShopUtils().addShop(shop, true, callback(id -> {
            require(id > 0, "Copper shop receives a database identity");
            shopId = id;
            await("initial real display entities", this::displaysReady, 80, () -> verifyPersisted(() -> transition(0)));
        }));
    }

    private void transition(int index) {
        if (index == STAGES.size()) { finish(); return; }
        Stage stage = STAGES.get(index);
        // This is an API-driven state transition, not a honeycomb/axe/player or random-tick event.
        // In particular, never copy inventory contents back after changing the block data.
        setCopper(stage.material());
        verifyState(stage.material());
        stockRoundTrip();
        tradeProbe();
        getLogger().info("COPPER_TRANSITION_PASS action=" + stage.action() + " material=" + stage.material()
                + " shopId=" + shopId + " owner=" + OWNER + " stock=" + STOCK);
        if (exercised.add(stage.material())) {
            recoverRemovedDisplays(() -> nativeHoppers(stage.material(), () ->
                    unloadReload(stage.material(), () -> transition(index + 1))));
        } else {
            later(1, () -> transition(index + 1));
        }
    }

    private void setCopper(Material material) {
        Chest data = (Chest) material.createBlockData();
        if (at().getBlock().getBlockData() instanceof Chest previous) {
            previous.copyTo(data);
            require(previous.getFacing() == data.getFacing() && previous.getType() == data.getType()
                    && previous.isWaterlogged() == data.isWaterlogged(), "Copper transition preserves common block properties");
        } else {
            data.setFacing(BlockFace.NORTH);
            data.setType(Chest.Type.SINGLE);
            data.setWaterlogged(false);
        }
        at().getBlock().setBlockData(data);
    }

    private ItemStack product() {
        ItemStack item = ItemStack.of(Material.DIAMOND);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Copper lifecycle diamond"));
            meta.lore(List.of(Component.text("Foreign metadata must survive every copper transition"),
                    Component.text("payload:" + "copper-".repeat(64))));
            meta.getPersistentDataContainer().set(new NamespacedKey("foreign_plugin", "copper_identity"),
                    PersistentDataType.STRING, "copper-lifecycle-v1");
            meta.getPersistentDataContainer().set(new NamespacedKey("foreign_plugin", "opaque"),
                    PersistentDataType.BYTE_ARRAY, new byte[] {0, 1, 127, -1, 42});
        });
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return item;
    }

    private ItemStack cargo() {
        ItemStack item = ItemStack.of(Material.SHULKER_BOX);
        item.editMeta(BlockStateMeta.class, meta -> {
            ShulkerBox box = (ShulkerBox) meta.getBlockState();
            box.getInventory().setItem(8, product().asQuantity(5));
            meta.setBlockState(box);
            meta.displayName(Component.text("Unrelated nested cargo"));
            meta.getPersistentDataContainer().set(new NamespacedKey("foreign_plugin", "cargo"),
                    PersistentDataType.STRING, "must-not-be-traded");
        });
        return item;
    }

    private ItemStack[] expectedContents() {
        ItemStack[] contents = new ItemStack[27];
        contents[0] = product().asQuantity(STOCK);
        contents[7] = cargo();
        contents[26] = ItemStack.of(Material.COPPER_INGOT, 3);
        return contents;
    }

    private Inventory inventory() {
        require(at().getBlock().getState(false) instanceof Container, "Current material has a live container");
        return ((Container) at().getBlock().getState(false)).getInventory();
    }

    private Shop currentShop() {
        Shop shop = shops.getShopUtils().getShop(at());
        require(shop != null, "Persisted copper shop remains registered");
        return shop;
    }

    private void verifyIdentity(Shop shop) {
        require(shop.getID() == shopId, "Persistent shop ID is unchanged");
        require(shop.getVendor().getUniqueId().equals(OWNER), "Authoritative synthetic owner UUID is unchanged");
        require(shop.getLocation().equals(at()), "Shop anchor is unchanged");
        require(shop.getBuyPrice() == BUY_PRICE && shop.getSellPrice() == SELL_PRICE, "Both prices are unchanged");
        require(shop.getShopType() == Shop.ShopType.NORMAL, "Normal shop type is unchanged");
        require(shop.getProduct().getAmount() == BUNDLE && shop.getProduct().getItemStack().equals(product()),
                "Exact product components and trade bundle are unchanged");
    }

    private void verifyState(Material material) {
        require(world.isChunkLoaded(CHUNK_X, CHUNK_Z), "State inspection never loads an absent chunk");
        require(at().getBlock().getType() == material, "Expected copper material " + material);
        Shop shop = currentShop();
        verifyIdentity(shop);
        require(shop.getContainer() != null, "Current copper variant resolves through Shop's public container API");
        require(shop.getContainerLocations().equals(Set.of(at())), "Current variant still has the same physical anchor");
        require(Arrays.equals(expectedContents(), inventory().getContents()), "Exact world stock and nested cargo are unchanged");
        require(Arrays.equals(expectedContents(), shop.getInventory().getContents()), "Shop resolves the current live inventory");
        require(shops.getShopUtils().getShopByInventory(inventory()) == shop, "Current inventory remains registered for protection");
        require(Utils.getAmount(shop.getInventory(), product()) == STOCK, "Metadata-aware stock is exactly twelve");
        require(Utils.getFreeSpaceForItem(shop.getInventory(), product()) == 388, "Metadata/component-aware capacity is exactly 388");
        verifyDisplays();
    }

    /** The stock/capacity boundary, not a real-player purchase or CMI balance transaction. */
    private void stockRoundTrip() {
        Inventory stock = currentShop().getInventory();
        Inventory customer = Bukkit.createInventory(null, 9);
        ItemStack bundle = product().asQuantity(BUNDLE);
        require(stock.removeItem(bundle.clone()).isEmpty(), "Customer-buy stock extraction succeeds completely");
        require(customer.addItem(bundle.clone()).isEmpty(), "Customer-buy inventory insertion succeeds completely");
        require(Utils.getAmount(stock, product()) == 10 && Utils.getAmount(customer, product()) == 2,
                "Customer-buy bundle transfers exactly two items");
        require(customer.getItem(0).equals(bundle), "Customer-buy preserves every product component");
        require(customer.removeItem(bundle.clone()).isEmpty(), "Customer-sell extraction succeeds completely");
        require(stock.addItem(bundle.clone()).isEmpty(), "Customer-sell insertion succeeds completely");
        require(customer.isEmpty() && Arrays.equals(expectedContents(), inventory().getContents()),
                "Customer-sell returns to exact stock and unrelated cargo baseline");
        tradeRoundTrips++;
    }

    private void tradeProbe() {
        assertions += CopperLifecycleSmokeTradeProbe.verify(this, shops, currentShop());
        tradeProbes++;
        require(Arrays.equals(expectedContents(), inventory().getContents()), "Real trade probe leaves exact fixture stock");
    }

    private List<Entity> displays() {
        return world.getNearbyEntities(at().add(0.5, 2, 0.5), 1.4, 5, 1.4).stream()
                .filter(entity -> entity instanceof TextDisplay || entity instanceof ItemDisplay).toList();
    }

    private boolean displaysReady() {
        if (!world.isChunkLoaded(CHUNK_X, CHUNK_Z) || shops.getShopUtils().getShop(at()) == null) return false;
        List<Entity> entities = displays();
        return entities.stream().anyMatch(entity -> entity instanceof TextDisplay && entity.isValid())
                && entities.stream().anyMatch(entity -> entity instanceof ItemDisplay && entity.isValid());
    }

    private void verifyDisplays() {
        List<Entity> entities = displays();
        require(entities.size() == 2, "Exactly two native display entities, without missing or duplicate displays");
        require(entities.stream().filter(entity -> entity instanceof TextDisplay && entity.isValid()).count() == 1,
                "Exactly one valid TextDisplay entity");
        require(entities.stream().filter(entity -> entity instanceof ItemDisplay && entity.isValid()).count() == 1,
                "Exactly one valid ItemDisplay entity");
        TextDisplay text = (TextDisplay) entities.stream().filter(entity -> entity instanceof TextDisplay).findFirst().orElseThrow();
        ItemDisplay item = (ItemDisplay) entities.stream().filter(entity -> entity instanceof ItemDisplay).findFirst().orElseThrow();
        String visible = PlainTextComponentSerializer.plainText().serialize(text.text());
        require(visible.contains("Copper lifecycle"),
                "TextDisplay contains the actual product name");
        require(visible.contains(plainCurrency(BUY_PRICE)) && visible.contains(plainCurrency(SELL_PRICE)),
                "TextDisplay contains both original formatted prices: " + visible);
        require(item.getItemStack().equals(product()), "ItemDisplay carries the exact metadata-rich product");
        require(entities.stream().noneMatch(Entity::isPersistent), "Transient ShopChest displays are not saved as duplicates");
        require(currentShop().getHologram().exists() && currentShop().getItem().exists(), "Wrappers agree with actual entity validity");
        assertRetiredDisplaysGone();
    }

    private String plainCurrency(double price) {
        return PlainTextComponentSerializer.plainText().serialize(
                LegacyComponentSerializer.legacySection().deserialize(shops.getEconomy().format(price)));
    }

    private void assertRetiredDisplaysGone() {
        for (UUID id : retiredDisplays) {
            Entity old = Bukkit.getEntity(id);
            require(old == null || !old.isValid(), "Old display UUID is not resurrected or duplicated: " + id);
        }
    }

    private void recoverRemovedDisplays(Runnable next) {
        List<Entity> old = displays();
        old.forEach(entity -> { retiredDisplays.add(entity.getUniqueId()); entity.remove(); });
        require(old.stream().noneMatch(Entity::isValid), "Deliberately removed entities are invalid");
        require(!currentShop().getHologram().exists() && !currentShop().getItem().exists(),
                "Wrapper presence is not mistaken for actual entity validity");
        require(currentShop().restoreDisplays(), "Public restoration recreates absent native entities");
        verifyDisplays();
        require(!currentShop().restoreDisplays(), "Repeated restoration does not create duplicates");
        verifyDisplays();
        next.run();
    }

    private void nativeHoppers(Material material, Runnable next) {
        caseIncoming = caseOutgoing = caseMinecart = 0;
        Inventory incoming = helper(Material.HOPPER, 1, 0);
        Hopper data = (Hopper) world.getBlockAt(X + 1, Y, Z).getBlockData();
        data.setFacing(BlockFace.WEST);
        world.getBlockAt(X + 1, Y, Z).setBlockData(data, false);
        incoming.setItem(0, product().asQuantity(7));
        Inventory outgoing = helper(Material.HOPPER, 0, -1);
        Inventory controlSource = helper(Material.CHEST, 4, 0);
        Inventory controlDestination = helper(Material.HOPPER, 4, -1);
        controlSource.setItem(0, product().asQuantity(12));
        later(40, () -> {
            require(caseIncoming > 0 && caseOutgoing > 0, material + " receives real incoming and outgoing hopper events");
            require(incoming.getItem(0).equals(product().asQuantity(7)), "Cancelled incoming hopper preserves its exact source");
            require(outgoing.isEmpty(), "Cancelled outgoing hopper remains empty");
            require(Utils.getAmount(controlDestination, product()) > 0, "Unregistered control hopper really transfers items");
            require(Utils.getAmount(controlSource, product()) + Utils.getAmount(controlDestination, product()) == 12,
                    "Unregistered control conserves exact item counts/components");
            verifyState(material);
            clearHelpers();
            world.getBlockAt(X, Y - 2, Z).setType(Material.STONE, false);
            world.getBlockAt(X, Y - 1, Z).setType(Material.RAIL, false);
            cart = world.spawn(new Location(world, X + 0.5, Y - 0.9, Z + 0.5), HopperMinecart.class);
            cart.setGravity(false);
            cart.setPersistent(false);
            later(30, () -> {
                require(caseMinecart > 0, material + " receives real hopper-minecart events");
                require(cart.getInventory().isEmpty(), "Hopper minecart cannot extract metadata-rich stock");
                verifyState(material);
                clearHelpers();
                engineCases++;
                next.run();
            });
        });
    }

    private Inventory helper(Material material, int dx, int dy) {
        world.getBlockAt(X + dx, Y + dy, Z).setType(material, false);
        return ((Container) world.getBlockAt(X + dx, Y + dy, Z).getState(false)).getInventory();
    }

    private int[][] helperOffsets() { return new int[][] {{1, 0}, {0, -1}, {0, -2}, {4, 0}, {4, -1}}; }

    private void clearHelpers() {
        if (cart != null) { cart.remove(); cart = null; }
        for (int[] offset : helperOffsets()) {
            var block = world.getBlockAt(X + offset[0], Y + offset[1], Z);
            if (block.getState(false) instanceof Container container) container.getInventory().clear();
            block.setType(Material.AIR, false);
        }
    }

    private void unloadReload(Material material, Runnable next) {
        verifyPersisted(() -> {
            List<Entity> old = displays();
            old.forEach(entity -> retiredDisplays.add(entity.getUniqueId()));
            int previousLoads = chunkLoads, previousUnloads = chunkUnloads;
            world.setChunkForceLoaded(CHUNK_X, CHUNK_Z, false);
            // Ticket removal and unload can complete on a subsequent server tick.
            // Never replace physical unload verification with an unload request alone.
            later(2, () -> requestUnload(80, () -> {
                require(chunkUnloads > previousUnloads, "Actual ChunkUnloadEvent was delivered");
                require(old.stream().noneMatch(Entity::isValid), "Unload invalidates the old entity objects");
                assertRetiredDisplaysGone();
                later(5, () -> {
                    require(!world.isChunkLoaded(CHUNK_X, CHUNK_Z), "Chunk remains absent across ticks");
                    world.loadChunk(CHUNK_X, CHUNK_Z);
                    world.setChunkForceLoaded(CHUNK_X, CHUNK_Z, true);
                    await("automatic chunk-load display recovery", this::displaysReady, 160, () -> {
                        require(chunkLoads > previousLoads, "Actual ChunkLoadEvent was delivered");
                        verifyState(material);
                        tradeProbe();
                        verifyPersisted(() -> {
                            unloadCycles++;
                            getLogger().info("COPPER_CHUNK_CYCLE_PASS material=" + material + " shopId=" + shopId);
                            next.run();
                        });
                    });
                });
            }));
        });
    }

    private void requestUnload(int attempts, Runnable next) {
        if (!world.isChunkLoaded(CHUNK_X, CHUNK_Z)) {
            next.run();
            return;
        }
        world.unloadChunk(CHUNK_X, CHUNK_Z, true);
        if (!world.isChunkLoaded(CHUNK_X, CHUNK_Z)) {
            next.run();
            return;
        }
        require(attempts > 0, "Chunk must unload after fixture tickets are released; plugin tickets="
                + world.getPluginChunkTickets(CHUNK_X, CHUNK_Z));
        later(2, () -> requestUnload(attempts - 1, next));
    }

    private void verifyPersisted(Runnable next) {
        shops.getShopDatabase().getShops(OWNER, callback((Collection<Shop> rows) -> {
            require(rows.size() == 1, "Exactly one persisted shop belongs to the synthetic owner");
            verifyIdentity(rows.iterator().next());
            next.run();
        }));
    }

    private void finish() {
        verifyState(Material.EXPOSED_COPPER_CHEST);
        require(exercised.size() == 8 && engineCases == 8 && unloadCycles == 8, "All eight variants complete engine and chunk lifecycle cases");
        require(tradeProbes == (mode.equals("verify") ? 25 : 24), "Every transition, recovered chunk and cold boot trades");
        verifyPersisted(() -> {
            List<Entity> entities = displays();
            String value = shopId + "\n" + world.getUID() + "\nEXPOSED_COPPER_CHEST\n"
                    + entities.get(0).getUniqueId() + "\n" + entities.get(1).getUniqueId() + "\n";
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    Files.createDirectories(checkpoint().getParent());
                    Path staged = checkpoint().resolveSibling("copper-checkpoint.tmp");
                    Files.writeString(staged, value);
                    Files.move(staged, checkpoint(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    sync(() -> {
                        busy = false;
                        getLogger().info("COPPER_SMOKE_PASS mode=" + mode + " assertions=" + assertions
                                + " shopId=" + shopId + " owner=" + OWNER + " variants=" + exercised.size()
                                + " transitions=" + STAGES.size() + " chunkCycles=" + unloadCycles
                                + " incoming=" + incomingEvents + " outgoing=" + outgoingEvents + " minecart=" + minecartEvents
                                + " stockRoundTrips=" + tradeRoundTrips + " tradeProbes=" + tradeProbes + " finalMaterial=EXPOSED_COPPER_CHEST"
                                + " transitionMechanism=Block.setBlockData trades=real-listener-synthetic-player");
                    });
                } catch (Throwable error) { sync(() -> failed(error)); }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeTransfer(InventoryMoveItemEvent event) {
        if (!busy || world == null) return;
        boolean source = at().equals(event.getSource().getLocation());
        boolean destination = at().equals(event.getDestination().getLocation());
        if (!source && !destination) return;
        guarded(() -> {
            require(event.isCancelled(), "Native transfer touching the persistent copper shop is cancelled");
            if (event.getInitiator().getHolder(false) instanceof HopperMinecart) {
                minecartEvents++; caseMinecart++;
            } else if (destination) { incomingEvents++; caseIncoming++; }
            else { outgoingEvents++; caseOutgoing++; }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeLoad(ChunkLoadEvent event) {
        if (busy && event.getWorld().equals(world) && event.getChunk().getX() == CHUNK_X && event.getChunk().getZ() == CHUNK_Z) chunkLoads++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observeUnload(ChunkUnloadEvent event) {
        if (busy && event.getWorld().equals(world) && event.getChunk().getX() == CHUNK_X && event.getChunk().getZ() == CHUNK_Z) {
            guarded(() -> require(event.isSaveChunk(), "Actual chunk unload retains save semantics"));
            chunkUnloads++;
        }
    }

    private void await(String label, BooleanSupplier condition, int attempts, Runnable next) {
        guarded(() -> {
            if (condition.getAsBoolean()) { next.run(); return; }
            require(attempts > 0, "Timed out waiting for " + label);
            later(2, () -> await(label, condition, attempts - 1, next));
        });
    }

    private void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(this, () -> { if (busy) guarded(task); }, ticks);
    }

    private void sync(Runnable task) {
        if (isEnabled()) Bukkit.getScheduler().runTask(this, () -> guarded(task));
    }

    private <T> Callback<T> callback(Consumer<T> next) {
        return new Callback<>(shops) {
            @Override public void onResult(T result) {
                if (busy) guarded(() -> { require(Bukkit.isPrimaryThread(), "Persistence callback returns to main thread"); next.accept(result); });
            }
            @Override public void onError(Throwable error) { failed(error); }
        };
    }

    private void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private void guarded(Runnable task) {
        try { task.run(); } catch (Throwable error) { failed(error); }
    }

    private void failed(Throwable error) {
        if (!busy) { getLogger().log(java.util.logging.Level.SEVERE, "COPPER_SMOKE_FAIL", error); return; }
        busy = false;
        getLogger().log(java.util.logging.Level.SEVERE, "COPPER_SMOKE_FAIL", error);
        // Preserve the shop, inventory, world and checkpoint for diagnosis.
        if (cart != null) { cart.remove(); cart = null; }
        if (world != null) world.setChunkForceLoaded(CHUNK_X, CHUNK_Z, false);
    }

    @Override public void onDisable() {
        busy = false;
        if (cart != null) cart.remove();
        if (world != null) world.setChunkForceLoaded(CHUNK_X, CHUNK_Z, false);
    }
}
