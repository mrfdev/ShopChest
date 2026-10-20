import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Containers.CMIUser;
import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.event.ShopBuySellEvent;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.listeners.ShopInteractListener;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.utils.Permissions;
import de.epiceric.shopchest.utils.Utils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Actual ShopChest trade handler and CMI/Vault transactions, with an explicit
 * synthetic Player boundary. This does not emulate a connected Minecraft client,
 * WorldGuard, authentication, permissions providers, or client feedback rendering.
 * No private methods, CraftBukkit/NMS internals, or mocked economy are used.
 */
public final class CopperLifecycleSmokeTradeProbe {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0009-01faeaf02222");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0009-01faeaf03333");
    private static final String BUYER_NAME = "CopperProbeBuyer";
    private final List<ShopBuySellEvent> events = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private int assertions;

    public static int verify(JavaPlugin fixture, ShopChest plugin, Shop shop) {
        return new CopperLifecycleSmokeTradeProbe().run(fixture, plugin, shop);
    }

    private int run(JavaPlugin fixture, ShopChest plugin, Shop shop) {
        require(Bukkit.isPrimaryThread(), "Trade probe runs on the server thread");
        require(Bukkit.getOnlinePlayers().isEmpty(), "Synthetic trades require no connected players");
        require(shop.getLocation().getWorld().getName().equals("stable-fixture"), "Disposable world only");
        require(shop.getVendor().getUniqueId().equals(OWNER), "Only the fixture's synthetic vendor");
        require(shop.getShopType() == Shop.ShopType.NORMAL, "Normal shop exercises both accounts");
        require(!plugin.hasAuthMe() && !plugin.hasPlotSquared(), "Unsupported external player adapters absent");
        Economy economy = Objects.requireNonNull(plugin.getEconomy());
        require(economy.getName().equals("CMIEconomy"), "Actual CMI economy provider is required");
        cacheAccount(OWNER, "CopperProbeOwner");
        cacheAccount(BUYER, BUYER_NAME);

        Inventory backing = Bukkit.createInventory(null, 45);
        PlayerInventory inventory = playerInventory(backing);
        Player player = player(shop, inventory);
        ShopInteractListener handler = registeredHandler(plugin);
        Listener observer = new Listener() { };
        Bukkit.getPluginManager().registerEvent(ShopBuySellEvent.class, observer, EventPriority.MONITOR,
                (listener, event) -> {
                    if (event instanceof ShopBuySellEvent trade && trade.getPlayer() == player) events.add(trade);
                }, fixture);

        boolean worldGuard = Config.enableWorldGuardIntegration;
        boolean confirmation = Config.confirmShopping;
        int cooldown = Config.tradeInteractionCooldownMillis;
        try {
            // Synchronous scope only: isolate player-boundary adapters and pacing,
            // leaving ShopChest's actual trade, item, event, and economy code intact.
            Config.enableWorldGuardIntegration = false;
            Config.confirmShopping = false;
            Config.tradeInteractionCooldownMillis = 0;
            Inventory stock = Objects.requireNonNull(shop.getInventory());
            ItemStack item = shop.getProduct().getItemStack();
            int amount = shop.getProduct().getAmount();
            ItemStack[] originalStock = snapshot(stock);
            int originalCount = Utils.getAmount(stock, item);
            require(originalCount >= amount, "Enough authoritative stock for the positive trade");
            String world = shop.getLocation().getWorld().getName();
            double originalBuyerBalance = economy.getBalance(player, world);
            double originalOwnerBalance = economy.getBalance(shop.getVendor(), world);
            fund(economy, player, world, shop.getBuyPrice() * 4 + 100);
            fund(economy, shop.getVendor(), world, shop.getSellPrice() * 4 + 100);
            double buyerBefore = economy.getBalance(player, world);
            double ownerBefore = economy.getBalance(shop.getVendor(), world);

            interact(handler, player, shop, true);
            assertEvent(0, ShopBuySellEvent.Type.BUY, shop, amount, shop.getBuyPrice());
            require(Utils.getAmount(stock, item) == originalCount - amount, "Buy removes exact matching stock");
            require(Utils.getAmount(inventory, item) == amount, "Buy delivers exact component-rich product");
            require(total(inventory) == amount, "Buy does not deliver extra items");
            balance(economy, player, world, buyerBefore - shop.getBuyPrice(), "Buy debits the customer");
            balance(economy, shop.getVendor(), world, ownerBefore + shop.getBuyPrice(), "Buy credits the owner");
            require(economy.getBalance(player, world) + economy.getBalance(shop.getVendor(), world)
                    == buyerBefore + ownerBefore, "Buy conserves combined balances");
            require(nonProductContentsEqual(originalStock, snapshot(stock), item), "Buy preserves unrelated metadata stock");

            interact(handler, player, shop, false);
            assertEvent(1, ShopBuySellEvent.Type.SELL, shop, amount, shop.getSellPrice());
            require(Utils.getAmount(inventory, item) == 0 && total(inventory) == 0, "Sell consumes only delivered items");
            require(Arrays.equals(originalStock, snapshot(stock)), "Buy/sell round trip restores exact stock naturally");
            double buyerAfter = buyerBefore - shop.getBuyPrice() + shop.getSellPrice();
            double ownerAfter = ownerBefore + shop.getBuyPrice() - shop.getSellPrice();
            balance(economy, player, world, buyerAfter, "Sell credits the customer");
            balance(economy, shop.getVendor(), world, ownerAfter, "Sell debits the owner");
            require(economy.getBalance(player, world) + economy.getBalance(shop.getVendor(), world)
                    == buyerBefore + ownerBefore, "Sell conserves combined balances");

            // Full inventory denial must reach the real failure path, with no event,
            // money movement, or item mutation. The offhand is included in capacity.
            for (int i = 0; i < 36; i++) backing.setItem(i, new ItemStack(Material.BARRIER, 64));
            backing.setItem(40, new ItemStack(Material.BARRIER, 64));
            ItemStack[] fullInventory = snapshot(inventory);
            messages.clear();
            interact(handler, player, shop, true);
            require(messages.contains(plugin.getLanguageManager().getMessageRegistry()
                    .getMessage(Message.NOT_ENOUGH_INVENTORY_SPACE)), "Full inventory reaches the intended denial");
            require(events.size() == 2, "Full inventory creates no trade event");
            require(Arrays.equals(fullInventory, snapshot(inventory)), "Full inventory denial leaves customer inventory intact");
            require(Arrays.equals(originalStock, snapshot(stock)), "Full inventory denial leaves stock intact");
            balance(economy, player, world, buyerAfter, "Full inventory denial leaves customer balance intact");
            balance(economy, shop.getVendor(), world, ownerAfter, "Full inventory denial leaves owner balance intact");
            backing.clear();

            // Remove only the fixture's matching stock as negative-test setup.
            // Always undo that setup; never reset a failed positive transaction.
            try {
                for (int i = 0; i < stock.getSize(); i++) {
                    if (Utils.isItemSimilar(stock.getItem(i), item)) stock.setItem(i, null);
                }
                ItemStack[] unavailable = snapshot(stock);
                messages.clear();
                interact(handler, player, shop, true);
                require(messages.contains(plugin.getLanguageManager().getMessageRegistry()
                        .getMessage(Message.OUT_OF_STOCK)), "Empty stock reaches the intended denial");
                require(events.size() == 2, "Empty stock creates no trade event");
                require(Arrays.equals(unavailable, snapshot(stock)), "Empty stock denial preserves remaining metadata items");
                require(total(inventory) == 0, "Empty stock cannot deliver an item");
                balance(economy, player, world, buyerAfter, "Empty stock denial leaves customer balance intact");
                balance(economy, shop.getVendor(), world, ownerAfter, "Empty stock denial leaves owner balance intact");
            } finally {
                stock.setContents(originalStock);
            }
            require(Arrays.equals(originalStock, snapshot(stock)), "Negative stock setup is fully restored");

            // Explicit fixture funding/compensation happens only after all trade
            // assertions pass, and is verified separately from trade correctness.
            restoreBalance(economy, player, world, originalBuyerBalance);
            restoreBalance(economy, shop.getVendor(), world, originalOwnerBalance);
            fixture.getLogger().info("COPPER_TRADE_PASS shopId=" + shop.getID()
                    + " material=" + shop.getLocation().getBlock().getType()
                    + " buy=" + shop.getBuyPrice() + " sell=" + shop.getSellPrice()
                    + " amount=" + amount + " events=2 negatives=full-inventory,empty-stock"
                    + " boundary=synthetic-player actual=registered-handler,CMI,Vault,Bukkit-inventories");
            return assertions;
        } finally {
            Config.enableWorldGuardIntegration = worldGuard;
            Config.confirmShopping = confirmation;
            Config.tradeInteractionCooldownMillis = cooldown;
            HandlerList.unregisterAll(observer);
        }
    }

    private void cacheAccount(UUID uuid, String name) {
        // Read the local cache directly, so a missing fixture record never triggers
        // username resolution, network identity lookups, or offline player loading.
        var manager = CMI.getInstance().getPlayerManager();
        CMIUser user = manager.getAllUsers().get(uuid);
        if (user == null) {
            user = new CMIUser(uuid);
            user.setName(name);
            user.setFakeAccount(true);
            manager.addUser(user);
        }
        require(user.getUniqueId().equals(uuid) && user.getName(false).equals(name)
                && user.isFakeAccount(), "The real CMI cache contains the expected synthetic UUID account");
        require(manager.getAllUsers().get(uuid) == user, "CMI account is cached before Vault access");
    }

    private ShopInteractListener registeredHandler(ShopChest plugin) {
        Set<ShopInteractListener> handlers = new LinkedHashSet<>();
        for (RegisteredListener registration : HandlerList.getRegisteredListeners(plugin)) {
            if (registration.getListener() instanceof ShopInteractListener handler) handlers.add(handler);
        }
        require(handlers.size() == 1, "Use the one registered ShopChest interaction listener");
        return handlers.iterator().next();
    }

    private void interact(ShopInteractListener handler, Player player, Shop shop, boolean buy) {
        boolean rightClick = buy != Config.invertMouseButtons;
        PlayerInteractEvent event = new PlayerInteractEvent(player,
                rightClick ? Action.RIGHT_CLICK_BLOCK : Action.LEFT_CLICK_BLOCK,
                null, shop.getLocation().getBlock(), BlockFace.UP, EquipmentSlot.HAND);
        handler.onPlayerInteract(event);
        require(event.isCancelled(), "ShopChest consumed the interaction");
    }

    private void assertEvent(int index, ShopBuySellEvent.Type type, Shop shop, int amount, double price) {
        require(events.size() == index + 1, "Exactly one real trade event for " + type);
        ShopBuySellEvent event = events.get(index);
        require(event.getType() == type && event.getShop() == shop && !event.isCancelled(),
                "Trade event references this same live shop and succeeds");
        require(event.getPlayer().getUniqueId().equals(BUYER), "Trade event retains authoritative customer UUID");
        require(event.getNewAmount() == amount && event.getNewPrice() == price, "Trade event uses unchanged quantity and price");
    }

    private void fund(Economy economy, OfflinePlayer player, String world, double minimum) {
        double balance = economy.getBalance(player, world);
        require(Double.isFinite(balance) && balance >= 0, "Synthetic account starts with a valid balance");
        if (balance < minimum) {
            require(economy.depositPlayer(player, world, minimum - balance).transactionSuccess(), "Fund synthetic account through Vault");
            balance(economy, player, world, minimum, "Funding amount is exact");
        }
    }

    private void restoreBalance(Economy economy, OfflinePlayer player, String world, double original) {
        double difference = original - economy.getBalance(player, world);
        EconomyResponse response = difference >= 0
                ? economy.depositPlayer(player, world, difference)
                : economy.withdrawPlayer(player, world, -difference);
        require(response.transactionSuccess(), "Explicit fixture balance compensation succeeds");
        balance(economy, player, world, original, "Fixture balance restored after successful assertions");
    }

    private void balance(Economy economy, OfflinePlayer player, String world, double expected, String context) {
        require(Math.abs(economy.getBalance(player, world) - expected) < 0.000001, context);
    }

    private Player player(Shop shop, PlayerInventory inventory) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> BUYER;
                    case "getName" -> BUYER_NAME;
                    case "getInventory" -> inventory;
                    case "getWorld" -> shop.getLocation().getWorld();
                    case "getLocation" -> {
                        if (args != null && args.length != 0) throw unsupported(method.toString());
                        yield shop.getLocation().clone().add(0.5, 1, 1.5);
                    }
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "isSneaking", "isOnline", "isOp" -> false;
                    case "hasPermission" -> {
                        String permission = args[0] instanceof Permission p ? p.getName() : (String) args[0];
                        yield permission.equals(Permissions.BUY) || permission.equals(Permissions.SELL);
                    }
                    case "sendMessage" -> {
                        if (args.length == 1 && args[0] instanceof String message) messages.add(message);
                        else throw unsupported(method.toString());
                        yield null;
                    }
                    case "updateInventory", "spawnParticle", "playSound" -> null; // Client-only feedback boundary.
                    case "hashCode" -> BUYER.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Copper lifecycle synthetic Player [" + BUYER + "]";
                    default -> throw unsupported(method.toString());
                });
    }

    private PlayerInventory playerInventory(Inventory backing) {
        return (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getItem" -> {
                        if (!(args[0] instanceof Integer slot)) throw unsupported(method.toString());
                        yield backing.getItem(slot);
                    }
                    case "setItem" -> {
                        if (!(args[0] instanceof Integer slot)) throw unsupported(method.toString());
                        backing.setItem(slot, (ItemStack) args[1]);
                        yield null;
                    }
                    case "getStorageContents" -> Arrays.copyOf(backing.getContents(), 36);
                    case "getContents" -> Arrays.copyOf(backing.getContents(), 41);
                    case "getSize" -> 41;
                    case "getItemInMainHand" -> airIfEmpty(backing.getItem(0));
                    case "getItemInOffHand" -> airIfEmpty(backing.getItem(40));
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Copper lifecycle PlayerInventory backed by Bukkit Inventory";
                    default -> throw unsupported(method.toString());
                });
    }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("Unmodelled synthetic player boundary: " + method);
    }

    private static ItemStack airIfEmpty(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private static ItemStack[] snapshot(Inventory inventory) {
        return Arrays.stream(inventory.getContents()).map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }

    private static boolean nonProductContentsEqual(ItemStack[] before, ItemStack[] after, ItemStack product) {
        for (int i = 0; i < before.length; i++) {
            if (!Utils.isItemSimilar(before[i], product) && !Objects.equals(before[i], after[i])) return false;
        }
        return true;
    }

    private static int total(Inventory inventory) {
        return Arrays.stream(inventory.getContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();
    }

    private void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("Copper trade probe: " + message);
    }
}
