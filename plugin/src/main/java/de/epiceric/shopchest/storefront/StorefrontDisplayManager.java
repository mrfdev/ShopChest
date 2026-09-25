package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.display.TextDisplayData;
import de.epiceric.shopchest.display.TextDisplayWrapper;
import de.epiceric.shopchest.utils.Callback;
import de.epiceric.shopchest.utils.ChunkCoordinates;
import de.epiceric.shopchest.utils.ClickType;
import de.epiceric.shopchest.utils.Permissions;
import de.epiceric.shopchest.utils.ShopInteractionCooldown;
import fr.xephi.authme.api.v3.AuthMeApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns persistent Storefront Display anchors and transient TextDisplay entities.
 * Ender Chests managed here are presentation anchors, never tradable shops.
 */
public final class StorefrontDisplayManager implements Listener {

    private static final long PLACEMENT_TIMEOUT_TICKS = 300L;
    private static final double DISPLAY_BASE_Y_OFFSET = -0.85D;
    private static final float DISPLAY_SCALE_FACTOR = 0.8F;

    private final ShopChest plugin;
    private final Map<UUID, StorefrontDisplay> displaysByOwner = new HashMap<>();
    private final Map<StorefrontDisplayAnchor, UUID> ownersByAnchor = new HashMap<>();
    private final Map<UUID, TextDisplayWrapper> activeViews = new HashMap<>();
    private final Map<UUID, StorefrontDisplayIcon> activeIcons = new HashMap<>();
    private final Map<UUID, StorefrontDisplayInteraction> activeInteractions = new HashMap<>();
    private final Map<UUID, UUID> ownersByInteractionEntity = new HashMap<>();
    private final ShopInteractionCooldown interactionCooldown = new ShopInteractionCooldown();
    private final Set<UUID> pendingPlacements = new HashSet<>();
    private final Set<UUID> pendingMutations = new HashSet<>();
    private final Map<UUID, BukkitTask> placementTimers = new HashMap<>();

    private boolean started;
    private boolean loaded;
    private long lifecycleGeneration;
    private BukkitTask effectsTask;
    private long effectsElapsedTicks;

    public StorefrontDisplayManager(ShopChest plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        started = true;
        startEffectsTask();
        final long generation = ++lifecycleGeneration;
        plugin.getStorefrontRepository().findDisplays(
                new Callback<List<StorefrontDisplay>>(plugin) {
                    @Override
                    public void onResult(List<StorefrontDisplay> displays) {
                        if (!isCurrent(generation)) {
                            return;
                        }
                        for (StorefrontDisplay display : displays) {
                            if (!displaysByOwner.containsKey(display.ownerId())
                                    && !ownersByAnchor.containsKey(display.anchor())) {
                                cache(display);
                            }
                        }
                        loaded = true;
                        refreshAll();
                        plugin.getLogger().info("Loaded " + displaysByOwner.size()
                                + " Storefront Display"
                                + (displaysByOwner.size() == 1 ? "" : "s"));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (!isCurrent(generation)) {
                            return;
                        }
                        plugin.getLogger().warning(
                                "Storefront Displays could not be loaded; placement remains unavailable");
                        if (throwable != null) {
                            plugin.debug(throwable);
                        }
                    }
                });
    }

    public void stop() {
        started = false;
        loaded = false;
        lifecycleGeneration++;
        stopEffectsTask();
        cancelPendingPlacements();
        interactionCooldown.clearAll();
        pendingMutations.clear();
        removeAllViews();
        displaysByOwner.clear();
        ownersByAnchor.clear();
    }

