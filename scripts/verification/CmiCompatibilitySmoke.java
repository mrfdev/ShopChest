import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.display.TextComponentHelper;
import de.epiceric.shopchest.external.cmi.CmiCachedOwnerNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Synthetic fixtures only. Never install on a shared or production server. */
public final class CmiCompatibilitySmoke extends JavaPlugin {
    private record VisualSpec(String name, double x, List<String> lines) {}
    private static final List<VisualSpec> VISUALS = List.of(
            new VisualSpec("shopchest-compat-floating", 4.5,
                    List.of("&aCMI floating ICON", "_", "ICON:diamond", "_")),
            new VisualSpec("shopchest-compat-static", 8.5,
                    List.of("&bCMI static SICON", "SICON:diamond")));
    private final List<Object> holograms = new ArrayList<>();
    private Plugin cmi;
    private Class<?> rawMessageClass;
    private Class<?> hologramClass;
    private int assertions;

    @Override
    public void onEnable() {
        cmi = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("CMI"));
        Plugin cmilib = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("CMILib"));
        try {
            rawMessageClass = Class.forName("net.Zrips.CMILib.RawMessages.RawMessage",
                    true, cmilib.getClass().getClassLoader());
            hologramClass = cmiClass("com.Zrips.CMI.Modules.Holograms.CMIHologram");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("CMI compatibility fixture API unavailable", error);
        }
        Objects.requireNonNull(getCommand("cmicompat")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof ConsoleCommandSender) && !sender.isOp()) return true;
            try {
                require(Bukkit.isPrimaryThread(), "Fixture runs on server thread");
                if (args.length == 1 && args[0].equals("smoke")
                        && sender instanceof ConsoleCommandSender) {
                    smoke();
                } else if (args.length == 1 && args[0].equals("visual") && sender instanceof Player player) {
                    visual(player);
                } else if (args.length == 1 && args[0].equals("preparevisual")) {
                    prepareVisualHolograms(sender);
                } else if (args.length == 1 && args[0].equals("verifyvisual")) {
                    verifyVisualHolograms();
                    getLogger().info("CMI_COMPAT_VISUAL_VERIFY_PASS holograms=2");
                } else if (args.length == 2 && args[0].equals("clicked")
                        && List.of("cmilib", "shopchest").contains(args[1]) && sender instanceof Player player) {
                    player.sendMessage(Component.text("Click received: " + args[1]));
                    getLogger().info("CMI_COMPAT_CLIENT_CLICK source=" + args[1] + " player=" + player.getUniqueId());
                } else {
                    sender.sendMessage("Console: cmicompat smoke/preparevisual/verifyvisual. Player: /cmicompat visual.");
                }
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "CMI_COMPAT_SMOKE_FAIL", error);
                sender.sendMessage("Compatibility fixture failed; see server log.");
            }
            return true;
        });
    }

    @Override
    public void onDisable() {
        try {
            removeHolograms();
        } catch (ReflectiveOperationException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "CMI_COMPAT_SMOKE_FAIL cleanup", error);
        }
    }

    private void smoke() throws ReflectiveOperationException {
        assertions = 0;
        ShopChest shops = (ShopChest) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ShopChest"));
        require(shops.isEnabled(), "ShopChest enabled alongside new CMI/CMILib");
        require("CMIEconomy".equals(shops.getEconomy().getName()), "Vault uses real CMI economy");
        Object worthManager = call(cmi, "getWorthManager");
        ItemStack diamond = ItemStack.of(Material.DIAMOND);
        ItemStack original = diamond.clone();
        Object worth = call(worthManager, "getWorth", ItemStack.class, diamond);
        require(worth != null, "Default diamond worth available");
        Object price = call(worth, "getSellPrice");
        require(price instanceof Double value && Double.isFinite(value) && value > 0,
                "Worth API retains positive boxed Double sell price");
        require(diamond.equals(original), "Worth lookup preserves input stack");
        Object playerManager = call(cmi, "getPlayerManager");
        Map<?, ?> users = (Map<?, ?>) call(playerManager, "getAllUsers");
        for (Map.Entry<?, ?> entry : users.entrySet()) {
            require(entry.getKey().equals(call(entry.getValue(), "getUniqueId")), "Cached owner is keyed by UUID");
        }
        UUID absent = UUID.fromString("00000000-0000-0000-0000-000000000796");
        require(!users.containsKey(absent), "Synthetic unknown UUID is absent");
        require(CmiCachedOwnerNames.find(absent) == null, "Unknown UUID returns no invented owner name");
        require(!users.containsKey(absent), "Owner lookup does not create CMI records");

        Object backend = rawMessageClass.getMethod("createBackend").invoke(null);
        require(backend.getClass().getSimpleName().equals("PaperRawMessage"), "CMILib uses Paper message backend");
        Object message = clickableMessage();
        Component component = rawComponent(message);
        require(plain(component).contains("CMILib click"), "RawMessage retains text");
        require(any(component, part -> ClickEvent.runCommand("/cmicompat clicked cmilib").equals(part.clickEvent())),
                "RawMessage retains run-command action and slash");
        require(any(component, part -> part.hoverEvent() != null
                        && part.hoverEvent().action() == HoverEvent.Action.SHOW_TEXT
                        && plain((Component) part.hoverEvent().value()).contains("CMILib hover")),
                "RawMessage retains hover text");

        Object suggestion = raw("Suggestion");
        call(suggestion, "addSuggestion", "/shops search diamond");
        require(any(rawComponent(suggestion), part ->
                        ClickEvent.suggestCommand("/shops search diamond").equals(part.clickEvent())),
                "RawMessage retains suggested command");
        Object url = raw("Paper documentation");
        call(url, "addUrl", "https://docs.papermc.io/paper/");
        require(any(rawComponent(url), part ->
                        ClickEvent.openUrl("https://docs.papermc.io/paper/").equals(part.clickEvent())),
                "RawMessage retains URL");
        Object item = raw("Diamond item hover");
        ItemStack hoverItem = ItemStack.of(Material.DIAMOND, 2);
        call(item, "addItem", ItemStack.class, hoverItem);
        require(any(rawComponent(item), part -> hoverItem.asHoverEvent().equals(part.hoverEvent())),
                "RawMessage retains exact item hover");

        Object parsed = rawMessageClass.getMethod("translateRawMessage", CommandSender.class, String.class)
                .invoke(null, Bukkit.getConsoleSender(),
                        "<T>Parser click</T><H>Parser hover</H><C>/shops search diamond</C>");
        require(any(rawComponent(parsed), part ->
                        ClickEvent.runCommand("/shops search diamond").equals(part.clickEvent())),
                "CMI rawmsg markup parser retains command");
        require(any(rawComponent(parsed), part -> part.hoverEvent() != null
                        && part.hoverEvent().action() == HoverEvent.Action.SHOW_TEXT),
                "CMI rawmsg markup parser retains hover");

        Class<?> textSettingsClass = cmiClass("com.Zrips.CMI.Modules.Holograms.Settings.CMIHologramTextSettings");
        Class<?> lineClass = cmiClass("com.Zrips.CMI.Modules.Holograms.CMIHologramLine");
        Object settings = textSettingsClass.getConstructor().newInstance();
        Object floating = lineClass.getConstructor(textSettingsClass, String.class).newInstance(settings, "ICON:diamond");
        Object fixed = lineClass.getConstructor(textSettingsClass, String.class).newInstance(settings, "SICON:diamond");
        require(call(floating, "getType").toString().equals("icon"), "ICON selects floating icon");
        require(call(fixed, "getType").toString().equals("staticIcon"), "SICON selects static icon");

        Object hologram = createHologram("shopchest-compat-lifecycle",
                new Location(Bukkit.getWorlds().getFirst(), 12.5, 103, 9.5), List.of("Compatibility fixture", "SICON:diamond"));
        Object manager = call(cmi, "getHologramManager");
        require(call(manager, "getByName", "shopchest-compat-lifecycle") == hologram, "CMI registers fixture hologram");
        removeHolograms();
        require(call(manager, "getByName", "shopchest-compat-lifecycle") == null, "CMI removes fixture hologram");
        getLogger().info("CMI_COMPAT_SMOKE_PASS assertions=" + assertions
                + " backend=PaperRawMessage icon=icon sicon=staticIcon clientVisuals=manual");
    }

    private void visual(Player player) throws ReflectiveOperationException {
        call(clickableMessage(), "show", Player.class, player);
        player.sendMessage(TextComponentHelper.getClickableActionMessage(
                "ShopChest native component", "[ShopChest click]", "ShopChest hover",
                "/cmicompat clicked shopchest"));
        Object suggestion = raw("&b[Suggest ShopChest search]");
        call(suggestion, "addSuggestion", "/shops search diamond");
        call(suggestion, "show", Player.class, player);
        Object item = raw("&b[CMILib diamond item tooltip]");
        call(item, "addItem", ItemStack.class, ItemStack.of(Material.DIAMOND, 2));
        call(item, "show", Player.class, player);
        Object parsed = rawMessageClass.getMethod("translateRawMessage", CommandSender.class, String.class)
                .invoke(null, player, "<T>&a[CMI rawmsg markup click]</T><H>Parser hover</H><C>/cmicompat clicked cmilib</C>");
        call(parsed, "show", Player.class, player);
        prepareVisualHolograms(player);
    }

    private void prepareVisualHolograms(CommandSender sender) throws ReflectiveOperationException {
        Object manager = call(cmi, "getHologramManager");
        for (VisualSpec spec : VISUALS) {
            Object hologram = call(manager, "getByName", spec.name());
            if (hologram == null) {
                hologram = createHologram(spec.name(), visualLocation(spec), spec.lines());
                call(call(hologram, "getSettings"), "setSaveToFile", boolean.class, true);
                // CMI owns these saved synthetic displays through reload and restart.
                holograms.remove(hologram);
            } else {
                verifyVisualHologram(spec, hologram);
            }
        }
        verifyVisualHolograms();
        File savedFile = new File(cmi.getDataFolder(), "Saves/Holograms.yml");
        CompletableFuture<?> saving = (CompletableFuture<?>) call(manager, "saveHologramsAsync");
        saving.thenApplyAsync(saved -> {
            if (!Boolean.TRUE.equals(saved)) throw new IllegalStateException("CMI refused hologram save");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(savedFile);
            for (VisualSpec spec : VISUALS) {
                if (!yaml.getStringList(spec.name() + ".Lines").equals(spec.lines())) {
                    throw new IllegalStateException("Saved fixture content missing or changed: " + spec.name());
                }
            }
            return true;
        }).whenComplete((saved, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null) {
                getLogger().log(java.util.logging.Level.SEVERE, "CMI_COMPAT_SMOKE_FAIL saving visual fixtures", error);
                sender.sendMessage("CMI visual fixture save failed; see server log.");
            } else {
                getLogger().info("CMI_COMPAT_VISUAL_SAVED holograms=2");
                sender.sendMessage(Component.text("CMI icons saved at x=4.5/8.5, y=103, z=9.5. "
                        + "Check their appearance, then run /cmi reload while standing still. "
                        + "They should return without moving. /cmicompat verifyvisual checks the loaded fixtures."));
            }
        }));
    }

    private void verifyVisualHolograms() throws ReflectiveOperationException {
        Object manager = call(cmi, "getHologramManager");
        for (VisualSpec spec : VISUALS) {
            Object hologram = call(manager, "getByName", spec.name());
            require(hologram != null, "Saved fixture exists: " + spec.name());
            verifyVisualHologram(spec, hologram);
        }
    }

    private void verifyVisualHologram(VisualSpec spec, Object hologram) throws ReflectiveOperationException {
        require(spec.lines().equals(call(call(hologram, "getPages"), "getLines")),
                "Only exact owned fixture content is reused: " + spec.name());
        Location actual = (Location) call(hologram, "getLocation");
        Location expected = visualLocation(spec);
        require(actual.getWorld().equals(expected.getWorld()) && actual.distanceSquared(expected) == 0,
                "Owned fixture location retained: " + spec.name());
        require(Boolean.TRUE.equals(call(call(hologram, "getSettings"), "isSaveToFile")),
                "Visual fixture is persistent");
        require(call(hologram, "getType").toString().equals("TextDisplay"), "Visual fixture uses modern CMI renderer");
    }

    private static Location visualLocation(VisualSpec spec) {
        return new Location(Bukkit.getWorlds().getFirst(), spec.x(), 103, 9.5);
    }

    private Object createHologram(String name, Location location, List<String> lines) throws ReflectiveOperationException {
        Object manager = call(cmi, "getHologramManager");
        require(call(manager, "getByName", name) == null, "Fixture name does not replace an existing hologram");
        Object hologram = hologramClass.getConstructor(String.class, Location.class).newInstance(name, location);
        call(call(hologram, "getSettings"), "setSaveToFile", boolean.class, false);
        call(call(hologram, "getPages"), "setLines", List.class, lines);
        require(call(hologram, "getType").toString().equals("TextDisplay"), "CMI uses modern TextDisplay visualization");
        holograms.add(hologram);
        call(manager, "add", hologramClass, hologram);
        return hologram;
    }

    private void removeHolograms() throws ReflectiveOperationException {
        if (holograms.isEmpty()) return;
        Object manager = call(cmi, "getHologramManager");
        for (Object hologram : holograms) call(manager, "remove", hologramClass, hologram);
        holograms.clear();
    }

    private Object clickableMessage() throws ReflectiveOperationException {
        Object message = raw("&a[CMILib click]");
        call(message, "addHover", "&eCMILib hover");
        call(message, "addCommand", "cmicompat clicked cmilib");
        return message;
    }

    private Object raw(String text) throws ReflectiveOperationException {
        return rawMessageClass.getConstructor(String.class).newInstance(text);
    }

    private Component rawComponent(Object message) throws ReflectiveOperationException {
        return GsonComponentSerializer.gson().deserialize((String) call(message, "getRaw"));
    }

    private Class<?> cmiClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, cmi.getClass().getClassLoader());
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static Object call(Object target, String name, String argument) throws ReflectiveOperationException {
        return call(target, name, String.class, argument);
    }

    private static Object call(Object target, String name, Class<?> type, Object argument) throws ReflectiveOperationException {
        return target.getClass().getMethod(name, type).invoke(target, argument);
    }

    private static boolean any(Component component, Predicate<Component> test) {
        return test.test(component) || component.children().stream().anyMatch(child -> any(child, test));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private void require(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
