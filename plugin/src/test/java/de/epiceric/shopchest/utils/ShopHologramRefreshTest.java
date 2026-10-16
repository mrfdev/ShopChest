package de.epiceric.shopchest.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.hologram.HologramColorPalette;
import de.epiceric.shopchest.config.hologram.HologramFormat;
import de.epiceric.shopchest.display.Hologram;
import de.epiceric.shopchest.external.cmi.CmiCachedOwnerNames;
import de.epiceric.shopchest.language.LanguageManager;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.MessageRegistry;
import de.epiceric.shopchest.language.item.LocalizedItemNameManager;
import de.epiceric.shopchest.listeners.ShopUpdateListener;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopProduct;
import de.epiceric.shopchest.shop.ShopTerms;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real updater, sweep, Shop, formatter, item naming, hologram and display wrapper; Paper boundaries are fakes. */
class ShopHologramRefreshTest {
    private static final UUID JAVA_OWNER = UUID.fromString("f411068c-86d3-4c4b-8c3e-746abd44cf80");
    private static final UUID BEDROCK_OWNER = UUID.fromString("00000000-0000-0000-0009-01faeaf01111");
    private static final String FORMAT = """
            lines:
              0:
                options:
                  default:
                    format: "%VENDOR% %AMOUNT% x %ITEMNAME%"
              1:
                options:
                  default:
                    format: "Buy %BUY-PRICE% Sell %SELL-PRICE% Stock %STOCK% Space %CHEST-SPACE%"
            """;

