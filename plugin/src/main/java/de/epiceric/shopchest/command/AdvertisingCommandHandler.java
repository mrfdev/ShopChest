package de.epiceric.shopchest.command;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.advertising.AdvertisementRequest;
import de.epiceric.shopchest.advertising.AdvertisementQueueFullException;
import de.epiceric.shopchest.advertising.AdvertisementTransition;
import de.epiceric.shopchest.advertising.AdvertisingCommand;
import de.epiceric.shopchest.advertising.AdvertisingCurrencyMatcher;
import de.epiceric.shopchest.advertising.AdvertisingCurrencyTemplateStore;
import de.epiceric.shopchest.advertising.AdvertisingFeature;
import de.epiceric.shopchest.advertising.AdvertisingPass;
import de.epiceric.shopchest.advertising.AdvertisingPassPurchase;
import de.epiceric.shopchest.advertising.AdvertisingPurchaseDeliveryRejectedException;
import de.epiceric.shopchest.advertising.AdvertisingPurchaseStatus;
import de.epiceric.shopchest.advertising.ExactStackRemovalPlanner;
import de.epiceric.shopchest.advertising.InsufficientCurrencyException;
import de.epiceric.shopchest.advertising.ItemStackEscrowCodec;
import de.epiceric.shopchest.advertising.ItemStackStackSemantics;
import de.epiceric.shopchest.advertising.PurchaseEscrowEvidence;
import de.epiceric.shopchest.advertising.PurchaseInventoryState;
import de.epiceric.shopchest.advertising.StackRemovalPlan;
import de.epiceric.shopchest.advertising.StaleStackSnapshotException;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.utils.Callback;
import de.epiceric.shopchest.utils.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player-friendly purchase, preview, queue, status, and cancellation commands. */
final class AdvertisingCommandHandler {

    private static final long DRAFT_TTL_MILLIS = 60_000L;

    private final ShopChest plugin;
    private final Map<UUID, ConfirmationDraft> drafts = new ConcurrentHashMap<>();
    private final ExactStackRemovalPlanner<ItemStack> removalPlanner =
            new ExactStackRemovalPlanner<>(ItemStackStackSemantics.INSTANCE);
    private final AdvertisingCurrencyMatcher<ItemStack> currencyMatcher =
            AdvertisingCurrencyMatcher.itemStacks();
    private final ItemStackEscrowCodec escrowCodec = new ItemStackEscrowCodec();

    AdvertisingCommandHandler(ShopChest plugin) {
        this.plugin = plugin;
    }

    void invalidateDrafts() {
        drafts.clear();
    }