    public void beginPlacement(Player player) {
        if (!available(player)) {
            return;
        }
        final UUID ownerId = player.getUniqueId();
        final StorefrontDisplay existing = displaysByOwner.get(ownerId);
        if (existing != null) {
            player.sendMessage(Component.text(
                    "You already have a Storefront Display at " + locationLabel(existing)
                            + ". Remove it before choosing a new location.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (pendingMutations.contains(ownerId)) {
            player.sendMessage(Component.text(
                    "Your previous Storefront Display change is still being saved.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (ClickType.getPlayerClickType(player) != null) {
            player.sendMessage(Component.text(
                    "Finish your current ShopChest selection before placing a Storefront Display.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!plugin.getPublicCatalogue().isReady()) {
            player.sendMessage(Component.text(
                    "The public shop catalogue is warming up. Please try again shortly.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (plugin.getPublicCatalogue().ownerEntries(ownerId).isEmpty()) {
            player.sendMessage(Component.text(
                    "Create an eligible normal shop before placing a Storefront Display.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (plugin.getPublicCatalogue().profile(ownerId)
                .map(StorefrontProfile::suspended)
                .orElse(false)) {
            player.sendMessage(Component.text(
                    "Your storefront is suspended, so its display cannot be placed.",
                    NamedTextColor.RED));
            return;
        }

        cancelPlacement(ownerId);
        pendingPlacements.add(ownerId);
        placementTimers.put(ownerId, plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (pendingPlacements.remove(ownerId)) {
                        player.sendMessage(Component.text(
                                "Storefront Display placement timed out.",
                                NamedTextColor.YELLOW));
                    }
                    placementTimers.remove(ownerId);
                },
                PLACEMENT_TIMEOUT_TICKS));
        player.sendMessage(Component.text(
                "Right-click the Ender Chest that should host your Storefront Display. "
                        + "It will remain a normal Ender Chest and cannot trade items.",
                NamedTextColor.AQUA));
        player.sendMessage(Component.text(
                "You may own only one display. Selection expires in 15 seconds.",
                NamedTextColor.GRAY));
    }

    public void showStatus(Player player) {
        if (!available(player)) {
            return;
        }
        final UUID ownerId = player.getUniqueId();
        if (pendingPlacements.contains(ownerId)) {
            player.sendMessage(Component.text(
                    "Storefront Display placement is active. Right-click an Ender Chest.",
                    NamedTextColor.AQUA));
            return;
        }
        final StorefrontDisplay display = displaysByOwner.get(ownerId);
        if (display == null) {
            player.sendMessage(Component.text(
                    "You do not have a Storefront Display. Use /" + Config.mainCommandName
                            + " profile display create.",
                    NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text(
                "Your Storefront Display is anchored at " + locationLabel(display) + ".",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                displayState(display),
                NamedTextColor.GRAY));
    }

    public void removeOwn(Player player) {
        cancelPlacement(player.getUniqueId());
        remove(player.getUniqueId(), player, false);
    }

    public void removeForStaff(UUID ownerId, String ownerName, CommandSender sender) {
        remove(ownerId, sender, true, ownerName);
    }

    public void refreshOwner(UUID ownerId) {
        if (!started || !loaded) {
            return;
        }
        final StorefrontDisplay display = displaysByOwner.get(ownerId);
        if (display == null) {
            removeView(ownerId);
            return;
        }
        reconcile(display);
    }

    public void refreshAll() {
        if (!started || !loaded) {
            return;
        }
        for (StorefrontDisplay display : List.copyOf(displaysByOwner.values())) {
            reconcile(display);
        }
    }

    /** Refreshes landmark entities immediately after a live config change. */
    public void refreshEffects() {
        if (!started || !loaded) {
            return;
        }
        refreshAll();
        tickEffects();
    }

    public void cancelPendingPlacements() {
        for (BukkitTask task : placementTimers.values()) {
            task.cancel();
        }
        placementTimers.clear();
        pendingPlacements.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlacementSelection(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !pendingPlacements.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        final Player player = event.getPlayer();
        final Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) {
            player.sendMessage(Component.text(
                    "That block is not an Ender Chest. Your display selection is still active.",
                    NamedTextColor.YELLOW));
            return;
        }
        final boolean protectedInteraction = event.useInteractedBlock() == Event.Result.DENY;
        event.setCancelled(true);
        if (protectedInteraction
                && !player.hasPermission(Permissions.CREATE_PROTECTED)) {
            player.sendMessage(Component.text(
                    "You cannot place a Storefront Display on a protected Ender Chest.",
                    NamedTextColor.RED));
            return;
        }
        if (Config.enableAuthMeIntegration && plugin.hasAuthMe()
                && !AuthMeApi.getInstance().isAuthenticated(player)) {
            player.sendMessage(Component.text(
                    "Log in before placing a Storefront Display.",
                    NamedTextColor.RED));
            return;
        }
        if (!block.getRelative(BlockFace.UP).getType().isAir()) {
            player.sendMessage(Component.text(
                    "Leave the block above the Ender Chest empty for the display.",
                    NamedTextColor.YELLOW));
            return;
        }

        final UUID ownerId = player.getUniqueId();
        final StorefrontDisplay candidate = new StorefrontDisplay(
                ownerId,
                block.getWorld().getUID(),
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                System.currentTimeMillis());
        if (displaysByOwner.containsKey(ownerId)) {
            cancelPlacement(ownerId);
            player.sendMessage(Component.text(
                    "You already have a Storefront Display.", NamedTextColor.YELLOW));
            return;
        }
        if (ownersByAnchor.containsKey(candidate.anchor())) {
            player.sendMessage(Component.text(
                    "That Ender Chest already hosts a Storefront Display. Choose another one.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!plugin.getPublicCatalogue().isReady()
                || plugin.getPublicCatalogue().ownerEntries(ownerId).isEmpty()) {
            cancelPlacement(ownerId);
            player.sendMessage(Component.text(
                    "Your public shops changed while selecting. Start placement again after they refresh.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (plugin.getPublicCatalogue().profile(ownerId)
                .map(StorefrontProfile::suspended)
                .orElse(false)) {
            cancelPlacement(ownerId);
            player.sendMessage(Component.text(
                    "Your storefront was suspended while selecting, so the display was not placed.",
                    NamedTextColor.RED));
            return;
        }

        cancelPlacement(ownerId);
        pendingMutations.add(ownerId);
        final long generation = lifecycleGeneration;
        plugin.getStorefrontRepository().createDisplay(candidate, new Callback<Void>(plugin) {
            @Override
            public void onResult(Void ignored) {
                pendingMutations.remove(ownerId);
                if (!isCurrent(generation)) {
                    recoverOwner(ownerId);
                    return;
                }
                cache(candidate);
                reconcile(candidate);
                player.sendMessage(Component.text(
                        "Storefront Display created at " + locationLabel(candidate)
                                + ". You cannot place a second one.",
                        NamedTextColor.GREEN));
            }

            @Override
            public void onError(Throwable throwable) {
                pendingMutations.remove(ownerId);
                if (throwable instanceof StorefrontDisplayConflictException conflict) {
                    player.sendMessage(Component.text(
                            conflict.getMessage() + ".",
                            NamedTextColor.YELLOW));
                    if (conflict.kind() == StorefrontDisplayConflictException.Kind.OWNER) {
                        recoverOwner(ownerId);
                    }
                } else {
                    persistenceError(player, throwable);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        final UUID ownerId = ownersByAnchor.get(anchor(event.getBlock()));
        if (ownerId != null) {
            removeAfterAnchorLoss(ownerId);
            return;
        }
        refreshIfDisplaySpaceChanged(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        refreshIfDisplaySpaceChanged(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectAnchors(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protectAnchors(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!started || !loaded) {
            return;
        }
        for (StorefrontDisplay display : displaysInChunk(
                event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ())) {
            reconcile(display);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (StorefrontDisplay display : displaysInChunk(
                event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ())) {
            removeView(display.ownerId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (started && loaded) {
            plugin.getServer().getScheduler().runTask(plugin, this::refreshAll);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (StorefrontDisplay display : displaysByOwner.values()) {
            if (display.worldId().equals(event.getWorld().getUID())) {
                removeView(display.ownerId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        for (TextDisplayWrapper view : activeViews.values()) {
            if (view.exists()) {
                view.setVisible(event.getPlayer(), true);
            }
        }
        for (StorefrontDisplayInteraction interaction : activeInteractions.values()) {
            if (interaction.exists()) {
                interaction.setVisible(event.getPlayer(), true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDisplayInteract(PlayerInteractAtEntityEvent event) {
        final UUID entityId = event.getRightClicked().getUniqueId();
        final UUID ownerId = ownersByInteractionEntity.get(entityId);
        if (ownerId == null) {
            return;
        }
        final StorefrontDisplayInteraction interaction = activeInteractions.get(ownerId);
        if (interaction == null || !interaction.exists()
                || !interaction.matches(event.getRightClicked())) {
            ownersByInteractionEntity.remove(entityId);
            return;
        }

        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND
                || !interactionCooldown.tryAcquire(
                        event.getPlayer().getUniqueId(),
                        Config.storefrontDisplayInteractionCooldownMillis)) {
            return;
        }
        plugin.getShopCommand().showStorefrontProfile(event.getPlayer(), ownerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        cancelPlacement(playerId);
        interactionCooldown.clear(playerId);
        for (StorefrontDisplayIcon icon : activeIcons.values()) {
            icon.forgetViewer(playerId);
        }
    }

    private void remove(
            UUID ownerId,
            CommandSender sender,
            boolean staff
    ) {
        remove(ownerId, sender, staff, null);
    }

    private void remove(
            UUID ownerId,
            CommandSender sender,
            boolean staff,
            String suppliedOwnerName
    ) {
        if (!started || !loaded) {
            sender.sendMessage(Component.text(
                    "Storefront Displays are still loading. Please try again shortly.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!displaysByOwner.containsKey(ownerId)) {
            sender.sendMessage(Component.text(
                    staff
                            ? displayOwnerName(ownerId, suppliedOwnerName)
                                    + " does not have a Storefront Display."
                            : "You do not have a Storefront Display.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!pendingMutations.add(ownerId)) {
            sender.sendMessage(Component.text(
                    "That Storefront Display is already being changed.",
                    NamedTextColor.YELLOW));
            return;
        }
        plugin.getStorefrontRepository().deleteDisplay(
                ownerId,
                new Callback<Optional<StorefrontDisplay>>(plugin) {
                    @Override
                    public void onResult(Optional<StorefrontDisplay> removed) {
                        pendingMutations.remove(ownerId);
                        uncache(ownerId);
                        sender.sendMessage(Component.text(
                                staff
                                        ? "Removed " + displayOwnerName(ownerId, suppliedOwnerName)
                                                + "'s Storefront Display."
                                        : "Your Storefront Display was removed. You may place a new one.",
                                NamedTextColor.GREEN));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        pendingMutations.remove(ownerId);
                        persistenceError(sender, throwable);
                    }
                });
    }

    private boolean available(CommandSender sender) {
        if (started && loaded) {
            return true;
        }
        sender.sendMessage(Component.text(
                "Storefront Displays are still loading. Please try again shortly.",
                NamedTextColor.YELLOW));
        return false;
    }

    private void reconcile(StorefrontDisplay display) {
        if (!started || !loaded || pendingMutations.contains(display.ownerId())) {
            return;
        }
        final World world = Bukkit.getWorld(display.worldId());
        if (world == null || !world.isChunkLoaded(
                ChunkCoordinates.fromBlock(display.blockX()),
                ChunkCoordinates.fromBlock(display.blockZ()))) {
            removeView(display.ownerId());
            return;
        }
        final Block anchor = world.getBlockAt(
                display.blockX(), display.blockY(), display.blockZ());
        if (anchor.getType() != Material.ENDER_CHEST) {
            removeView(display.ownerId());
            removeOrphan(display.ownerId());
            return;
        }
        if (!anchor.getRelative(BlockFace.UP).getType().isAir()
                || !plugin.getPublicCatalogue().isReady()
                || plugin.getPublicCatalogue().profile(display.ownerId())
                        .map(StorefrontProfile::suspended)
                        .orElse(false)) {
            removeView(display.ownerId());
            return;
        }

        final OfflinePlayer owner = Bukkit.getOfflinePlayer(display.ownerId());
        final String ownerName = owner.getName() == null
                ? display.ownerId().toString().substring(0, 8)
                : owner.getName();
        final StorefrontProfile profile = plugin.getPublicCatalogue()
                .profile(display.ownerId())
                .orElseGet(() -> StorefrontProfile.empty(display.ownerId(), 0L));
        final StorefrontDisplayStats stats = StorefrontDisplayStats.from(
                plugin.getPublicCatalogue().ownerEntries(display.ownerId()));
        final StorefrontDisplayRenderer.RenderedPanel renderedPanel =
                StorefrontDisplayRenderer.renderPanel(
                        profile,
                        ownerName,
                        stats,
                        Config.mainCommandName,
                        Config.hologramColors);
        final float panelScale = Math.max(
                0.1F, Config.hologramTextScale * DISPLAY_SCALE_FACTOR);
        final TextDisplayData data = new TextDisplayData(
                renderedPanel.text(),
                Config.storefrontDisplayPanelWidth,
                Config.hologramBackgroundColor,
                false,
                panelScale,
                Config.hologramTextOpacity,
                Config.hologramTextShadowed,
                Config.hologramTextSeeThrough,
                Config.hologramTextAlignment);
        final Location location = new Location(
                world,
                display.blockX() + 0.5D,
                display.blockY() + DISPLAY_BASE_Y_OFFSET
                        + Config.storefrontDisplayPanelVerticalOffset,
                display.blockZ() + 0.5D);
        final TextDisplayWrapper current = activeViews.get(display.ownerId());
        if (current != null && current.exists()) {
            current.setLocation(location);
            current.setDisplayData(data);
            for (Player player : Bukkit.getOnlinePlayers()) {
                current.setVisible(player, true);
            }
            syncInteraction(display, current, panelScale, renderedPanel.lineCount());
            syncIcon(display, world);
            return;
        }
        removeView(display.ownerId());
        final TextDisplayWrapper created = new TextDisplayWrapper(plugin, location, data);
        activeViews.put(display.ownerId(), created);
        for (Player player : Bukkit.getOnlinePlayers()) {
            created.setVisible(player, true);
        }
        syncInteraction(display, created, panelScale, renderedPanel.lineCount());
        syncIcon(display, world);
    }

    private void syncInteraction(
            StorefrontDisplay display,
            TextDisplayWrapper view,
            float panelScale,
            int lineCount
    ) {
        if (!Config.storefrontDisplayInteractionEnabled) {
            removeInteraction(display.ownerId());
            return;
        }
        final StorefrontDisplayInteraction current = activeInteractions.get(display.ownerId());
        if (current != null && current.exists()) {
            current.refresh(
                    view.location(),
                    Config.storefrontDisplayPanelWidth,
                    panelScale,
                    lineCount);
            ownersByInteractionEntity.put(current.entityId(), display.ownerId());
            for (Player player : Bukkit.getOnlinePlayers()) {
                current.setVisible(player, true);
            }
            return;
        }

        removeInteraction(display.ownerId());
        final StorefrontDisplayInteraction created = new StorefrontDisplayInteraction(
                plugin,
                view.location(),
                Config.storefrontDisplayPanelWidth,
                panelScale,
                lineCount);
        activeInteractions.put(display.ownerId(), created);
        ownersByInteractionEntity.put(created.entityId(), display.ownerId());
        for (Player player : Bukkit.getOnlinePlayers()) {
            created.setVisible(player, true);
        }
    }

    private void syncIcon(StorefrontDisplay display, World world) {
        if (!Config.storefrontDisplayIconEnabled) {
            removeIcon(display.ownerId());
            return;
        }
        final Location location = new Location(
                world,
                display.blockX() + 0.5D,
                display.blockY() + Config.storefrontDisplayIconHeight
                        + Config.storefrontDisplayIconVerticalOffset,
                display.blockZ() + 0.5D);
        final StorefrontDisplayIcon current = activeIcons.get(display.ownerId());
        if (current != null && current.exists()) {
            current.refresh(Config.storefrontDisplayIconMaterial, location, effectsElapsedTicks);
            return;
        }
        removeIcon(display.ownerId());
        final StorefrontDisplayIcon created = new StorefrontDisplayIcon(
                plugin,
                display.ownerId(),
                Config.storefrontDisplayIconMaterial,
                location);
        created.applyAnimation(effectsElapsedTicks);
        activeIcons.put(display.ownerId(), created);
    }

    private void startEffectsTask() {
        if (effectsTask != null && !effectsTask.isCancelled()) {
            return;
        }
        effectsTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tickEffects,
                StorefrontDisplayEffects.UPDATE_INTERVAL_TICKS,
                StorefrontDisplayEffects.UPDATE_INTERVAL_TICKS);
    }

    private void stopEffectsTask() {
        if (effectsTask != null) {
            effectsTask.cancel();
            effectsTask = null;
        }
        effectsElapsedTicks = 0L;
    }

    private void tickEffects() {
        if (!started || !loaded) {
            return;
        }
        effectsElapsedTicks += StorefrontDisplayEffects.UPDATE_INTERVAL_TICKS;

        final List<StorefrontDisplay> effectDisplays = new ArrayList<>();
        for (StorefrontDisplay display : displaysByOwner.values()) {
            final TextDisplayWrapper view = activeViews.get(display.ownerId());
            if (view == null || !view.exists()) {
                removeIcon(display.ownerId());
                removeInteraction(display.ownerId());
                continue;
            }
            final World world = Bukkit.getWorld(display.worldId());
            if (world == null || !world.isChunkLoaded(
                    ChunkCoordinates.fromBlock(display.blockX()),
                    ChunkCoordinates.fromBlock(display.blockZ()))) {
                removeView(display.ownerId());
                continue;
            }
            effectDisplays.add(display);
            final StorefrontDisplayIcon icon = activeIcons.get(display.ownerId());
            if (Config.storefrontDisplayIconEnabled
                    && (icon == null || !icon.exists())) {
                syncIcon(display, world);
            } else if (!Config.storefrontDisplayIconEnabled) {
                removeIcon(display.ownerId());
            }
            final StorefrontDisplayInteraction interaction =
                    activeInteractions.get(display.ownerId());
            if (Config.storefrontDisplayInteractionEnabled
                    && (interaction == null || !interaction.exists())) {
                reconcile(display);
            } else if (!Config.storefrontDisplayInteractionEnabled) {
                removeInteraction(display.ownerId());
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            updateEffectsFor(player, effectDisplays);
        }

        for (UUID ownerId : List.copyOf(activeIcons.keySet())) {
            final StorefrontDisplayIcon icon = activeIcons.get(ownerId);
            if (icon == null) {
                continue;
            }
            if (!icon.exists()) {
                removeIcon(ownerId);
            } else if (icon.hasViewers()) {
                icon.applyAnimation(effectsElapsedTicks);
            }
        }
    }

    private void updateEffectsFor(Player player, List<StorefrontDisplay> effectDisplays) {
        final Location playerLocation = player.getLocation();
        final List<NearbyEffect> particleDisplays = new ArrayList<>();
        for (StorefrontDisplay display : effectDisplays) {
            final StorefrontDisplayIcon icon = activeIcons.get(display.ownerId());
            if (!display.worldId().equals(player.getWorld().getUID())) {
                if (icon != null) {
                    icon.setVisible(player, false);
                }
                continue;
            }
            final double distanceSquared = distanceSquared(playerLocation, display);
            if (icon != null) {
                icon.setVisible(player, StorefrontDisplayEffects.within(
                        distanceSquared, Config.storefrontDisplayIconViewDistance));
            }
            if (Config.storefrontDisplayParticlesEnabled
                    && Config.storefrontDisplayParticle != null
                    && Config.storefrontDisplayParticleCount > 0
                    && StorefrontDisplayEffects.within(
                            distanceSquared, Config.storefrontDisplayParticleRadius)) {
                particleDisplays.add(new NearbyEffect(display, distanceSquared));
            }
        }

        particleDisplays.sort(Comparator.comparingDouble(NearbyEffect::distanceSquared));
        final int visibleDisplays = Math.min(
                particleDisplays.size(),
                StorefrontDisplayEffects.MAX_PARTICLE_DISPLAYS_PER_VIEWER);
        for (int index = 0; index < visibleDisplays; index++) {
            spawnParticles(player, particleDisplays.get(index));
        }
    }

    private void spawnParticles(Player player, NearbyEffect nearby) {
        final int count = StorefrontDisplayEffects.particleCount(
                nearby.distanceSquared(),
                Config.storefrontDisplayParticleRadius,
                Config.storefrontDisplayParticleCount);
        final StorefrontDisplay display = nearby.display();
        for (StorefrontDisplayEffects.Offset offset : StorefrontDisplayEffects.particleOffsets(
                effectsElapsedTicks,
                StorefrontDisplayEffects.phaseFor(display.ownerId()),
                count)) {
            final double x = display.blockX() + 0.5D + offset.x();
            final double y = display.blockY() + offset.y();
            final double z = display.blockZ() + 0.5D + offset.z();
            if (Config.storefrontDisplayParticleDustOptions == null) {
                player.spawnParticle(
                        Config.storefrontDisplayParticle,
                        x,
                        y,
                        z,
                        1,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D);
            } else {
                player.spawnParticle(
                        Config.storefrontDisplayParticle,
                        x,
                        y,
                        z,
                        1,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        Config.storefrontDisplayParticleDustOptions);
            }
        }
    }

    private static double distanceSquared(Location location, StorefrontDisplay display) {
        final double deltaX = location.getX() - (display.blockX() + 0.5D);
        final double deltaY = location.getY() - (display.blockY() + 0.5D);
        final double deltaZ = location.getZ() - (display.blockZ() + 0.5D);
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private void removeOrphan(UUID ownerId) {
        if (!pendingMutations.add(ownerId)) {
            return;
        }
        plugin.getStorefrontRepository().deleteDisplay(
                ownerId,
                new Callback<Optional<StorefrontDisplay>>(plugin) {
                    @Override
                    public void onResult(Optional<StorefrontDisplay> ignored) {
                        pendingMutations.remove(ownerId);
                        uncache(ownerId);
                        plugin.getLogger().info(
                                "Removed a Storefront Display whose Ender Chest no longer exists");
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        pendingMutations.remove(ownerId);
                        if (throwable != null) {
                            plugin.debug(throwable);
                        }
                    }
                });
    }

    private void removeAfterAnchorLoss(UUID ownerId) {
        removeView(ownerId);
        if (!pendingMutations.add(ownerId)) {
            return;
        }
        plugin.getStorefrontRepository().deleteDisplay(
                ownerId,
                new Callback<Optional<StorefrontDisplay>>(plugin) {
                    @Override
                    public void onResult(Optional<StorefrontDisplay> ignored) {
                        pendingMutations.remove(ownerId);
                        uncache(ownerId);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        pendingMutations.remove(ownerId);
                        plugin.getLogger().warning(
                                "Could not release a Storefront Display after its Ender Chest was broken");
                        if (throwable != null) {
                            plugin.debug(throwable);
                        }
                    }
                });
    }

    private void protectAnchors(List<Block> blocks) {
        boolean refreshNeeded = false;
        for (Block block : List.copyOf(blocks)) {
            if (ownersByAnchor.containsKey(anchor(block))) {
                blocks.remove(block);
                refreshNeeded = true;
            } else if (ownersByAnchor.containsKey(anchor(block.getRelative(BlockFace.DOWN)))) {
                refreshNeeded = true;
            }
        }
        if (refreshNeeded) {
            plugin.getServer().getScheduler().runTask(plugin, this::refreshAll);
        }
    }

    private void refreshIfDisplaySpaceChanged(Block changed) {
        final Block possibleAnchor = changed.getRelative(BlockFace.DOWN);
        final UUID ownerId = ownersByAnchor.get(anchor(possibleAnchor));
        if (ownerId != null) {
            plugin.getServer().getScheduler().runTask(
                    plugin, () -> refreshOwner(ownerId));
        }
    }

    private void cache(StorefrontDisplay display) {
        displaysByOwner.put(display.ownerId(), display);
        ownersByAnchor.put(display.anchor(), display.ownerId());
    }

    private void recoverOwner(UUID ownerId) {
        if (!started) {
            return;
        }
        plugin.getStorefrontRepository().findDisplay(
                ownerId,
                new Callback<Optional<StorefrontDisplay>>(plugin) {
                    @Override
                    public void onResult(Optional<StorefrontDisplay> persisted) {
                        if (!started || persisted.isEmpty()) {
                            return;
                        }
                        final StorefrontDisplay display = persisted.orElseThrow();
                        final UUID anchorOwner = ownersByAnchor.get(display.anchor());
                        if (anchorOwner != null && !anchorOwner.equals(ownerId)) {
                            return;
                        }
                        final StorefrontDisplay current = displaysByOwner.get(ownerId);
                        if (current != null && !current.anchor().equals(display.anchor())) {
                            ownersByAnchor.remove(current.anchor(), ownerId);
                        }
                        cache(display);
                        refreshOwner(ownerId);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (throwable != null) {
                            plugin.debug(throwable);
                        }
                    }
                });
    }

    private void uncache(UUID ownerId) {
        final StorefrontDisplay removed = displaysByOwner.remove(ownerId);
        if (removed != null) {
            ownersByAnchor.remove(removed.anchor(), ownerId);
        }
        removeView(ownerId);
    }

    private void removeView(UUID ownerId) {
        final TextDisplayWrapper view = activeViews.remove(ownerId);
        if (view != null) {
            view.remove();
        }
        removeInteraction(ownerId);
        removeIcon(ownerId);
    }

    private void removeInteraction(UUID ownerId) {
        final StorefrontDisplayInteraction interaction = activeInteractions.remove(ownerId);
        if (interaction != null) {
            ownersByInteractionEntity.remove(interaction.entityId(), ownerId);
            interaction.remove();
        }
    }

    private void removeIcon(UUID ownerId) {
        final StorefrontDisplayIcon icon = activeIcons.remove(ownerId);
        if (icon != null) {
            icon.remove();
        }
    }

    private void removeAllViews() {
        for (TextDisplayWrapper view : activeViews.values()) {
            view.remove();
        }
        activeViews.clear();
        for (StorefrontDisplayInteraction interaction : activeInteractions.values()) {
            interaction.remove();
        }
        activeInteractions.clear();
        ownersByInteractionEntity.clear();
        for (StorefrontDisplayIcon icon : activeIcons.values()) {
            icon.remove();
        }
        activeIcons.clear();
    }

    private void cancelPlacement(UUID ownerId) {
        pendingPlacements.remove(ownerId);
        final BukkitTask timer = placementTimers.remove(ownerId);
        if (timer != null) {
            timer.cancel();
        }
    }

    private List<StorefrontDisplay> displaysInChunk(UUID worldId, int chunkX, int chunkZ) {
        final List<StorefrontDisplay> displays = new ArrayList<>();
        for (StorefrontDisplay display : displaysByOwner.values()) {
            if (display.worldId().equals(worldId)
                    && ChunkCoordinates.fromBlock(display.blockX()) == chunkX
                    && ChunkCoordinates.fromBlock(display.blockZ()) == chunkZ) {
                displays.add(display);
            }
        }
        return displays;
    }

    private String displayState(StorefrontDisplay display) {
        if (plugin.getPublicCatalogue().profile(display.ownerId())
                .map(StorefrontProfile::suspended)
                .orElse(false)) {
            return "The anchor is saved, but the display is hidden while the storefront is suspended.";
        }
        final World world = Bukkit.getWorld(display.worldId());
        if (world == null) {
            return "The anchor is saved, but its world is not loaded.";
        }
        if (!world.isChunkLoaded(
                ChunkCoordinates.fromBlock(display.blockX()),
                ChunkCoordinates.fromBlock(display.blockZ()))) {
            return "The anchor is saved and will appear when its chunk loads.";
        }
        final Block block = world.getBlockAt(
                display.blockX(), display.blockY(), display.blockZ());
        if (block.getType() != Material.ENDER_CHEST) {
            return "The Ender Chest is missing; this stale anchor is being cleaned up.";
        }
        if (!block.getRelative(BlockFace.UP).getType().isAir()) {
            return "The anchor is saved, but the block above it must be empty.";
        }
        return activeViews.containsKey(display.ownerId())
                ? "The display is active and uses live storefront data."
                : "The anchor is saved; its text will appear after the catalogue refreshes.";
    }

    private static StorefrontDisplayAnchor anchor(Block block) {
        return new StorefrontDisplayAnchor(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static String locationLabel(StorefrontDisplay display) {
        return display.worldName() + " " + display.blockX() + ", "
                + display.blockY() + ", " + display.blockZ();
    }

    private static String displayOwnerName(UUID ownerId, String supplied) {
        if (supplied != null && !supplied.isBlank()) {
            return supplied;
        }
        final OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        return owner.getName() == null
                ? ownerId.toString().substring(0, 8)
                : owner.getName();
    }

    private void persistenceError(CommandSender sender, Throwable throwable) {
        sender.sendMessage(Component.text(
                "The Storefront Display could not be saved. Please try again.",
                NamedTextColor.RED));
        if (throwable != null) {
            plugin.debug(throwable);
        }
    }

    private boolean isCurrent(long generation) {
        return started && lifecycleGeneration == generation && plugin.isEnabled();
    }

    private record NearbyEffect(StorefrontDisplay display, double distanceSquared) {
    }
}
