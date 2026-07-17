package de.epiceric.shopchest;

import com.palmergames.bukkit.towny.Towny;
import com.wasteofplastic.askyblock.ASkyBlock;
import de.epiceric.shopchest.command.ShopCommand;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.hologram.HologramFormat;
import de.epiceric.shopchest.external.BentoBoxShopFlag;
import de.epiceric.shopchest.external.PlotSquaredOldShopFlag;
import de.epiceric.shopchest.external.PlotSquaredShopFlag;
import de.epiceric.shopchest.external.WorldGuardShopFlag;
import de.epiceric.shopchest.external.listeners.*;
import de.epiceric.shopchest.language.LanguageLoader;
import de.epiceric.shopchest.language.LanguageManager;
import de.epiceric.shopchest.listeners.BentoBoxListener;
import de.epiceric.shopchest.listeners.WorldGuardListener;
import de.epiceric.shopchest.listeners.*;
import de.epiceric.shopchest.nms.Platform;
import de.epiceric.shopchest.nms.PlatformLoader;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.sql.Database;
import de.epiceric.shopchest.sql.MySQL;
import de.epiceric.shopchest.sql.SQLite;
import de.epiceric.shopchest.utils.*;
import fr.xephi.authme.AuthMe;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.wiefferink.areashop.AreaShop;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import pl.islandworld.IslandWorld;
import us.talabrek.ultimateskyblock.api.uSkyBlockAPI;
import world.bentobox.bentobox.BentoBox;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ShopChest extends JavaPlugin {

    private static ShopChest instance;

    private Config config;
    private Platform platform;
    private LanguageManager languageManager;
    private HologramFormat hologramFormat;
    private ShopCommand shopCommand;
    private Economy econ = null;
    private Database database;
    private ShopUtils shopUtils;
    private FileWriter fw;
    private Plugin worldGuard;
    private Towny towny;
    private AuthMe authMe;
    private uSkyBlockAPI uSkyBlock;
    private ASkyBlock aSkyBlock;
    private IslandWorld islandWorld;
    private GriefPrevention griefPrevention;
    private AreaShop areaShop;
    private BentoBox bentoBox;
    private ShopUpdater updater;
    private ExecutorService shopCreationThreadPool;

    /**
     * @return An instance of ShopChest
     */
    public static ShopChest getInstance() {
        return instance;
    }

    /**
     * Sets up the economy of Vault
     * @return Whether an economy plugin has been registered
     */
    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public void onLoad() {
        instance = this;

        config = new Config(this);

        if (Config.enableDebugLog) {
            File debugLogFile = new File(getDataFolder(), "debug.txt");

            try {
                if (!debugLogFile.exists()) {
                    debugLogFile.createNewFile();
                }

                new PrintWriter(debugLogFile).close();

                fw = new FileWriter(debugLogFile, true);
            } catch (IOException e) {
                getLogger().info("Failed to instantiate FileWriter");
                e.printStackTrace();
            }
        }

        debug("Loading ShopChest version " + getPluginMeta().getVersion());

        worldGuard = Bukkit.getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard != null) {
            WorldGuardShopFlag.register(this);
        }
    }

    @Override
    public void onEnable() {
        debug("Enabling ShopChest version " + getPluginMeta().getVersion());

        if (!getServer().getPluginManager().isPluginEnabled("Vault")) {
            debug("Could not find plugin \"Vault\"");
            getLogger().severe("Could not find plugin \"Vault\"");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupEconomy()) {
            debug("Could not find any Vault economy dependency!");
            getLogger().severe("Could not find any Vault economy dependency!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Load NMS
        final PlatformLoader platformLoader = new PlatformLoader();
        try {
            platform = platformLoader.loadPlatform();
        } catch (RuntimeException e) {
            debug(e.getMessage());
            debug("Disabling the plugin");
            getLogger().warning(e.getMessage());
            getLogger().warning("Disabling the plugin");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        shopUtils = new ShopUtils(this);

        File hologramFormatFile = new File(getDataFolder(), "hologram-format.yml");
        if (!hologramFormatFile.exists()) {
            saveResource("hologram-format.yml", false);
        }

        hologramFormat = new HologramFormat(this);
        hologramFormat.load();
        shopCommand = new ShopCommand(this);
        shopCreationThreadPool = new ThreadPoolExecutor(0, 8,
                5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        
        loadExternalPlugins();
        initDatabase();
        registerListeners();
        registerExternalListeners();
        initializeShops();

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        updater = new ShopUpdater(this);
        updater.start();
    }

    @Override
    public void onDisable() {
        debug("Disabling ShopChest...");

        if (shopUtils == null) {
            // Plugin has not been fully enabled (probably due to errors),
            // so only close file writer.
            if (fw != null && Config.enableDebugLog) {
                try {
                    fw.close();
                } catch (IOException e) {
                    getLogger().severe("Failed to close FileWriter");
                    e.printStackTrace();
                }
            }
            return;
        }

        if (getShopCommand() != null) {
            getShopCommand().unregister();
        }

        ClickType.clear();

        if (updater != null) {
            debug("Stopping updater");
            updater.stop();
        }

        if (shopCreationThreadPool != null) {
            shopCreationThreadPool.shutdown();
        }

        shopUtils.removeShops();
        debug("Removed shops");

        if (database != null && database.isInitialized()) {
            if (database instanceof SQLite) {
                ((SQLite) database).vacuum();
            }

            database.disconnect();
        }

        if (fw != null && Config.enableDebugLog) {
            try {
                fw.close();
            } catch (IOException e) {
                getLogger().severe("Failed to close FileWriter");
                e.printStackTrace();
            }
        }
    }

    /**
     * Load every language files. It needs to be called after the initialization of the configuration
     */
    public void loadLanguages() {
        final LanguageLoader languageLoader = new LanguageLoader(this, Config.languageFile);
        languageManager = languageLoader.loadLanguageManager();
    }

    private void loadExternalPlugins() {
        Plugin townyPlugin = Bukkit.getServer().getPluginManager().getPlugin("Towny");
        if (townyPlugin instanceof Towny) {
            towny = (Towny) townyPlugin;
        }

        Plugin authMePlugin = Bukkit.getServer().getPluginManager().getPlugin("AuthMe");
        if (authMePlugin instanceof AuthMe) {
            authMe = (AuthMe) authMePlugin;
        }

        Plugin uSkyBlockPlugin = Bukkit.getServer().getPluginManager().getPlugin("uSkyBlock");
        if (uSkyBlockPlugin instanceof uSkyBlockAPI) {
            uSkyBlock = (uSkyBlockAPI) uSkyBlockPlugin;
        }

        Plugin aSkyBlockPlugin = Bukkit.getServer().getPluginManager().getPlugin("ASkyBlock");
        if (aSkyBlockPlugin instanceof ASkyBlock) {
            aSkyBlock = (ASkyBlock) aSkyBlockPlugin;
        }

        Plugin islandWorldPlugin = Bukkit.getServer().getPluginManager().getPlugin("IslandWorld");
        if (islandWorldPlugin instanceof IslandWorld) {
            islandWorld = (IslandWorld) islandWorldPlugin;
        }

        Plugin griefPreventionPlugin = Bukkit.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (griefPreventionPlugin instanceof GriefPrevention) {
            griefPrevention = (GriefPrevention) griefPreventionPlugin;
        }

        Plugin areaShopPlugin = Bukkit.getServer().getPluginManager().getPlugin("AreaShop");
        if (areaShopPlugin instanceof AreaShop) {
            areaShop = (AreaShop) areaShopPlugin;
        }

        Plugin bentoBoxPlugin = getServer().getPluginManager().getPlugin("BentoBox");
        if (bentoBoxPlugin instanceof BentoBox) {
            bentoBox = (BentoBox) bentoBoxPlugin;
        }

        if (hasWorldGuard()) {
            WorldGuardWrapper.getInstance().registerEvents(this);
        }

        if (hasPlotSquared()) {
            try {
                Class.forName("com.plotsquared.core.PlotSquared");
                PlotSquaredShopFlag.register(this);
            } catch (ClassNotFoundException ex) {
                PlotSquaredOldShopFlag.register(this);
            }
        }

        if (hasBentoBox()) {
            BentoBoxShopFlag.register(this);
        }
    }

    private void initDatabase() {
        if (Config.databaseType == Database.DatabaseType.SQLite) {
            debug("Using database type: SQLite");
            getLogger().info("Using SQLite");
            database = new SQLite(this);
        } else {
            debug("Using database type: MySQL");
            getLogger().info("Using MySQL");
            database = new MySQL(this);
            if (Config.databaseMySqlPingInterval > 0) {
                Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                    @Override
                    public void run() {
                        if (database instanceof MySQL) {
                            ((MySQL) database).ping();
                        }
                    }
                }, Config.databaseMySqlPingInterval * 20L, Config.databaseMySqlPingInterval * 20L);
            }
        }
    }

    private void registerListeners() {
        debug("Registering listeners...");
        getServer().getPluginManager().registerEvents(new ShopUpdateListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopItemListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new NotifyPlayerOnJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ChestProtectListener(this), this);
        getServer().getPluginManager().registerEvents(new CreativeModeListener(this), this);

        if (Utils.getMajorVersion() != 8 || Utils.getRevision() != 1) {
            getServer().getPluginManager().registerEvents(new BlockExplodeListener(this), this);
        }

        if (hasWorldGuard()) {
            getServer().getPluginManager().registerEvents(new WorldGuardListener(this), this);

            if (hasAreaShop()) {
                getServer().getPluginManager().registerEvents(new AreaShopListener(this), this);
            }
        }

        if (hasBentoBox()) {
            getServer().getPluginManager().registerEvents(new BentoBoxListener(this), this);
        }
    }

    private void registerExternalListeners() {
        if (hasASkyBlock())
            getServer().getPluginManager().registerEvents(new ASkyBlockListener(this), this);
        if (hasGriefPrevention())
            getServer().getPluginManager().registerEvents(new GriefPreventionListener(this), this);
        if (hasIslandWorld())
            getServer().getPluginManager().registerEvents(new IslandWorldListener(this), this);
        if (hasPlotSquared())
            getServer().getPluginManager().registerEvents(new PlotSquaredListener(this), this);
        if (hasTowny())
            getServer().getPluginManager().registerEvents(new TownyListener(this), this);
        if (hasUSkyBlock())
            getServer().getPluginManager().registerEvents(new USkyBlockListener(this), this);
        if (hasWorldGuard())
            getServer().getPluginManager().registerEvents(new de.epiceric.shopchest.external.listeners.WorldGuardListener(this), this);
        if (hasBentoBox())
            getServer().getPluginManager().registerEvents(new de.epiceric.shopchest.external.listeners.BentoBoxListener(this), this);
    }

    /**
     * Initializes the shops
     */
    private void initializeShops() {
        getShopDatabase().connect(new Callback<Integer>(this) {
            @Override
            public void onResult(Integer result) {
                Chunk[] loadedChunks = getServer().getWorlds().stream().map(World::getLoadedChunks)
                        .flatMap(Stream::of).toArray(Chunk[]::new);

                shopUtils.loadShopAmounts(new Callback<Map<UUID,Integer>>(ShopChest.this) {
                    @Override
                    public void onResult(Map<UUID, Integer> result) {
                        getLogger().info("Loaded shop amounts");
                        debug("Loaded shop amounts");
                    }
                    
                    @Override
                    public void onError(Throwable throwable) {
                        getLogger().severe("Failed to load shop amounts. Shop limits will not be working correctly!");
                        if (throwable != null) getLogger().severe(throwable.getMessage());
                    }
                });

                shopUtils.loadShops(loadedChunks, new Callback<Integer>(ShopChest.this) {
                    @Override
                    public void onResult(Integer result) {
                        getLogger().info("Loaded " + result + " shops in already loaded chunks");
                        debug("Loaded " + result + " shops in already loaded chunks");
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        getLogger().severe("Failed to load shops in already loaded chunks");
                        if (throwable != null) getLogger().severe(throwable.getMessage());
                    }
                });
            }

            @Override
            public void onError(Throwable throwable) {
                // Database connection probably failed => disable plugin to prevent more errors
                getLogger().severe("No database access. Disabling ShopChest");
                if (throwable != null) getLogger().severe(throwable.getMessage());
                getServer().getPluginManager().disablePlugin(ShopChest.this);
            }
        });
    }

    /**
     * Print a message to the <i>/plugins/ShopChest/debug.txt</i> file
     * @param message Message to print
     */
    public void debug(String message) {
        if (Config.enableDebugLog && fw != null) {
            try {
                Calendar c = Calendar.getInstance();
                String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(c.getTime());
                fw.write(String.format("[%s] %s\r\n", timestamp, message));
                fw.flush();
            } catch (IOException e) {
                getLogger().severe("Failed to print debug message.");
                e.printStackTrace();
            }
        }
    }

    /**
     * Print a {@link Throwable}'s stacktrace to the <i>/plugins/ShopChest/debug.txt</i> file
     * @param throwable {@link Throwable} whose stacktrace will be printed
     */
    public void debug(Throwable throwable) {
        if (Config.enableDebugLog && fw != null) {
            PrintWriter pw = new PrintWriter(fw);
            throwable.printStackTrace(pw);
            pw.flush();
        }
    }

    /**
     * @return A thread pool for executing shop creation tasks
     */
    public ExecutorService getShopCreationThreadPool() {
        return shopCreationThreadPool;
    }

    public Platform getPlatform() {
        return platform;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public HologramFormat getHologramFormat() {
        return hologramFormat;
    }

    public ShopCommand getShopCommand() {
        return shopCommand;
    }

    /**
     * @return The {@link ShopUpdater} that schedules hologram and item updates
     */
    public ShopUpdater getUpdater() {
        return updater;
    }

    /**
     * @return Whether the plugin 'AreaShop' is enabled
     */
    public boolean hasAreaShop() {
        return Config.enableAreaShopIntegration && areaShop != null && areaShop.isEnabled();
    }

    /**
     * @return Whether the plugin 'GriefPrevention' is enabled
     */
    public boolean hasGriefPrevention() {
        return Config.enableGriefPreventionIntegration && griefPrevention != null && griefPrevention.isEnabled();
    }

    /**
     * @return An instance of {@link GriefPrevention} or {@code null} if GriefPrevention is not enabled
     */
    public GriefPrevention getGriefPrevention() {
        return griefPrevention;
    }

    /**
     * @return Whether the plugin 'IslandWorld' is enabled
     */
    public boolean hasIslandWorld() {
        return Config.enableIslandWorldIntegration && islandWorld != null && islandWorld.isEnabled();
    }
    /**
     * @return Whether the plugin 'ASkyBlock' is enabled
     */
    public boolean hasASkyBlock() {
        return Config.enableASkyblockIntegration && aSkyBlock != null && aSkyBlock.isEnabled();
    }

    /**
     * @return Whether the plugin 'uSkyBlock' is enabled
     */
    public boolean hasUSkyBlock() {
        return Config.enableUSkyblockIntegration && uSkyBlock != null && uSkyBlock.isEnabled();
    }

    /**
     * @return An instance of {@link uSkyBlockAPI} or {@code null} if uSkyBlock is not enabled
     */
    public uSkyBlockAPI getUSkyBlock() {
        return uSkyBlock;
    }

    /**
     * @return Whether the plugin 'PlotSquared' is enabled
     */
    public boolean hasPlotSquared() {
        if (!Config.enablePlotsquaredIntegration) {
            return false;
        }

        if (Utils.getMajorVersion() < 13) {
            // Supported PlotSquared versions don't support versions below 1.13
            return false;
        }
        Plugin p = getServer().getPluginManager().getPlugin("PlotSquared");
        return p != null && p.isEnabled();
    }

    /**
     * @return Whether the plugin 'AuthMe' is enabled
     */
    public boolean hasAuthMe() {
        return Config.enableAuthMeIntegration && authMe != null && authMe.isEnabled();
    }
    /**
     * @return Whether the plugin 'Towny' is enabled
     */
    public boolean hasTowny() {
        return Config.enableTownyIntegration && towny != null && towny.isEnabled();
    }

    /**
     * @return Whether the plugin 'WorldGuard' is enabled
     */
    public boolean hasWorldGuard() {
        return Config.enableWorldGuardIntegration && worldGuard != null && worldGuard.isEnabled();
    }

    /**
     * @return Whether the plugin 'WorldGuard' is enabled
     */
    public boolean hasBentoBox() {
        return Config.enableBentoBoxIntegration && bentoBox != null && bentoBox.isEnabled();
    }

    /**
     * @return ShopChest's {@link ShopUtils} containing some important methods
     */
    public ShopUtils getShopUtils() {
        return shopUtils;
    }

    /**
     * @return Registered Economy of Vault
     */
    public Economy getEconomy() {
        return econ;
    }

    /**
     * @return ShopChest's shop database
     */
    public Database getShopDatabase() {
        return database;
    }

    /**
     * @return The {@link Config} of ShopChest
     */
    public Config getShopChestConfig() {
        return config;
    }
}
