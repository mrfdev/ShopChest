package de.epiceric.shopchest.config;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.hologram.HologramColorPalette;
import de.epiceric.shopchest.sql.Database;
import de.epiceric.shopchest.utils.ItemUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Config {

    static final float DEFAULT_HOLOGRAM_TEXT_SCALE = 0.5f;
    static final float MINIMUM_HOLOGRAM_TEXT_SCALE = 0.5f;
    static final float MAXIMUM_HOLOGRAM_TEXT_SCALE = 1.25f;
    static final int DEFAULT_HOLOGRAM_MAX_ITEM_DETAIL_ENTRIES = 7;
    static final int DEFAULT_HOLOGRAM_ITEM_DETAILS_PER_LINE = 2;
    static final int DEFAULT_TRADE_INTERACTION_COOLDOWN_MILLIS = 250;
    static final double DEFAULT_CMI_WORTH_LOW_MULTIPLIER = 0.5D;
    static final double DEFAULT_CMI_WORTH_HIGH_MULTIPLIER = 20.0D;
    private static final String SUCCESS_SOUND = "minecraft:entity.experience_orb.pickup";
    private static final String SUCCESS_PARTICLE = "minecraft:happy_villager";
    private static final String FAILURE_SOUND = "minecraft:block.note_block.bass";
    private static final String FAILURE_PARTICLE = "minecraft:smoke";

    /**
     * The item with which a player can click a shop to retrieve information
     **/
    public static ItemStack shopInfoItem;

    /**
     * The default value for the custom WorldGuard flag 'create-shop'
     **/
    public static boolean wgAllowCreateShopDefault;

    /**
     * The default value for the custom WorldGuard flag 'use-admin-shop'
     **/
    public static boolean wgAllowUseAdminShopDefault;

    /**
     * The default value for the custom WorldGuard flag 'use-shop'
     **/
    public static boolean wgAllowUseShopDefault;

    /**
     * The types of town plots residents are allowed to create shops in
     **/
    public static List<String> townyShopPlotsResidents;

    /**
     * The types of town plots the mayor is allowed to create shops in
     **/
    public static List<String> townyShopPlotsMayor;

    /**
     * The types of town plots the king is allowed to create shops in
     **/
    public static List<String> townyShopPlotsKing;

    /**
     * The events of AreaShop when shops in that region should be removed
     **/
    public static List<String> areashopRemoveShopEvents;

    /**
     * The hostname used in ShopChest's MySQL database
     **/
    public static String databaseMySqlHost;

    /**
     * The port used for ShopChest's MySQL database
     **/
    public static int databaseMySqlPort;

    /**
     * The database used for ShopChest's MySQL database
     **/
    public static String databaseMySqlDatabase;

    /**
     * The username used in ShopChest's MySQL database
     **/
    public static String databaseMySqlUsername;

    /**
     * The password used in ShopChest's MySQL database
     **/
    public static String databaseMySqlPassword;

    /**
     * The prefix to be used for database tables
     */
    public static String databaseTablePrefix;

    /**
     * The database type used for ShopChest
     **/
    public static Database.DatabaseType databaseType;

    /**
     * The interval in seconds, a ping is sent to the MySQL server
     **/
    public static int databaseMySqlPingInterval;

    /**
     * <p>The minimum prices for certain items</p>
     * This returns a key set, which contains e.g "STONE", "STONE:1", of the <i>minimum-prices</i> section in ShopChest's config.
     * To actually retrieve the minimum price for an item, you have to get the double {@code minimum-prices.<key>}.
     **/
    public static Set<String> minimumPrices;

    /**
     * <p>The maximum prices for certain items</p>
     * This returns a key set, which contains e.g "STONE", "STONE:1", of the {@code maximum-prices} section in ShopChest's config.
     * To actually retrieve the maximum price for an item, you have to get the double {@code maximum-prices.<key>}.
     **/
    public static Set<String> maximumPrices;

    /**
     * <p>List containing items, of which players can't create a shop</p>
     * If this list contains an item (e.g "STONE", "STONE:1"), it's in the blacklist.
     **/
    public static List<String> blacklist;

    /**
     * Whether prices may contain decimals
     **/
    public static boolean allowDecimalsInPrice;

    /**
     * Whether the buy price of a shop must be greater than or equal the sell price
     **/
    public static boolean buyGreaterOrEqualSell;

    /**
     * Whether normal-shop prices are compared with CMI's configured worth.
     **/
    public static boolean cmiWorthPriceWarningEnabled;

    /**
     * Whether a direct buy-then-/sell profit should be called out.
     **/
    public static boolean cmiWorthWarnResaleRisk;

    /**
     * Multipliers outside this range are considered unusual.
     **/
    public static double cmiWorthLowMultiplier;
    public static double cmiWorthHighMultiplier;

    /**
     * Whether buys and sells must be confirmed
     **/
    public static boolean confirmShopping;

    /**
     * Minimum time between shop trade attempts from the same player
     **/
    public static int tradeInteractionCooldownMillis;

    /**
     * Player-local sound and particle shown after a completed trade
     **/
    public static TradeFeedbackEffect tradeSuccessFeedback;

    /**
     * Player-local sound and particle shown after a failed trade attempt
     **/
    public static TradeFeedbackEffect tradeFailureFeedback;

    /**
     * Whether the shop creation price should be refunded at removal.
     */
    public static boolean refundShopCreation;

    /**
     * Whether the debug log file should be created
     **/
    public static boolean enableDebugLog;

    /**
     * Whether buys and sells should be logged in the database
     **/
    public static boolean enableEconomyLog;

    /**
     * Whether WorldGuard integration should be enabled
     **/
    public static boolean enableWorldGuardIntegration;

    /**
     * <p>Sets the time limit for cleaning up the economy log in days</p>
     * 
     * If this equals to {@code 0}, the economy log will not be cleaned.
     **/
    public static int cleanupEconomyLogDays;

    /**
     * Whether Towny integration should be enabled
     **/
    public static boolean enableTownyIntegration;

    /**
     * Whether AuthMe integration should be enabled
     **/
    public static boolean enableAuthMeIntegration;

    /**
     * Whether PlotSquared integration should be enabled
     **/
    public static boolean enablePlotsquaredIntegration;

    /**
     * Whether uSkyBlock integration should be enabled
     **/
    public static boolean enableUSkyblockIntegration;

    /**
     * Whether ASkyBlock integration should be enabled
     **/
    public static boolean enableASkyblockIntegration;

    /**
     * Whether BentoBox integration should be enabled
     **/
    public static boolean enableBentoBoxIntegration;

    /**
     * Whether IslandWorld integration should be enabled
     **/
    public static boolean enableIslandWorldIntegration;

    /**
     * Whether GriefPrevention integration should be enabled
     **/
    public static boolean enableGriefPreventionIntegration;

    /**
     * Whether AreaShop integration should be enabled
     **/
    public static boolean enableAreaShopIntegration;

    /**
     * Whether the vendor of the shop should get messages about buys and sells
     **/
    public static boolean enableVendorMessages;

    /**
     * Whether the vendor of the shop should get messages on all servers about buys and sells
     **/
    public static boolean enableVendorBungeeMessages;

    /**
     * Whether the extension of a potion or tipped arrow (if available) should be appended to the item name.
     **/
    public static boolean appendPotionLevelToItemName;

    /**
     * Whether players are allowed to sell/buy broken items
     **/
    public static boolean allowBrokenItems;

    /**
     * Whether only the shop a player is pointing at should be shown
     **/
    public static boolean onlyShowShopsInSight;

    /**
     * <p>Whether shops should automatically be removed from the database if an error occurred while loading</p>
     * (e.g. when no chest is found at a shop's location)
     */
    public static boolean removeShopOnError;

    /**
     * Whether the item amount should be calculated to fit the available money or inventory space
     **/
    public static boolean autoCalculateItemAmount;

    /**
     * Whether players should be able to select an item from the creative inventory
     */
    public static boolean creativeSelectItem;

    /**
     * <p>Whether the mouse buttons are inverted</p>
     * <b>Default:</b><br>
     * Right-Click: Buy<br>
     * Left-Click: Sell
     **/
    public static boolean invertMouseButtons;

    /**
     * Whether the hologram's location should be fixed at the bottom
     **/
    public static boolean hologramFixedBottom;

    /**
     * Amount every hologram should be lifted
     **/
    public static double hologramLift;

    /**
     * Maximum TextDisplay line width in client font pixels
     **/
    public static int hologramPanelWidth;

    /**
     * Uniform visual scale of the TextDisplay panel
     **/
    public static float hologramTextScale;

    /**
     * TextDisplay panel background as an ARGB integer
     **/
    public static int hologramBackgroundColor;

    /**
     * Semantic text colors used by shop holograms
     **/
    public static HologramColorPalette hologramColors;

    /**
     * Maximum visible characters in an item name shown on a hologram
     **/
    public static int hologramMaxItemNameLength;

    /**
     * Maximum enchantment and potion detail entries shown on a hologram
     **/
    public static int hologramMaxItemDetailEntries;

    /**
     * Number of enchantment and potion detail entries shown on each line
     **/
    public static int hologramItemDetailsPerLine;

    /**
     * Whether the TextDisplay panel keeps the chest's facing direction
     **/
    public static boolean hologramFixedFacing;

    /**
     * The maximum distance between a player and a shop to see the hologram
     **/
    public static double maximalDistance;

    /**
     * The maximum distance between a player and a shop to see the shop item
     **/
    public static double maximalItemDistance;

    /**
     * The price a player has to pay in order to create a normal shop
     **/
    public static double shopCreationPriceNormal;

    /**
     * The price a player has to pay in order to create an admin shop
     **/
    public static double shopCreationPriceAdmin;

    /**
     * The default shop limit for players whose limit is not set via a permission
     **/
    public static int defaultLimit;

    /**
     * The main command of ShopChest <i>(default: shops)</i>
     **/
    public static String mainCommandName;

    /**
     * The language file to use (e.g <i>en_US</i>, <i>de_DE</i>)
     **/
    public static String languageFile;

    private ShopChest plugin;

    public Config(ShopChest plugin) {
        this.plugin = plugin;

        plugin.saveDefaultConfig();
        addMissingModernDefaults();

        reload(true, true, true);
    }

    /**
     * <p>Set a configuration value</p>
     * <i>Config is automatically reloaded</i>
     *
     * @param property Property to change
     * @param value    Value to set
     */
    public void set(String property, String value) {
        boolean langChange = (property.equalsIgnoreCase("language-file"));
        try {
            int intValue = Integer.parseInt(value);
            plugin.getConfig().set(property, intValue);

            plugin.saveConfig();
            reload(false, langChange, false);

            return;
        } catch (NumberFormatException e) { /* Value not an integer */ }

        try {
            double doubleValue = Double.parseDouble(value);
            plugin.getConfig().set(property, doubleValue);

            plugin.saveConfig();
            reload(false, langChange, false);

            return;
        } catch (NumberFormatException e) { /* Value not a double */ }

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            boolean boolValue = Boolean.parseBoolean(value);
            plugin.getConfig().set(property, boolValue);
        } else {
            plugin.getConfig().set(property, value);
        }

        plugin.saveConfig();

        reload(false, langChange, false);
    }

    /**
     * Add a value to a list in the config.yml.
     * If the list does not exist, a new list with the given value will be created
     *
     * @param property Location of the list
     * @param value    Value to add
     */
    public void add(String property, String value) {
        List<Object> list = new ArrayList<>(plugin.getConfig().getList(property, new ArrayList<>()));

        try {
            int intValue = Integer.parseInt(value);
            list.add(intValue);

            plugin.getConfig().set(property, list);
            plugin.saveConfig();
            reload(false, false, false);

            return;
        } catch (NumberFormatException e) { /* Value not an integer */ }

        try {
            double doubleValue = Double.parseDouble(value);
            list.add(doubleValue);

            plugin.getConfig().set(property, list);
            plugin.saveConfig();
            reload(false, false, false);

            return;
        } catch (NumberFormatException e) { /* Value not a double */ }

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            boolean boolValue = Boolean.parseBoolean(value);
            list.add(boolValue);
        } else {
            list.add(value);
        }

        plugin.getConfig().set(property, list);
        plugin.saveConfig();

        reload(false, false, false);
    }

    public void remove(String property, String value) {
        List<Object> list = new ArrayList<>(plugin.getConfig().getList(property, new ArrayList<>()));

        try {
            int intValue = Integer.parseInt(value);
            list.remove(intValue);

            plugin.getConfig().set(property, list);
            plugin.saveConfig();
            reload(false, false, false);

            return;
        } catch (NumberFormatException e) { /* Value not an integer */ }

        try {
            double doubleValue = Double.parseDouble(value);
            list.remove(doubleValue);

            plugin.getConfig().set(property, list);
            plugin.saveConfig();
            reload(false, false, false);

            return;
        } catch (NumberFormatException e) { /* Value not a double */ }

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            boolean boolValue = Boolean.parseBoolean(value);
            list.remove(boolValue);
        } else {
            list.remove(value);
        }

        plugin.getConfig().set(property, list);
        plugin.saveConfig();

        reload(false, false, false);
    }

    /**
     * Reload the configuration values from config.yml
     * @param firstLoad Whether the config values have not been loaded before
     * @param langReload Whether the language configuration should be reloaded
     * @param showMessages Whether console (error) messages should be shown
     */
    public void reload(boolean firstLoad, boolean langReload, boolean showMessages) {
        plugin.reloadConfig();

        shopInfoItem = ItemUtils.getItemStack(plugin.getConfig().getString("shop-info-item"));
        wgAllowCreateShopDefault = plugin.getConfig().getBoolean("worldguard-default-flag-values.create-shop");
        wgAllowUseAdminShopDefault = plugin.getConfig().getBoolean("worldguard-default-flag-values.use-admin-shop");
        wgAllowUseShopDefault = plugin.getConfig().getBoolean("worldguard-default-flag-values.use-shop");
        townyShopPlotsResidents = plugin.getConfig().getStringList("towny-shop-plots.residents");
        townyShopPlotsMayor = plugin.getConfig().getStringList("towny-shop-plots.mayor");
        townyShopPlotsKing = plugin.getConfig().getStringList("towny-shop-plots.king");
        areashopRemoveShopEvents = plugin.getConfig().getStringList("areashop-remove-shops");
        databaseMySqlPingInterval = plugin.getConfig().getInt("database.mysql.ping-interval");
        databaseMySqlHost = plugin.getConfig().getString("database.mysql.hostname");
        databaseMySqlPort = plugin.getConfig().getInt("database.mysql.port");
        databaseMySqlDatabase = plugin.getConfig().getString("database.mysql.database");
        databaseMySqlUsername = plugin.getConfig().getString("database.mysql.username");
        databaseMySqlPassword = plugin.getConfig().getString("database.mysql.password");
        databaseTablePrefix = plugin.getConfig().getString("database.table-prefix");
        databaseType = Database.DatabaseType.valueOf(plugin.getConfig().getString("database.type"));
        minimumPrices = (plugin.getConfig().getConfigurationSection("minimum-prices") == null) ? new HashSet<String>() : plugin.getConfig().getConfigurationSection("minimum-prices").getKeys(true);
        maximumPrices = (plugin.getConfig().getConfigurationSection("maximum-prices") == null) ? new HashSet<String>() : plugin.getConfig().getConfigurationSection("maximum-prices").getKeys(true);
        allowDecimalsInPrice = plugin.getConfig().getBoolean("allow-decimals-in-price");
        allowBrokenItems = plugin.getConfig().getBoolean("allow-broken-items");
        autoCalculateItemAmount = (allowDecimalsInPrice && plugin.getConfig().getBoolean("auto-calculate-item-amount"));
        creativeSelectItem = plugin.getConfig().getBoolean("creative-select-item");
        blacklist = (plugin.getConfig().getStringList("blacklist") == null) ? new ArrayList<String>() : plugin.getConfig().getStringList("blacklist");
        buyGreaterOrEqualSell = plugin.getConfig().getBoolean("buy-greater-or-equal-sell");
        cmiWorthPriceWarningEnabled = plugin.getConfig().getBoolean(
                "cmi-worth-price-warning.enabled", true);
        cmiWorthWarnResaleRisk = plugin.getConfig().getBoolean(
                "cmi-worth-price-warning.warn-resale-risk", true);
        cmiWorthLowMultiplier = normalizeCmiWorthLowMultiplier(plugin.getConfig().getDouble(
                "cmi-worth-price-warning.low-multiplier", DEFAULT_CMI_WORTH_LOW_MULTIPLIER));
        cmiWorthHighMultiplier = normalizeCmiWorthHighMultiplier(plugin.getConfig().getDouble(
                "cmi-worth-price-warning.high-multiplier", DEFAULT_CMI_WORTH_HIGH_MULTIPLIER));
        confirmShopping = plugin.getConfig().getBoolean("confirm-shopping");
        tradeInteractionCooldownMillis = normalizeTradeInteractionCooldownMillis(
                plugin.getConfig().getInt(
                        "trade-interaction-cooldown-milliseconds",
                        DEFAULT_TRADE_INTERACTION_COOLDOWN_MILLIS));
        tradeSuccessFeedback = getTradeFeedbackEffect(
                "success", SUCCESS_SOUND, 0.45, 1.2, SUCCESS_PARTICLE, 4);
        tradeFailureFeedback = getTradeFeedbackEffect(
                "failure", FAILURE_SOUND, 0.35, 0.7, FAILURE_PARTICLE, 3);
        refundShopCreation = plugin.getConfig().getBoolean("refund-shop-creation");
        enableDebugLog = plugin.getConfig().getBoolean("enable-debug-log");
        enableEconomyLog = plugin.getConfig().getBoolean("enable-economy-log");
        cleanupEconomyLogDays = plugin.getConfig().getInt("cleanup-economy-log-days");
        enableWorldGuardIntegration = plugin.getConfig().getBoolean("enable-worldguard-integration");
        enableTownyIntegration = plugin.getConfig().getBoolean("enable-towny-integration");
        enableAuthMeIntegration = plugin.getConfig().getBoolean("enable-authme-integration");
        enablePlotsquaredIntegration = plugin.getConfig().getBoolean("enable-plotsquared-integration");
        enableUSkyblockIntegration = plugin.getConfig().getBoolean("enable-uskyblock-integration");
        enableASkyblockIntegration = plugin.getConfig().getBoolean("enable-askyblock-integration");
        enableBentoBoxIntegration = plugin.getConfig().getBoolean("enable-bentobox-integration");
        enableIslandWorldIntegration = plugin.getConfig().getBoolean("enable-islandworld-integration");
        enableGriefPreventionIntegration = plugin.getConfig().getBoolean("enable-griefprevention-integration");
        enableAreaShopIntegration = plugin.getConfig().getBoolean("enable-areashop-integration");
        enableVendorMessages = plugin.getConfig().getBoolean("enable-vendor-messages");
        enableVendorBungeeMessages = plugin.getConfig().getBoolean("enable-vendor-bungee-messages");
        onlyShowShopsInSight = plugin.getConfig().getBoolean("only-show-shops-in-sight");
        appendPotionLevelToItemName = plugin.getConfig().getBoolean("append-potion-level-to-item-name");
        removeShopOnError = plugin.getConfig().getBoolean("remove-shop-on-error");
        invertMouseButtons = plugin.getConfig().getBoolean("invert-mouse-buttons");
        hologramFixedBottom = plugin.getConfig().getBoolean("hologram-fixed-bottom");
        hologramLift = plugin.getConfig().getDouble("hologram-lift");
        hologramPanelWidth = clamp(plugin.getConfig().getInt("hologram-panel-width", 200), 40, 1024);
        hologramTextScale = normalizeHologramTextScale(
                plugin.getConfig().getDouble("hologram-text-scale", DEFAULT_HOLOGRAM_TEXT_SCALE));
        hologramBackgroundColor = getHologramBackgroundColor();
        hologramColors = HologramColorPalette.load(
                key -> plugin.getConfig().getString(HologramColorPalette.CONFIG_PREFIX + key),
                plugin.getLogger()::warning);
        hologramMaxItemNameLength = clamp(plugin.getConfig().getInt("hologram-max-item-name-length", 48), 0, 256);
        hologramMaxItemDetailEntries = clamp(plugin.getConfig().getInt(
                "hologram-max-item-detail-entries", DEFAULT_HOLOGRAM_MAX_ITEM_DETAIL_ENTRIES), 1, 32);
        hologramItemDetailsPerLine = clamp(plugin.getConfig().getInt(
                "hologram-item-details-per-line", DEFAULT_HOLOGRAM_ITEM_DETAILS_PER_LINE), 1, 4);
        hologramFixedFacing = plugin.getConfig().getBoolean("hologram-fixed-facing", true);
        maximalDistance = plugin.getConfig().getDouble("maximal-distance");
        maximalItemDistance = plugin.getConfig().getDouble("maximal-item-distance");
        shopCreationPriceNormal = plugin.getConfig().getDouble("shop-creation-price.normal");
        shopCreationPriceAdmin = plugin.getConfig().getDouble("shop-creation-price.admin");
        defaultLimit = plugin.getConfig().getInt("shop-limits.default");
        mainCommandName = plugin.getConfig().getString("main-command-name");
        languageFile = plugin.getConfig().getString("language-file");

        if (langReload) {
            plugin.loadLanguages();
        }
    }

    private int getHologramBackgroundColor() {
        final int opacity = clamp(plugin.getConfig().getInt("hologram-background-opacity", 112), 0, 255);
        final String configuredColor = plugin.getConfig().getString("hologram-background-color", "#315B7D");
        final String hexColor = configuredColor == null ? "" : configuredColor.strip().replaceFirst("^#", "");

        if (!hexColor.matches("[0-9a-fA-F]{6}")) {
            plugin.getLogger().warning("Invalid hologram-background-color '" + configuredColor
                    + "'. Using #315B7D.");
            return (opacity << 24) | 0x315B7D;
        }

        return (opacity << 24) | Integer.parseInt(hexColor, 16);
    }

    private TradeFeedbackEffect getTradeFeedbackEffect(
            String outcome,
            String defaultSound,
            double defaultVolume,
            double defaultPitch,
            String defaultParticle,
            int defaultParticleCount) {
        final String prefix = "trade-feedback." + outcome + ".";
        return new TradeFeedbackEffect(
                plugin.getConfig().getBoolean(prefix + "enabled", true),
                getSound(prefix + "sound", defaultSound),
                getBoundedFloat(prefix + "volume", defaultVolume, 0, 2),
                getBoundedFloat(prefix + "pitch", defaultPitch, 0.5, 2),
                getParticle(prefix + "particle", defaultParticle),
                normalizeTradeFeedbackParticleCount(
                        plugin.getConfig().getInt(prefix + "particle-count", defaultParticleCount)));
    }

    private Sound getSound(String path, String fallback) {
        final String configured = plugin.getConfig().getString(path, fallback);
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("none")) {
            return null;
        }

        final NamespacedKey key = NamespacedKey.fromString(configured);
        final Sound sound = key == null ? null : Registry.SOUND_EVENT.get(key);
        if (sound != null) {
            return sound;
        }

        plugin.getLogger().warning("Invalid " + path + " '" + configured + "'. Using " + fallback + ".");
        return Registry.SOUND_EVENT.get(NamespacedKey.fromString(fallback));
    }

    private Particle getParticle(String path, String fallback) {
        final String configured = plugin.getConfig().getString(path, fallback);
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("none")) {
            return null;
        }

        final NamespacedKey key = NamespacedKey.fromString(configured);
        final Particle particle = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (particle != null && particle.getDataType() == Void.class) {
            return particle;
        }

        plugin.getLogger().warning("Invalid data-free " + path + " '" + configured
                + "'. Using " + fallback + ".");
        return Registry.PARTICLE_TYPE.get(NamespacedKey.fromString(fallback));
    }

    private float getBoundedFloat(String path, double fallback, double minimum, double maximum) {
        final double configured = plugin.getConfig().getDouble(path, fallback);
        return normalizeTradeFeedbackValue(configured, fallback, minimum, maximum);
    }

    private void addMissingModernDefaults() {
        boolean changed = false;
        if (!plugin.getConfig().contains("hologram-text-scale", true)) {
            plugin.getConfig().set("hologram-text-scale", DEFAULT_HOLOGRAM_TEXT_SCALE);
            changed = true;
        }
        changed |= addDefaultIfMissing(
                "hologram-max-item-detail-entries", DEFAULT_HOLOGRAM_MAX_ITEM_DETAIL_ENTRIES);
        changed |= addDefaultIfMissing(
                "hologram-item-details-per-line", DEFAULT_HOLOGRAM_ITEM_DETAILS_PER_LINE);
        for (HologramColorPalette.Role role : HologramColorPalette.Role.values()) {
            final String path = HologramColorPalette.CONFIG_PREFIX + role.configKey();
            if (!plugin.getConfig().contains(path, true)) {
                plugin.getConfig().set(path, role.defaultHex());
                changed = true;
            }
        }
        changed |= addDefaultIfMissing("trade-feedback.success.enabled", true);
        changed |= addDefaultIfMissing("trade-feedback.success.sound", SUCCESS_SOUND);
        changed |= addDefaultIfMissing("trade-feedback.success.volume", 0.45);
        changed |= addDefaultIfMissing("trade-feedback.success.pitch", 1.2);
        changed |= addDefaultIfMissing("trade-feedback.success.particle", SUCCESS_PARTICLE);
        changed |= addDefaultIfMissing("trade-feedback.success.particle-count", 4);
        changed |= addDefaultIfMissing("trade-feedback.failure.enabled", true);
        changed |= addDefaultIfMissing("trade-feedback.failure.sound", FAILURE_SOUND);
        changed |= addDefaultIfMissing("trade-feedback.failure.volume", 0.35);
        changed |= addDefaultIfMissing("trade-feedback.failure.pitch", 0.7);
        changed |= addDefaultIfMissing("trade-feedback.failure.particle", FAILURE_PARTICLE);
        changed |= addDefaultIfMissing("trade-feedback.failure.particle-count", 3);
        changed |= addDefaultIfMissing(
                "trade-interaction-cooldown-milliseconds",
                DEFAULT_TRADE_INTERACTION_COOLDOWN_MILLIS);
        changed |= addDefaultIfMissing("cmi-worth-price-warning.enabled", true);
        changed |= addDefaultIfMissing("cmi-worth-price-warning.warn-resale-risk", true);
        changed |= addDefaultIfMissing(
                "cmi-worth-price-warning.low-multiplier", DEFAULT_CMI_WORTH_LOW_MULTIPLIER);
        changed |= addDefaultIfMissing(
                "cmi-worth-price-warning.high-multiplier", DEFAULT_CMI_WORTH_HIGH_MULTIPLIER);
        if (changed) {
            plugin.saveConfig();
        }
    }

    private boolean addDefaultIfMissing(String path, Object value) {
        if (plugin.getConfig().contains(path, true)) {
            return false;
        }
        plugin.getConfig().set(path, value);
        return true;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    static float normalizeHologramTextScale(double value) {
        if (!Double.isFinite(value)) {
            return DEFAULT_HOLOGRAM_TEXT_SCALE;
        }
        return (float) Math.max(MINIMUM_HOLOGRAM_TEXT_SCALE,
                Math.min(value, MAXIMUM_HOLOGRAM_TEXT_SCALE));
    }

    static float normalizeTradeFeedbackValue(double value, double fallback, double minimum, double maximum) {
        final double finiteValue = Double.isFinite(value) ? value : fallback;
        return (float) Math.max(minimum, Math.min(finiteValue, maximum));
    }

    static int normalizeTradeFeedbackParticleCount(int value) {
        return clamp(value, 0, 16);
    }

    static int normalizeTradeInteractionCooldownMillis(int value) {
        return clamp(value, 0, 5_000);
    }

    static double normalizeCmiWorthLowMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return DEFAULT_CMI_WORTH_LOW_MULTIPLIER;
        }
        return Math.max(0.01D, Math.min(value, 1.0D));
    }

    static double normalizeCmiWorthHighMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return DEFAULT_CMI_WORTH_HIGH_MULTIPLIER;
        }
        return Math.max(1.0D, Math.min(value, 10_000.0D));
    }

}
