package de.epiceric.shopchest.diagnostics;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.hologram.HologramTradeAvailability;
import de.epiceric.shopchest.language.item.ItemNameDiagnostics;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.Shop.ShopType;
import de.epiceric.shopchest.sql.DatabaseDiagnostics;
import de.epiceric.shopchest.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public record ShopChestSupportReport(
        List<String> lines,
        List<String> warnings
) {
    private static final List<String> DEPENDENCIES = List.of(
            "Vault",
            "CMI",
            "CMILib",
            "PlaceholderAPI",
            "LuckPerms");

    public ShopChestSupportReport {
        lines = List.copyOf(lines);
        warnings = List.copyOf(warnings);
    }

    public static ShopChestSupportReport create(
            ShopChest plugin,
            DatabaseDiagnostics database,
            boolean databaseQueryFailed
    ) {
        final PluginBuildInfo build = PluginBuildInfo.load(plugin);
        final LoadedShopState loaded = inspectLoadedShops(plugin.getShopUtils().getShops());
        final ItemNameDiagnostics itemNames = plugin.getLanguageManager()
                .getItemNameManager()
                .getDiagnostics();
        final List<String> warnings = collectWarnings(
                plugin, build, database, databaseQueryFailed, loaded, itemNames);
        final List<String> lines = new ArrayList<>();

        lines.add("=== ShopChest support report ===");
        lines.add("Generated: " + Instant.now());
        lines.add("Plugin: " + plugin.getPluginMeta().getVersion()
                + " | build " + build.build()
                + " | target Java " + build.javaTarget()
                + " / Paper " + build.paperTarget());
        lines.add("Runtime: " + Bukkit.getName()
                + " " + Bukkit.getMinecraftVersion()
                + " | " + Bukkit.getVersion());
        lines.add("Java: " + System.getProperty("java.version", "unknown")
                + " | " + System.getProperty("java.vm.name", "unknown")
                + " | " + System.getProperty("os.name", "unknown")
                + " " + System.getProperty("os.arch", "unknown"));
        lines.add("Platform: Paper API 26.2+"
                + " | displays TextDisplay + ItemDisplay"
                + " | per-player entity visibility");
        lines.add("Item naming: runtime translation keys " + itemNames.translatableItems()
                + "/" + itemNames.runtimeItems()
                + " | locale overrides " + itemNames.localeOverrides()
                + " | invalid overrides ignored " + itemNames.ignoredOverrides());
        lines.add("Dependencies: " + dependencySummary());
        lines.add("Economy: " + economyName(plugin));
        lines.add("Active hooks: " + activeHookSummary(plugin));
        lines.add("CMI worth advisory: "
                + plugin.getCmiWorthPriceAdvisor().status().summary());
        lines.add("Database: " + Config.databaseType
                + " | initialized " + yesNo(database.initialized())
                + " | connection " + (database.connectionValid() ? "valid" : "unavailable")
                + " | schema " + value(database.schemaVersion())
                + " | latency " + milliseconds(database.latencyMillis()));
        lines.add("Database pool: active " + value(database.activeConnections())
                + " | idle " + value(database.idleConnections())
                + " | total " + value(database.totalConnections())
                + " | waiting " + value(database.waitingThreads()));
        lines.add("Registered shops: total " + value(database.totalShops())
                + " | normal " + value(database.normalShops())
                + " | admin " + value(database.adminShops())
                + " | owners " + value(database.owners())
                + " | economy logs " + value(database.economyLogs()));
        lines.add("Loaded shops: total " + loaded.total()
                + " | normal " + loaded.normal()
                + " | admin " + loaded.admin()
                + " | out of stock " + loaded.outOfStock());
        lines.add("Loaded visuals: holograms " + loaded.holograms()
                + "/" + loaded.total()
                + " | items " + loaded.items() + "/" + loaded.total());
        lines.add("Config: command /" + Config.mainCommandName
                + " | locale " + Config.languageFile
                + " | table prefix " + Config.databaseTablePrefix);
        lines.add("Config: economy log " + onOff(Config.enableEconomyLog)
                + " | debug log " + onOff(Config.enableDebugLog)
                + " | remove-on-error " + onOff(Config.removeShopOnError)
                + " | sight-only " + onOff(Config.onlyShowShopsInSight));
        lines.add("Display config: fixed-facing " + onOff(Config.hologramFixedFacing)
                + " | text-scale " + String.format(Locale.ROOT, "%.2f", Config.hologramTextScale));
        lines.add("Warnings: " + warnings.size());
        warnings.forEach(warning -> lines.add("- " + warning));
        lines.add("=== End ShopChest support report ===");

        return new ShopChestSupportReport(lines, warnings);
    }

    public String plainText() {
        return String.join(System.lineSeparator(), lines);
    }

    private static LoadedShopState inspectLoadedShops(Collection<Shop> loadedShops) {
        final Map<Integer, Shop> uniqueShops = new LinkedHashMap<>();
        for (Shop shop : loadedShops) {
            uniqueShops.putIfAbsent(shop.getID(), shop);
        }

        int normal = 0;
        int admin = 0;
        int holograms = 0;
        int items = 0;
        int outOfStock = 0;

        for (Shop shop : uniqueShops.values()) {
            if (shop.getShopType() == ShopType.ADMIN) {
                admin++;
            } else {
                normal++;
            }
            if (shop.hasHologram()) {
                holograms++;
            }
            if (shop.hasItem()) {
                items++;
            }
            if (isOutOfStock(shop)) {
                outOfStock++;
            }
        }

        return new LoadedShopState(
                uniqueShops.size(), normal, admin, holograms, items, outOfStock);
    }

    private static boolean isOutOfStock(Shop shop) {
        if (shop.getShopType() == ShopType.ADMIN || shop.getBuyPrice() <= 0) {
            return false;
        }

        final InventoryHolder holder = shop.getInventoryHolder();
        if (holder == null) {
            return false;
        }

        final int stock = Utils.getAmount(
                holder.getInventory(),
                shop.getProduct().getItemStack());
        return HologramTradeAvailability.isBuyOutOfStock(
                shop.getBuyPrice(),
                false,
                stock,
                shop.getProduct().getAmount());
    }

    private static List<String> collectWarnings(
            ShopChest plugin,
            PluginBuildInfo build,
            DatabaseDiagnostics database,
            boolean databaseQueryFailed,
            LoadedShopState loaded,
            ItemNameDiagnostics itemNames
    ) {
        final List<String> warnings = new ArrayList<>();
        final Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");

        if (vault == null || !vault.isEnabled()) {
            warnings.add("Vault is missing or disabled; economy trades cannot operate.");
        }
        if (plugin.getEconomy() == null) {
            warnings.add("No Vault economy provider is registered.");
        }
        if (Config.cmiWorthPriceWarningEnabled
                && Bukkit.getPluginManager().isPluginEnabled("CMI")
                && !plugin.getCmiWorthPriceAdvisor().status().active()) {
            warnings.add("CMI is enabled but its worth API is unavailable; price advisories are inactive.");
        }
        if (databaseQueryFailed) {
            warnings.add("Database diagnostics query failed; inspect the server log.");
        } else if (!database.initialized() || !database.connectionValid()) {
            warnings.add("Database is not initialized or its connection is unavailable.");
        }
        if (database.latencyMillis() > 500) {
            warnings.add("Database diagnostics took " + database.latencyMillis()
                    + " ms; investigate storage or database latency.");
        }
        if (database.totalShops() >= 0 && loaded.total() > database.totalShops()) {
            warnings.add("More shops are loaded than registered; reload ShopChest and inspect the database.");
        }
        if (loaded.holograms() < loaded.total()) {
            warnings.add((loaded.total() - loaded.holograms())
                    + " loaded shop(s) have no hologram display.");
        }
        if (loaded.items() < loaded.total()) {
            warnings.add((loaded.total() - loaded.items())
                    + " loaded shop(s) have no item display.");
        }
        if (!Config.enableEconomyLog) {
            warnings.add("Economy logging is disabled; /shops recent cannot record new trades.");
        }
        if (!itemNames.complete()) {
            warnings.add((itemNames.runtimeItems() - itemNames.translatableItems())
                    + " runtime item(s) have no usable translation key: "
                    + summarize(itemNames.missingTranslationKeys()));
        }
        if (itemNames.ignoredOverrides() > 0) {
            warnings.add(itemNames.ignoredOverrides()
                    + " blank or invalid item-name override(s) were ignored.");
        }
        if (!"unknown".equals(build.paperTarget())
                && !Bukkit.getMinecraftVersion().equals(build.paperTarget())) {
            warnings.add("Runtime Minecraft " + Bukkit.getMinecraftVersion()
                    + " differs from the Paper " + build.paperTarget() + " build target.");
        }

        return warnings;
    }

    private static String summarize(List<String> values) {
        if (values.isEmpty()) {
            return "unknown";
        }
        final int visible = Math.min(values.size(), 8);
        final String summary = String.join(", ", values.subList(0, visible));
        return values.size() > visible
                ? summary + " (+" + (values.size() - visible) + " more)"
                : summary;
    }

    private static String dependencySummary() {
        final StringJoiner dependencies = new StringJoiner(", ");
        for (String name : DEPENDENCIES) {
            dependencies.add(pluginStatus(name));
        }
        return dependencies.toString();
    }

    private static String pluginStatus(String name) {
        final Plugin dependency = Bukkit.getPluginManager().getPlugin(name);
        if (dependency == null) {
            return name + " missing";
        }
        return name + " " + dependency.getPluginMeta().getVersion()
                + " " + (dependency.isEnabled() ? "enabled" : "disabled");
    }

    private static String activeHookSummary(ShopChest plugin) {
        final List<String> hooks = new ArrayList<>();
        addHook(hooks, plugin.hasAreaShop(), "AreaShop");
        addHook(hooks, plugin.hasGriefPrevention(), "GriefPrevention");
        addHook(hooks, plugin.hasIslandWorld(), "IslandWorld");
        addHook(hooks, plugin.hasASkyBlock(), "ASkyBlock");
        addHook(hooks, plugin.hasUSkyBlock(), "uSkyBlock");
        addHook(hooks, plugin.hasPlotSquared(), "PlotSquared");
        addHook(hooks, plugin.hasAuthMe(), "AuthMe");
        addHook(hooks, plugin.hasTowny(), "Towny");
        addHook(hooks, plugin.hasWorldGuard(), "WorldGuard");
        addHook(hooks, plugin.hasBentoBox(), "BentoBox");
        return hooks.isEmpty() ? "none" : String.join(", ", hooks);
    }

    private static void addHook(List<String> hooks, boolean active, String pluginName) {
        if (active) {
            hooks.add(pluginStatus(pluginName));
        }
    }

    private static String economyName(ShopChest plugin) {
        return plugin.getEconomy() == null
                ? "unavailable"
                : plugin.getEconomy().getName();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static String value(int value) {
        return value < 0 ? "unavailable" : String.valueOf(value);
    }

    private static String milliseconds(long value) {
        return value < 0 ? "unavailable" : value + " ms";
    }

    private record LoadedShopState(
            int total,
            int normal,
            int admin,
            int holograms,
            int items,
            int outOfStock
    ) {
    }
}