    @TempDir Path directory;
    private MockedStatic<Bukkit> bukkit;
    private final Map<Field, Object> savedConfig = new HashMap<>();
    private ShopChest previousPlugin;
    private Fixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        for (Field field : Config.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && !java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                savedConfig.put(field, field.get(null));
            }
        }
        previousPlugin = ShopChest.getInstance();
        Config.hologramColors = HologramColorPalette.load(key -> null, warning -> fail(warning));
        Config.hologramMaxItemNameLength = 48;
        Config.hologramMaxItemDetailEntries = 4;
        Config.hologramItemDetailsPerLine = 2;
        Config.hologramTextScale = 0.5F;
        bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        fixture = new Fixture();
    }

    @AfterEach
    void tearDown() throws Exception {
        bukkit.close();
        setField(ShopChest.class, null, "instance", previousPlugin);
        for (var saved : savedConfig.entrySet()) saved.getKey().set(null, saved.getValue());
    }

    @Test
    void repeatedOfflineRefreshesDoNotRepeatExpensiveOwnerNameResolution() throws Exception {
        fixture.localNames.put(JAVA_OWNER, "OfflineOwner");
        DisplayShop first = fixture.add(JAVA_OWNER);
        DisplayShop second = fixture.add(JAVA_OWNER);
        fixture.ticks(40);
        assertTrue(first.text().contains("OfflineOwner"));
        assertTrue(second.text().contains("OfflineOwner"));
        assertEquals(1, fixture.lookups.get());
        clearInvocations(first.display, second.display, fixture.format);
        fixture.ticks(4_000);
        assertEquals(1, fixture.lookups.get());
        verify(first.owner, never()).getName();
        verify(second.owner, never()).getName();
        verify(fixture.server, never()).getOfflinePlayer(any(UUID.class));
        verify(fixture.server, never()).getOfflinePlayer(anyString());
        verify(fixture.format, never()).getFormat(anyInt(), anyMap(), anyMap());
        verify(first.display, never()).text(any(Component.class));
        verify(second.display, never()).setTransformation(any());
    }

    @Test
    void missingLocalIdentityIsNegativelyCachedUntilAnAuthoritativeJoin() throws Exception {
        DisplayShop shop = fixture.add(JAVA_OWNER);
        fixture.ticks(4_040);
        assertTrue(shop.text().contains(JAVA_OWNER.toString()));
        assertEquals(1, fixture.lookups.get());
        verify(shop.owner, never()).getName();
        fixture.join(JAVA_OWNER, "NowKnown");
        fixture.ticks(40);
        assertTrue(shop.text().contains("NowKnown"));
        assertEquals(1, fixture.lookups.get());
        assertEquals(JAVA_OWNER, shop.shop.getVendor().getUniqueId());
    }

    @Test
    void joinsRefreshJavaAndPrefixedBedrockNamesEvenWithAStaticFormat() throws Exception {
        fixture.loadFormat(FORMAT.replace("%STOCK%", "fixed").replace("%CHEST-SPACE%", "fixed")
                .replace("%BUY-PRICE%", "fixed"));
        assertFalse(fixture.format.isDynamic());
        fixture.localNames.put(JAVA_OWNER, "OldJavaName");
        fixture.localNames.put(BEDROCK_OWNER, ".OldBedrockName");
        DisplayShop javaShop = fixture.add(JAVA_OWNER);
        DisplayShop bedrockShop = fixture.add(BEDROCK_OWNER);
        javaShop.shop.updateHologramText();
        bedrockShop.shop.updateHologramText();
        fixture.join(JAVA_OWNER, "RenamedJava");
        fixture.join(BEDROCK_OWNER, ".RenamedBedrock");
        fixture.ticks(40);
        assertTrue(javaShop.text().contains("RenamedJava"));
        assertTrue(bedrockShop.text().contains(".RenamedBedrock"));
        assertEquals(JAVA_OWNER, javaShop.shop.getVendor().getUniqueId());
        assertEquals(BEDROCK_OWNER, bedrockShop.shop.getVendor().getUniqueId());
        assertEquals(2, fixture.lookups.get());
    }

    @Test
    void stockCapacityAndPersistedPriceChangesStillReachTheDisplay() throws Exception {
        DisplayShop shop = fixture.add(JAVA_OWNER);
        shop.contents[0] = new BoundaryItem(fixture.material, 16, null);
        fixture.ticks(40);
        assertTrue(shop.text().contains("Stock 16 Space 1712"), shop.text());
        assertTrue(shop.text().contains("Buy $5.0 Sell $2.0"), shop.text());
        shop.contents[0] = new BoundaryItem(fixture.material, 8, null);
        fixture.ticks(40);
        assertTrue(shop.text().contains("Stock 8 Space 1720"), shop.text());
        shop.shop.applyTerms(new ShopTerms(4, 12, 3));
        assertTrue(shop.text().contains("4 x"), shop.text());
        assertTrue(shop.text().contains("Buy $12.0 Sell $3.0"), shop.text());
        Arrays.fill(shop.contents, new BoundaryItem(fixture.material, 64, null));
        fixture.ticks(40);
        assertTrue(shop.text().contains("Space 0"), shop.text());
        Arrays.fill(shop.contents, null);
        fixture.ticks(40);
        assertTrue(shop.text().contains("Sold out"), shop.text());
    }

    @Test
    void formatLanguageItemAndPanelChangesInvalidateOnlyTheRequiredWork() throws Exception {
        DisplayShop shop = fixture.add(JAVA_OWNER);
        fixture.ticks(40);
        clearInvocations(shop.display, fixture.format);
        Config.hologramBackgroundColor = 0x70315B7D;
        fixture.ticks(40);
        verify(shop.display).setBackgroundColor(Color.fromARGB(0x70315B7D));
        verify(fixture.format, never()).getFormat(anyInt(), anyMap(), anyMap());
        fixture.loadFormat(FORMAT.replace("Buy ", "Updated Buy "));
        fixture.ticks(40);
        assertTrue(shop.text().contains("Updated Buy"));
        fixture.language("Uitverkocht", Map.of("stone", "Steen"));
        fixture.ticks(40);
        assertTrue(shop.text().contains("Uitverkocht"));
        assertTrue(shop.text().contains("Steen"));
        ItemMeta renamed = mock(ItemMeta.class);
        when(renamed.hasDisplayName()).thenReturn(true);
        when(renamed.displayName()).thenReturn(Component.text("Custom product"));
        shop.shop.getProduct().getItemStack().setItemMeta(renamed);
        fixture.ticks(40);
        assertTrue(shop.text().contains("Custom product"));
    }

    @Test
    void playerHeadNamesUseOnlyTheStoredItemProfileDuringActualRefreshes() throws Exception {
        DisplayShop shop = fixture.add(JAVA_OWNER);
        SkullMeta skull = mock(SkullMeta.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(skull.hasOwner()).thenReturn(true);
        when(skull.getPlayerProfile()).thenReturn(profile);
        when(profile.getName()).thenReturn(".HeadOwner");
        shop.shop.getProduct().getItemStack().setItemMeta(skull);
        fixture.ticks(4_040);
        assertTrue(shop.text().contains(".HeadOwner"), shop.text());
        verify(skull, never()).getOwningPlayer();
        verify(profile, never()).completeFromCache();
        verify(profile, never()).update();
        verify(profile, never()).complete(anyBoolean());
    }

    @Test
    void removalAndReplacementDoNotReuseTheOldOwnersTextOrPendingDisplayWork() throws Exception {
        fixture.localNames.put(JAVA_OWNER, "FirstOwner");
        fixture.localNames.put(BEDROCK_OWNER, ".SecondOwner");
        DisplayShop old = fixture.add(JAVA_OWNER);
        fixture.ticks(40);
        fixture.shops.removeShop(old.shop, false);
        clearInvocations(old.display);
        DisplayShop replacement = fixture.add(BEDROCK_OWNER);
        fixture.ticks(40);
        assertTrue(replacement.text().contains(".SecondOwner"));
        assertFalse(replacement.text().contains("FirstOwner"));
        assertFalse(old.shop.hasOwnerNameChange());
        verify(old.display, never()).text(any(Component.class));
        fixture.add(JAVA_OWNER);
        fixture.ticks(40);
        assertEquals(3, fixture.lookups.get());
    }

    @Test
    void shutdownCancelsTheSweepAndReleasesHologramsAndOwnerEntries() throws Exception {
        DisplayShop shop = fixture.add(JAVA_OWNER);
        fixture.ticks(40);
        setField(ShopChest.class, fixture.plugin, "shopUtils", fixture.shops);
        setField(ShopChest.class, fixture.plugin, "updater", fixture.updater);
        setField(ShopChest.class, fixture.plugin, "ownerNames", fixture.names);
        doCallRealMethod().when(fixture.plugin).onDisable();
        fixture.plugin.onDisable();
        verify(fixture.task).cancel();
        verify(shop.display).remove();
        assertFalse(fixture.updater.isRunning());
        assertTrue(fixture.shops.getShops().isEmpty());
        assertFalse(shop.shop.hasHologram());
        assertFalse(shop.shop.hasOwnerNameChange());
        fixture.names.acquire(JAVA_OWNER);
        assertEquals(2, fixture.lookups.get());
    }

    @Test
    void rejectsOffThreadRefreshBeforeReadingInventoryOrDisplayState() throws Exception {
        DisplayShop shop = fixture.add(JAVA_OWNER);
        clearInvocations(shop.inventory, shop.display);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertThrows(IllegalStateException.class, shop.shop::updateHologramText);
        verifyNoInteractions(shop.inventory, shop.display);
    }

    @Test
    void realUpdaterBoundsLargeShopCollectionsAndDiscardsRemovedPendingDisplays() throws Exception {
        List<DisplayShop> shops = new ArrayList<>();
        for (int index = 0; index < 513; index++) shops.add(fixture.add(JAVA_OWNER));
        fixture.ticks(39);
        assertEquals(0, fixture.inventoryReads.get());
        fixture.ticks(1);
        assertEquals(32, fixture.inventoryReads.get());
        DisplayShop survivor = shops.stream().filter(shop -> !shop.text().isEmpty()).findFirst().orElseThrow();
        List<DisplayShop> removed = shops.stream().filter(shop -> shop != survivor).toList();
        // Remove all but one shop during a sweep, including entries not visited yet.
        for (DisplayShop shop : removed) {
            fixture.shops.removeShop(shop.shop, false);
            clearInvocations(shop.display);
        }
        for (int tick = 0; tick < 60; tick++) {
            int before = fixture.inventoryReads.get();
            fixture.ticks(1);
            assertTrue(fixture.inventoryReads.get() - before <= 32);
        }
        for (DisplayShop shop : removed) {
            verify(shop.display, never()).text(any(Component.class));
        }
        assertEquals(1, fixture.shops.getShops().size());
        assertEquals(1, fixture.lookups.get());
    }

    @Test
    void onlineServerIdentityTakesPrecedenceAndVisibilitySurvivesUnchangedRefreshes() throws Exception {
        fixture.localNames.put(BEDROCK_OWNER, ".StaleName");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(BEDROCK_OWNER);
        when(player.getName()).thenReturn(".CurrentName");
        when(fixture.server.getPlayer(BEDROCK_OWNER)).thenReturn(player);
        DisplayShop shop = fixture.add(BEDROCK_OWNER);
        shop.hologram.showPlayer(player);
        fixture.ticks(80);
        assertTrue(shop.text().contains(".CurrentName"));
        verify(fixture.server, never()).createProfileExact(any(), any());
        assertTrue(shop.hologram.isVisible(player));
        verify(player, times(1)).showEntity(fixture.plugin, shop.display);
        verify(player, never()).hideEntity(any(), any());
        shop.hologram.hidePlayer(player);
        assertFalse(shop.hologram.isVisible(player));
        verify(player).hideEntity(fixture.plugin, shop.display);
    }

    @Test
    void mismatchedCachedProfileNeverChangesTheAuthoritativeUuid() throws Exception {
        PlayerProfile wrongProfile = mock(PlayerProfile.class);
        when(wrongProfile.getId()).thenReturn(BEDROCK_OWNER);
        when(wrongProfile.getName()).thenReturn("WrongOwner");
        when(fixture.server.createProfileExact(JAVA_OWNER, null)).thenReturn(wrongProfile);
        DisplayShop shop = fixture.add(JAVA_OWNER);
        fixture.ticks(80);
        assertTrue(shop.text().contains(JAVA_OWNER.toString()));
        assertFalse(shop.text().contains("WrongOwner"));
        verify(wrongProfile, times(1)).completeFromCache();
        verify(wrongProfile, never()).update();
        verify(wrongProfile, never()).complete(anyBoolean());
        assertEquals(JAVA_OWNER, shop.shop.getVendor().getUniqueId());
    }

    @Test
    void recreatedHologramDoesNotInheritOldRenderOrEntityState() throws Exception {
        DisplayShop shop = fixture.add(JAVA_OWNER);
        fixture.ticks(40);
        shop.shop.unloadDisplays();
        fixture.join(JAVA_OWNER, "JoinedWhileUnloaded");
        fixture.localNames.put(JAVA_OWNER, "JoinedWhileUnloaded");
        TextDisplay replacement = mock(TextDisplay.class);
        when(replacement.isValid()).thenReturn(true);
        when(fixture.world.spawn(any(Location.class), eq(TextDisplay.class),
                org.mockito.ArgumentMatchers.<Consumer<TextDisplay>>any())).thenAnswer(call -> {
            Consumer<TextDisplay> initializer = call.getArgument(2);
            initializer.accept(replacement);
            return replacement;
        });
        Hologram hologram = new Hologram(fixture.plugin, new Component[0], shop.shop.getLocation());
        setField(Shop.class, shop.shop, "hologram", hologram);
        clearInvocations(replacement);
        fixture.ticks(40);
        verify(replacement).text(any(Component.class));
        assertTrue(hologram.getLines()[0].contains("JoinedWhileUnloaded"));
        assertEquals(2, fixture.lookups.get());
    }

    @Test
    void ownerRequirementsAndPlaceholdersSeeTheSameUpdatedName() throws Exception {
        fixture.loadFormat("""
                lines:
                  0:
                    options:
                      named:
                        requirements:
                          - VENDOR == ".NewName"
                        format: "Matched %VENDOR%"
                      default:
                        format: "Other %VENDOR%"
                """);
        fixture.localNames.put(BEDROCK_OWNER, ".OldName");
        DisplayShop shop = fixture.add(BEDROCK_OWNER);
        shop.shop.updateHologramText();
        assertEquals("Other .OldName", shop.text());
        fixture.join(BEDROCK_OWNER, ".NewName");
        fixture.ticks(40);
        assertEquals("Matched .NewName", shop.text());
    }

    @Test
    void cmiCachedNamesTakePrecedenceOverPaperAndAreSharedAcrossHolograms() throws Exception {
        Plugin cmi = mock(Plugin.class);
        when(cmi.isEnabled()).thenReturn(true);
        when(fixture.server.getPluginManager().getPlugin("CMI")).thenReturn(cmi);
        try (var cachedCmi = mockStatic(CmiCachedOwnerNames.class,
                withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            cachedCmi.when(() -> CmiCachedOwnerNames.find(BEDROCK_OWNER)).thenReturn(".CmiName");
            DisplayShop first = fixture.add(BEDROCK_OWNER);
            fixture.add(BEDROCK_OWNER);
            fixture.ticks(80);
            assertTrue(first.text().contains(".CmiName"));
            cachedCmi.verify(() -> CmiCachedOwnerNames.find(BEDROCK_OWNER), times(1));
            verify(fixture.server, never()).createProfileExact(any(), any());
        }
    }

    @Test
    void unavailableCmiApiFallsBackOnceWithoutRepeatedFailures() throws Exception {
        Plugin cmi = mock(Plugin.class);
        when(cmi.isEnabled()).thenReturn(true);
        when(fixture.server.getPluginManager().getPlugin("CMI")).thenReturn(cmi);
        fixture.localNames.put(JAVA_OWNER, "PaperJava");
        fixture.localNames.put(BEDROCK_OWNER, ".PaperBedrock");
        try (var cachedCmi = mockStatic(CmiCachedOwnerNames.class,
                withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            cachedCmi.when(() -> CmiCachedOwnerNames.find(any(UUID.class)))
                    .thenThrow(new NoSuchMethodError("Unsupported CMI API"));
            DisplayShop javaShop = fixture.add(JAVA_OWNER);
            DisplayShop bedrockShop = fixture.add(BEDROCK_OWNER);
            fixture.ticks(80);
            assertTrue(javaShop.text().contains("PaperJava"));
            assertTrue(bedrockShop.text().contains(".PaperBedrock"));
            cachedCmi.verify(() -> CmiCachedOwnerNames.find(any(UUID.class)), times(1));
            verify(fixture.plugin.getLogger(), times(1)).warning(anyString());
        }
    }

    private final class Fixture {
        final ShopChest plugin = mock(ShopChest.class);
        final Server server = mock(Server.class);
        final World world = mock(World.class);
        final Material material = mock(Material.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        final Map<UUID, String> localNames = new HashMap<>();
        final AtomicInteger lookups = new AtomicInteger();
        final AtomicInteger inventoryReads = new AtomicInteger();
        final LocalOwnerNames localLookup = new LocalOwnerNames(plugin);
        final OwnerNameCache names = new OwnerNameCache(id -> {
            lookups.incrementAndGet();
            return localLookup.apply(id);
        });
        final ShopUtils shops = new ShopUtils(plugin);
        final ShopUpdater updater = new ShopUpdater(plugin);
        final HologramFormat format = spy(new HologramFormat(plugin));
        final BukkitTask task = mock(BukkitTask.class);
        Runnable tick;
        int nextId;

        Fixture() throws Exception {
            setField(JavaPlugin.class, plugin, "server", server);
            setField(JavaPlugin.class, plugin, "dataFolder", directory.toFile());
            setField(ShopChest.class, null, "instance", plugin);
            when(plugin.getOwnerNames()).thenReturn(names);
            when(plugin.getHologramFormat()).thenReturn(format);
            when(plugin.getShopUtils()).thenReturn(shops);
            when(plugin.getUpdater()).thenReturn(updater);
            Logger logger = mock(Logger.class);
            doAnswer(call -> { throw new AssertionError(call.getArgument(0).toString()); })
                    .when(logger).severe(anyString());
            when(plugin.getLogger()).thenReturn(logger);
            when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
            when(server.createProfileExact(any(UUID.class), isNull())).thenAnswer(call -> {
                UUID id = call.getArgument(0);
                PlayerProfile profile = mock(PlayerProfile.class);
                when(profile.getId()).thenReturn(id);
                when(profile.getName()).thenReturn(localNames.get(id));
                return profile;
            });
            when(material.name()).thenReturn("STONE");
            when(material.toString()).thenReturn("STONE");
            when(material.getKey()).thenReturn(NamespacedKey.minecraft("stone"));
            when(world.getUID()).thenReturn(UUID.fromString("b35bb9ed-3da0-4d68-a951-0a92cde19781"));
            Economy economy = mock(Economy.class);
            when(plugin.getEconomy()).thenReturn(economy);
            when(economy.format(anyDouble())).thenAnswer(call -> "$" + call.getArgument(0));
            language("Sold out", Map.of());
            loadFormat(FORMAT);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
                    .thenAnswer(call -> { tick = call.getArgument(1); return task; });
            updater.start();
        }

        void language(String unavailable, Map<String, String> items) {
            String[] messages = new String[Message.values().length];
            messages[Message.HOLOGRAM_OUT_OF_STOCK.ordinal()] = unavailable;
            when(plugin.getLanguageManager()).thenReturn(new LanguageManager(
                    new MessageRegistry(messages, Object::toString), new LocalizedItemNameManager(items)));
        }

        void loadFormat(String yaml) throws Exception {
            Files.writeString(directory.resolve("hologram-format.yml"), yaml);
            format.load();
        }

        DisplayShop add(UUID ownerId) throws Exception {
            OfflinePlayer owner = mock(OfflinePlayer.class);
            when(owner.getUniqueId()).thenReturn(ownerId);
            when(owner.getName()).thenAnswer(call -> { throw new AssertionError("Offline NBT lookup"); });
            TextDisplay display = mock(TextDisplay.class);
            when(display.isValid()).thenReturn(true);
            when(world.spawn(any(Location.class), eq(TextDisplay.class),
                    org.mockito.ArgumentMatchers.<Consumer<TextDisplay>>any())).thenAnswer(call -> {
                Consumer<TextDisplay> initializer = call.getArgument(2);
                initializer.accept(display);
                return display;
            });
            ItemStack[] contents = new ItemStack[27];
            Inventory inventory = mock(Inventory.class);
            when(inventory.getSize()).thenReturn(contents.length);
            when(inventory.getItem(anyInt())).thenAnswer(call -> contents[(int) call.getArgument(0)]);
            InventoryHolder holder = () -> inventory;
            Location location = new Location(world, ++nextId, 64, 0);
            Shop shop = new Shop(nextId, plugin, owner,
                    new ShopProduct(new BoundaryItem(material, 1, null)), location, 5, 2, Shop.ShopType.NORMAL) {
                @Override public InventoryHolder getInventoryHolder() {
                    inventoryReads.incrementAndGet();
                    return holder;
                }
                @Override public Set<Location> getContainerLocations() { return Set.of(location); }
            };
            Hologram hologram = new Hologram(plugin, new Component[0], location);
            setField(Shop.class, shop, "hologram", hologram);
            setField(Shop.class, shop, "created", true);
            shops.addShop(shop, false);
            return new DisplayShop(shop, owner, hologram, display, inventory, contents);
        }

        void join(UUID id, String name) {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn(name);
            new ShopUpdateListener(plugin).onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));
        }

        void ticks(int count) {
            for (int index = 0; index < count; index++) tick.run();
        }
    }

    private record DisplayShop(Shop shop, OfflinePlayer owner, Hologram hologram,
                               TextDisplay display, Inventory inventory, ItemStack[] contents) {
        String text() { return String.join("\n", hologram.getLines()).replaceAll("\u00a7.", ""); }
    }

    /** Minimal registry-free item boundary; real ShopProduct copies and compares it through Paper ItemStack. */
    private static final class BoundaryItem extends ItemStack {
        private final Material material;
        private int amount;
        private ItemMeta meta;

        BoundaryItem(Material material, int amount, ItemMeta meta) {
            this.material = material;
            this.amount = amount;
            this.meta = meta;
        }

        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int value) { amount = value; }
        @Override public int getMaxStackSize() { return 64; }
        @Override public boolean hasItemMeta() { return meta != null; }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public boolean setItemMeta(ItemMeta value) { meta = value; return true; }
        @Override public Map<Enchantment, Integer> getEnchantments() { return Map.of(); }
        @Override public String translationKey() { return "block.minecraft.stone"; }
        @Override public BoundaryItem clone() { return new BoundaryItem(material, amount, meta); }
        @Override public boolean isSimilar(ItemStack other) {
            return other != null && other.getType() == material && Objects.equals(meta, other.getItemMeta());
        }
        @Override public boolean equals(Object other) {
            return other instanceof ItemStack stack && amount == stack.getAmount() && isSimilar(stack);
        }
        @Override public int hashCode() { return Objects.hash(material, amount, meta); }
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