    boolean handlePlayer(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can advertise a storefront.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(Permissions.ADVERTISE)) {
            player.sendMessage(Component.text(
                    "You do not have permission to advertise a storefront.",
                    NamedTextColor.RED));
            return true;
        }
        if (!Config.advertisingEnabled) {
            player.sendMessage(Component.text(
                    "Storefront advertising is currently disabled.",
                    NamedTextColor.YELLOW));
            return true;
        }
        final AdvertisingCommand command;
        try {
            command = AdvertisingCommand.parse(arguments);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(
                    commandMessage(exception), NamedTextColor.YELLOW));
            return true;
        }
        if (command instanceof AdvertisingCommand.Dashboard) {
            dashboard(player);
        } else if (command instanceof AdvertisingCommand.PassPreview) {
            previewPass(player);
        } else if (command instanceof AdvertisingCommand.ConfirmPass confirm) {
            confirmPass(player, confirm.nonce());
        } else if (command instanceof AdvertisingCommand.ConfirmRequest confirm) {
            confirmRequest(player, confirm.nonce());
        } else if (command instanceof AdvertisingCommand.Status) {
            status(player);
        } else if (command instanceof AdvertisingCommand.Cancel) {
            cancel(player);
        }
        return true;
    }

    boolean handleAdmin(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(Permissions.ADMIN_ADVERTISE)) {
            sender.sendMessage(Component.text(
                    "You do not have permission to manage advertising.",
                    NamedTextColor.RED));
            return true;
        }
        final AdvertisingCommand command;
        try {
            command = AdvertisingCommand.parse(arguments);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(
                    commandMessage(exception), NamedTextColor.YELLOW));
            return true;
        }
        if (command instanceof AdvertisingCommand.AdminCurrencyStatus) {
            final Optional<ItemStack> template = feature().currencyStore().template();
            if (template.isEmpty()) {
                sender.sendMessage(Component.text(
                        "No AFK Shrine Token template is captured. Purchases fail closed.",
                        NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text(
                        "Advertising currency is captured as a complete "
                                + template.orElseThrow().getType().getKey().asString()
                                + " ItemStack template (amount normalized to 1).",
                        NamedTextColor.GREEN));
            }
        } else if (command instanceof AdvertisingCommand.AdminCurrencyCapture) {
            if (!(sender instanceof Player administrator)) {
                sender.sendMessage(Component.text(
                        "A player must hold the genuine token to capture it.",
                        NamedTextColor.RED));
                return true;
            }
            final ItemStack held = administrator.getInventory().getItemInMainHand().clone();
            feature().currencyStore().captureAsync(
                    held,
                    administrator.getUniqueId(),
                    () -> administrator.sendMessage(Component.text(
                            "Captured the complete AFK Shrine Token ItemStack."
                                    + " Advertising purchases are now enabled.",
                            NamedTextColor.GREEN)),
                    throwable -> {
                        plugin.debug(throwable);
                        administrator.sendMessage(Component.text(
                                throwable.getMessage() == null
                                        ? "The token template could not be saved."
                                        : throwable.getMessage(),
                                NamedTextColor.RED));
                    });
        } else if (command instanceof AdvertisingCommand.AdminCurrencyClear) {
            feature().currencyStore().clearAsync(
                    () -> sender.sendMessage(Component.text(
                            "Advertising currency cleared. New pass purchases now fail closed.",
                            NamedTextColor.GREEN)),
                    throwable -> commandError(sender, throwable));
        }
        return true;
    }

    private void dashboard(Player player) {
        if (!feature().isReady()) {
            player.sendMessage(Component.text(
                    "Advertising is still starting. Please try again in a moment.",
                    NamedTextColor.YELLOW));
            return;
        }
        feature().repository().findPass(player.getUniqueId(),
                new Callback<Optional<AdvertisingPass>>(plugin) {
                    @Override
                    public void onResult(Optional<AdvertisingPass> result) {
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text(
                                "Storefront Advertising", NamedTextColor.GOLD,
                                TextDecoration.BOLD));
                        if (result.isEmpty() || !result.orElseThrow().isActiveAt(Instant.now())) {
                            player.sendMessage(Component.text(
                                    "Advertising Pass: none active", NamedTextColor.GRAY));
                            player.sendMessage(Component.text(
                                    "A pass costs " + Config.advertisingTokenCost
                                            + " captured AFK Shrine Tokens and lasts "
                                            + Config.advertisingPassDays + " days.",
                                    NamedTextColor.WHITE));
                            player.sendMessage(Component.text("[Preview pass purchase]", NamedTextColor.AQUA)
                                    .clickEvent(ClickEvent.runCommand(
                                            "/" + Config.mainCommandName + " advertise pass")));
                            player.sendMessage(Component.empty());
                            return;
                        }
                        final AdvertisingPass pass = result.orElseThrow();
                        renderPassStatus(player, pass);
                        if (pass.openRequestId() == null && pass.unreservedBroadcasts() > 0) {
                            previewRequest(player, pass);
                        } else {
                            status(player);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        commandError(player, throwable);
                    }
                });
    }

    private void previewPass(Player player) {
        final Optional<ItemStack> template = feature().currencyStore().template();
        if (template.isEmpty()) {
            player.sendMessage(Component.text(
                    "Advertising purchases are not available yet: staff must capture"
                            + " the genuine AFK Shrine Token first.",
                    NamedTextColor.YELLOW));
            return;
        }
        feature().repository().findPass(player.getUniqueId(),
                new Callback<Optional<AdvertisingPass>>(plugin) {
                    @Override
                    public void onResult(Optional<AdvertisingPass> result) {
                        if (result.isPresent() && result.orElseThrow().isActiveAt(Instant.now())) {
                            player.sendMessage(Component.text(
                                    "You already have an active Advertising Pass; passes do not stack.",
                                    NamedTextColor.YELLOW));
                            return;
                        }
                        final int exactTokens = countExactTokens(
                                player.getInventory(), template.orElseThrow());
                        final String nonce = UUID.randomUUID().toString();
                        drafts.put(player.getUniqueId(), new ConfirmationDraft(
                                DraftType.PASS,
                                nonce,
                                System.currentTimeMillis() + DRAFT_TTL_MILLIS));
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("Advertising Pass purchase", NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD));
                        player.sendMessage(Component.text(
                                "Cost: " + Config.advertisingTokenCost
                                        + " exact AFK Shrine Tokens (you have " + exactTokens + ")",
                                exactTokens >= Config.advertisingTokenCost
                                        ? NamedTextColor.GREEN : NamedTextColor.RED));
                        player.sendMessage(Component.text(
                                "Includes: " + Config.advertisingBroadcastsPerPass
                                        + " successful broadcasts over "
                                        + Config.advertisingPassDays + " days, with a "
                                        + Config.advertisingOwnerCooldownHours
                                        + " hour owner cooldown.",
                                NamedTextColor.GRAY));
                        player.sendMessage(Component.text("[Confirm purchase]", NamedTextColor.AQUA)
                                .decorate(TextDecoration.UNDERLINED)
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        "Consumes only items exactly matching the captured template")))
                                .clickEvent(ClickEvent.runCommand(
                                        "/" + Config.mainCommandName
                                                + " advertise pass confirm " + nonce)));
                        player.sendMessage(Component.text(
                                "This confirmation expires in 60 seconds.", NamedTextColor.DARK_GRAY));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        commandError(player, throwable);
                    }
                });
    }

    private void confirmPass(Player player, String nonce) {
        final ConfirmationDraft draft = drafts.remove(player.getUniqueId());
        if (draft == null || draft.type() != DraftType.PASS
                || !draft.nonce().equals(nonce) || draft.expired()) {
            player.sendMessage(Component.text(
                    "That purchase confirmation expired or was already used. Preview it again.",
                    NamedTextColor.RED));
            return;
        }
        if (!feature().tryLockPurchase(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "A purchase is already being processed for you.", NamedTextColor.YELLOW));
            return;
        }
        feature().repository().findPass(player.getUniqueId(),
                new Callback<Optional<AdvertisingPass>>(plugin) {
                    @Override
                    public void onResult(Optional<AdvertisingPass> currentPass) {
                        if (!player.isOnline()) {
                            feature().unlockPurchase(player.getUniqueId());
                            return;
                        }
                        if (currentPass.isPresent()
                                && currentPass.orElseThrow().isActiveAt(Instant.now())) {
                            feature().unlockPurchase(player.getUniqueId());
                            player.sendMessage(Component.text(
                                    "You already have an active Advertising Pass.",
                                    NamedTextColor.YELLOW));
                            return;
                        }
                        final Optional<AdvertisingCurrencyTemplateStore.AuthoritySnapshot>
                                currentAuthority = feature().currencyStore().snapshot();
                        if (currentAuthority.isEmpty()) {
                            feature().unlockPurchase(player.getUniqueId());
                            player.sendMessage(Component.text(
                                    "The captured token template is unavailable; nothing was charged.",
                                    NamedTextColor.RED));
                            return;
                        }
                        final PlayerInventory inventory = player.getInventory();
                        final List<ItemStack> current = storageSnapshot(inventory);
                        final StackRemovalPlan<ItemStack> plan;
                        final PurchaseEscrowEvidence<ItemStack> evidence;
                        final String escrowPayload;
                        try {
                            plan = removalPlanner.plan(
                                    current,
                                    currentAuthority.orElseThrow().template(),
                                    Config.advertisingTokenCost);
                            evidence = PurchaseEscrowEvidence.fromPlan(
                                    plan, ItemStackStackSemantics.INSTANCE);
                            escrowPayload = escrowCodec.encode(evidence);
                        } catch (InsufficientCurrencyException exception) {
                            feature().unlockPurchase(player.getUniqueId());
                            player.sendMessage(Component.text(
                                    "You need " + Config.advertisingTokenCost
                                            + " exact AFK Shrine Tokens; matching lookalikes do not count.",
                                    NamedTextColor.RED));
                            return;
                        } catch (RuntimeException exception) {
                            feature().unlockPurchase(player.getUniqueId());
                            commandError(player, exception);
                            return;
                        }

                        final Instant now = Instant.now();
                        final AdvertisingPass pass = feature().policy().issuePass(
                                UUID.randomUUID(), player.getUniqueId(), now);
                        final AdvertisingPassPurchase purchase =
                                AdvertisingPassPurchase.prepared(
                                        nonce, pass, escrowPayload, now);
                        feature().repository().preparePurchase(
                                purchase,
                                new Callback<AdvertisingPassPurchase>(plugin) {
                                    @Override
                                    public void onResult(AdvertisingPassPurchase prepared) {
                                        continuePreparedPurchase(
                                                player,
                                                prepared,
                                                currentAuthority.orElseThrow());
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        feature().unlockPurchase(player.getUniqueId());
                                        commandError(player, throwable);
                                    }
                                });
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        feature().unlockPurchase(player.getUniqueId());
                        commandError(player, throwable);
                    }
                });
    }

    private void previewRequest(Player player, AdvertisingPass pass) {
        feature().buildPresentation(player.getUniqueId(), presentation -> {
            if (presentation.isEmpty()) {
                player.sendMessage(Component.text(
                        "Choose at least one eligible Featured Listing first:",
                        NamedTextColor.YELLOW));
                player.sendMessage(Component.text(
                        "/" + Config.mainCommandName
                                + " profile featured add <shop-id>",
                        NamedTextColor.GRAY));
                return;
            }
            final AdvertisingFeature.AdvertisementPresentation preview =
                    presentation.orElseThrow();
            if (!preview.ready()) {
                player.sendMessage(Component.text(
                        "Your primary Featured Listing needs one full bundle in stock"
                                + " before this advertisement can be queued.",
                        NamedTextColor.YELLOW));
                return;
            }
            final String nonce = UUID.randomUUID().toString();
            drafts.put(player.getUniqueId(), new ConfirmationDraft(
                    DraftType.REQUEST,
                    nonce,
                    System.currentTimeMillis() + DRAFT_TTL_MILLIS));
            player.sendMessage(Component.text("Advertisement preview", NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
            for (var listing : preview.listings()) {
                final var entry = listing.entry();
                player.sendMessage(Component.text(
                        "• " + entry.bundleAmount() + "x "
                                + plugin.getLanguageManager().getItemNameManager()
                                        .getItemName(entry.productTemplate())
                                + " for " + plugin.getEconomy().format(
                                        entry.customerBuyPrice()),
                        NamedTextColor.WHITE));
            }
            player.sendMessage(Component.text(
                    "If the global channel is busy, this request joins the queue.",
                    NamedTextColor.GRAY));
            player.sendMessage(Component.text("[Queue this advertisement]", NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.runCommand(
                            "/" + Config.mainCommandName + " advertise confirm " + nonce)));
        }, throwable -> commandError(player, throwable));
    }

    private void confirmRequest(Player player, String nonce) {
        final ConfirmationDraft draft = drafts.remove(player.getUniqueId());
        if (draft == null || draft.type() != DraftType.REQUEST
                || !draft.nonce().equals(nonce) || draft.expired()) {
            player.sendMessage(Component.text(
                    "That advertisement confirmation expired or was already used. Preview it again.",
                    NamedTextColor.RED));
            return;
        }
        feature().repository().findPass(player.getUniqueId(),
                new Callback<Optional<AdvertisingPass>>(plugin) {
                    @Override
                    public void onResult(Optional<AdvertisingPass> result) {
                        if (result.isEmpty()) {
                            player.sendMessage(Component.text(
                                    "You do not have an Advertising Pass.", NamedTextColor.RED));
                            return;
                        }
                        revalidateAndQueueRequest(player, result.orElseThrow());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        commandError(player, throwable);
                    }
                });
    }

    private void revalidateAndQueueRequest(Player player, AdvertisingPass pass) {
        feature().buildPresentation(player.getUniqueId(), presentation -> {
            if (presentation.isEmpty() || !presentation.orElseThrow().ready()) {
                player.sendMessage(Component.text(
                        "Your storefront, Featured Listings, or primary stock changed."
                                + " Preview the advertisement again.",
                        NamedTextColor.YELLOW));
                return;
            }
            final AdvertisementTransition submitted;
            try {
                submitted = feature().policy().submit(
                        pass, UUID.randomUUID(), Instant.now());
            } catch (RuntimeException exception) {
                player.sendMessage(Component.text(
                        exception.getMessage(), NamedTextColor.YELLOW));
                return;
            }
            feature().repository().saveTransition(submitted, new Callback<Void>(plugin) {
                @Override
                public void onResult(Void ignored) {
                    final AdvertisementRequest request = submitted.request();
                    player.sendMessage(Component.text(
                            "Advertisement queued! It becomes eligible "
                                    + relativeTime(request.eligibleAt()) + ".",
                            NamedTextColor.GREEN));
                    player.sendMessage(Component.text(
                            "Use /" + Config.mainCommandName
                                    + " advertise status or cancel.",
                            NamedTextColor.GRAY));
                }

                @Override
                public void onError(Throwable throwable) {
                    if (throwable instanceof AdvertisementQueueFullException) {
                        player.sendMessage(Component.text(
                                "The advertisement queue is full. Try again after another ad runs.",
                                NamedTextColor.YELLOW));
                    } else {
                        commandError(player, throwable);
                    }
                }
            });
        }, throwable -> commandError(player, throwable));
    }

    private void status(Player player) {
        feature().repository().findPass(player.getUniqueId(),
                new Callback<Optional<AdvertisingPass>>(plugin) {
                    @Override
                    public void onResult(Optional<AdvertisingPass> result) {
                        if (result.isEmpty()) {
                            player.sendMessage(Component.text(
                                    "You do not have an Advertising Pass.", NamedTextColor.GRAY));
                            return;
                        }
                        final AdvertisingPass pass = result.orElseThrow();
                        player.sendMessage(Component.empty());
                        renderPassStatus(player, pass);
                        feature().repository().findOpenRequest(
                                player.getUniqueId(),
                                new Callback<Optional<AdvertisementRequest>>(plugin) {
                                    @Override
                                    public void onResult(Optional<AdvertisementRequest> request) {
                                        if (request.isEmpty()) {
                                            player.sendMessage(Component.text(
                                                    "Queue: no open advertisement",
                                                    NamedTextColor.GRAY));
                                        } else {
                                            player.sendMessage(Component.text(
                                                    "Queue: waiting; eligible "
                                                            + relativeTime(request.orElseThrow().eligibleAt()),
                                                    NamedTextColor.AQUA));
                                        }
                                        player.sendMessage(Component.empty());
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        commandError(player, throwable);
                                    }
                                });
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        commandError(player, throwable);
                    }
                });
    }

    private void cancel(Player player) {
        feature().repository().findPass(player.getUniqueId(),
                new Callback<Optional<AdvertisingPass>>(plugin) {
                    @Override
                    public void onResult(Optional<AdvertisingPass> passResult) {
                        if (passResult.isEmpty()) {
                            player.sendMessage(Component.text("Nothing is queued.", NamedTextColor.GRAY));
                            return;
                        }
                        feature().repository().findOpenRequest(
                                player.getUniqueId(),
                                new Callback<Optional<AdvertisementRequest>>(plugin) {
                                    @Override
                                    public void onResult(Optional<AdvertisementRequest> requestResult) {
                                        if (requestResult.isEmpty()) {
                                            player.sendMessage(Component.text("Nothing is queued.", NamedTextColor.GRAY));
                                            return;
                                        }
                                        final AdvertisementTransition cancelled;
                                        try {
                                            cancelled = feature().policy().cancel(
                                                    passResult.orElseThrow(),
                                                    requestResult.orElseThrow(),
                                                    Instant.now());
                                        } catch (RuntimeException exception) {
                                            commandError(player, exception);
                                            return;
                                        }
                                        feature().repository().saveTransition(
                                                cancelled,
                                                new Callback<Void>(plugin) {
                                                    @Override
                                                    public void onResult(Void ignored) {
                                                        player.sendMessage(Component.text(
                                                                "Advertisement cancelled; its reserved"
                                                                        + " broadcast was returned to your pass.",
                                                                NamedTextColor.GREEN));
                                                    }

                                                    @Override
                                                    public void onError(Throwable throwable) {
                                                        commandError(player, throwable);
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        commandError(player, throwable);
                                    }
                                });
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        commandError(player, throwable);
                    }
                });
    }

    private void renderPassStatus(Player player, AdvertisingPass pass) {
        player.sendMessage(Component.text(
                "Advertising Pass: " + pass.unreservedBroadcasts()
                        + " unreserved broadcast"
                        + (pass.unreservedBroadcasts() == 1 ? "" : "s")
                        + " left • expires " + relativeTime(pass.expiresAt()),
                pass.isActiveAt(Instant.now()) ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        if (pass.lastBroadcastAt() != null) {
            player.sendMessage(Component.text(
                    "Owner cooldown ends "
                            + relativeTime(pass.lastBroadcastAt().plus(pass.ownerCooldown())) + ".",
                    NamedTextColor.GRAY));
        }
    }

    private int countExactTokens(PlayerInventory inventory, ItemStack template) {
        int count = 0;
        for (ItemStack candidate : inventory.getStorageContents()) {
            if (candidate != null && currencyMatcher.matches(candidate, template)) {
                count += candidate.getAmount();
            }
        }
        return count;
    }

    private void continuePreparedPurchase(
            Player player,
            AdvertisingPassPurchase purchase,
            AdvertisingCurrencyTemplateStore.AuthoritySnapshot expectedAuthority
    ) {
        if (purchase.status() == AdvertisingPurchaseStatus.DELIVERED) {
            feature().unlockPurchase(player.getUniqueId());
            purchaseSucceeded(player, purchase.pass());
            return;
        }
        if (purchase.status() != AdvertisingPurchaseStatus.PREPARED) {
            feature().unlockPurchase(player.getUniqueId());
            feature().recoverPurchase(player, purchase.nonce());
            return;
        }
        if (!player.isOnline()) {
            feature().unlockPurchase(player.getUniqueId());
            return;
        }

        final PurchaseEscrowEvidence<ItemStack> evidence;
        final List<ItemStack> current = storageSnapshot(player.getInventory());
        final List<ItemStack> charged;
        try {
            evidence = escrowCodec.decode(purchase.escrowPayload());
            final PurchaseInventoryState state = evidence.classify(current);
            if (state == PurchaseInventoryState.DIVERGED) {
                markUncertainChargeForRecovery(
                        player,
                        purchase.nonce(),
                        new StaleStackSnapshotException(
                                "Affected inventory slots diverged before delivery"));
                return;
            }
            if (!feature().currencyStore().isCurrent(expectedAuthority)) {
                authorityChangedBeforeCharge(player, purchase, state);
                return;
            }
            charged = state == PurchaseInventoryState.BEFORE
                    ? evidence.applyCharge(current)
                    : current;
        } catch (RuntimeException exception) {
            plugin.debug(exception);
            markUncertainChargeForRecovery(player, purchase.nonce(), exception);
            return;
        }
        if (evidence.classify(current) == PurchaseInventoryState.BEFORE) {
            try {
                applyStorage(player.getInventory(), charged);
            } catch (RuntimeException exception) {
                markUncertainChargeForRecovery(player, purchase.nonce(), exception);
                return;
            }
        }

        feature().repository().deliverPreparedPurchase(
                purchase.nonce(),
                new Callback<AdvertisingPass>(plugin) {
                    @Override
                    public void onResult(AdvertisingPass delivered) {
                        feature().unlockPurchase(player.getUniqueId());
                        purchaseSucceeded(player, delivered);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        handlePurchaseDeliveryFailure(player, purchase.nonce(), throwable);
                    }
                });
    }

    private void authorityChangedBeforeCharge(
            Player player,
            AdvertisingPassPurchase purchase,
            PurchaseInventoryState inventoryState
    ) {
        if (inventoryState == PurchaseInventoryState.BEFORE) {
            feature().repository().markNotCharged(
                    purchase.nonce(),
                    new Callback<Void>(plugin) {
                        @Override
                        public void onResult(Void ignored) {
                            feature().unlockPurchase(player.getUniqueId());
                            player.sendMessage(Component.text(
                                    "Staff changed the advertising token authority;"
                                            + " nothing was charged. Preview the purchase again.",
                                    NamedTextColor.YELLOW));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            feature().unlockPurchase(player.getUniqueId());
                            commandError(player, throwable);
                        }
                    });
            return;
        }
        feature().repository().markRefundPending(
                purchase.nonce(),
                "Advertising token authority changed during purchase",
                new Callback<Void>(plugin) {
                    @Override
                    public void onResult(Void ignored) {
                        feature().unlockPurchase(player.getUniqueId());
                        feature().recoverPurchase(player, purchase.nonce());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        feature().unlockPurchase(player.getUniqueId());
                        commandError(player, throwable);
                    }
                });
    }

    private void handlePurchaseDeliveryFailure(
            Player player,
            String nonce,
            Throwable throwable
    ) {
        if (throwable instanceof AdvertisingPurchaseDeliveryRejectedException) {
            feature().repository().markRefundPending(
                    nonce,
                    throwable.getMessage(),
                    new Callback<Void>(plugin) {
                        @Override
                        public void onResult(Void ignored) {
                            feature().unlockPurchase(player.getUniqueId());
                            feature().recoverPurchase(player, nonce);
                        }

                        @Override
                        public void onError(Throwable persistenceFailure) {
                            feature().unlockPurchase(player.getUniqueId());
                            plugin.debug(persistenceFailure);
                            player.sendMessage(Component.text(
                                    "Your exact token escrow is pending recovery; do not retry yet.",
                                    NamedTextColor.RED));
                        }
                    });
            return;
        }
        plugin.debug(throwable);
        feature().unlockPurchase(player.getUniqueId());
        player.sendMessage(Component.text(
                "Pass delivery was interrupted. ShopChest will verify the durable escrow now.",
                NamedTextColor.YELLOW));
        feature().recoverPurchase(player, nonce);
    }

    private void markUncertainChargeForRecovery(
            Player player,
            String nonce,
            Throwable throwable
    ) {
        feature().repository().markRefundPending(
                nonce,
                "Inventory charge did not complete cleanly: " + throwable.getMessage(),
                new Callback<Void>(plugin) {
                    @Override
                    public void onResult(Void ignored) {
                        feature().unlockPurchase(player.getUniqueId());
                        feature().recoverPurchase(player, nonce);
                    }

                    @Override
                    public void onError(Throwable persistenceFailure) {
                        feature().unlockPurchase(player.getUniqueId());
                        plugin.debug(persistenceFailure);
                        player.sendMessage(Component.text(
                                "Your exact token escrow is pending recovery; do not retry yet.",
                                NamedTextColor.RED));
                    }
                });
    }

    private void purchaseSucceeded(Player player, AdvertisingPass delivered) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage(Component.text(
                "Advertising Pass activated! You have "
                        + delivered.unreservedBroadcasts()
                        + " broadcasts available.",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "Use /" + Config.mainCommandName
                        + " advertise to preview your first ad.",
                NamedTextColor.GRAY));
    }

    private static List<ItemStack> storageSnapshot(PlayerInventory inventory) {
        final List<ItemStack> snapshot = new ArrayList<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            snapshot.add(stack == null ? null : stack.clone());
        }
        return snapshot;
    }

    private static void applyStorage(PlayerInventory inventory, List<ItemStack> stacks) {
        inventory.setStorageContents(stacks.toArray(ItemStack[]::new));
    }

    private AdvertisingFeature feature() {
        return plugin.getAdvertisingFeature();
    }

    private void commandError(CommandSender sender, Throwable throwable) {
        plugin.debug(throwable);
        sender.sendMessage(Component.text(
                "Advertising could not complete that action. Nothing new was queued.",
                NamedTextColor.RED));
    }

    private static String commandMessage(IllegalArgumentException exception) {
        final String message = exception.getMessage() == null
                ? "Invalid advertising command" : exception.getMessage();
        return message.replace("/shops", "/" + Config.mainCommandName);
    }

    private static String relativeTime(Instant instant) {
        final Duration difference = Duration.between(Instant.now(), instant);
        if (difference.isNegative() || difference.isZero()) {
            return "now";
        }
        if (difference.toHours() >= 24) {
            return "in " + difference.toDays() + " day"
                    + (difference.toDays() == 1 ? "" : "s");
        }
        if (difference.toMinutes() >= 60) {
            return "in " + difference.toHours() + " hour"
                    + (difference.toHours() == 1 ? "" : "s");
        }
        return "in " + Math.max(1, difference.toMinutes()) + " minute"
                + (difference.toMinutes() == 1 ? "" : "s");
    }

    private enum DraftType {
        PASS,
        REQUEST
    }

    private record ConfirmationDraft(DraftType type, String nonce, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
