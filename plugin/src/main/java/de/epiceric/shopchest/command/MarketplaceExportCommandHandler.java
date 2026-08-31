package de.epiceric.shopchest.command;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.catalog.export.MarketplaceSnapshotExportService;
import de.epiceric.shopchest.catalog.export.MarketplaceSnapshotExportService.ExportCounts;
import de.epiceric.shopchest.catalog.export.MarketplaceSnapshotExportService.ExportResult;
import de.epiceric.shopchest.catalog.export.MarketplaceSnapshotExportService.StartResult;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.utils.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class MarketplaceExportCommandHandler {

    private static final DateTimeFormatter CAPTURE_TIME = DateTimeFormatter
            .ofPattern("d MMMM uuuu 'at' HH:mm z", Locale.ENGLISH)
            .withZone(ZoneId.of("Europe/Amsterdam"));

    private final ShopChest plugin;
    private final MarketplaceSnapshotExportService exportService;

    MarketplaceExportCommandHandler(ShopChest plugin) {
        this.plugin = plugin;
        this.exportService = new MarketplaceSnapshotExportService(plugin);
    }

    boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN_EXPORT)) {
            sender.sendMessage(Component.text(
                    "You do not have permission to export the marketplace snapshot.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length != 3 || !args[2].equalsIgnoreCase("marketplace")) {
            sender.sendMessage(Component.text(
                    "Usage: /" + Config.mainCommandName + " admin export marketplace",
                    NamedTextColor.YELLOW));
            return true;
        }

        final StartResult result;
        try {
            result = exportService.export(
                    export -> displaySuccess(sender, export),
                    throwable -> displayFailure(sender, throwable));
        } catch (RuntimeException exception) {
            displayFailure(sender, exception);
            return true;
        }

        switch (result) {
            case STARTED -> {
                sender.sendMessage(Component.text(
                        "Building a private marketplace snapshot from already-loaded shops...",
                        NamedTextColor.GOLD));
                sender.sendMessage(Component.text(
                        "No chunks will be loaded. Unavailable rows are excluded; unloaded stock is marked unchecked.",
                        NamedTextColor.GRAY));
            }
            case CATALOGUE_NOT_READY -> sender.sendMessage(Component.text(
                    "The public shop catalogue is still warming up. Try again in a moment.",
                    NamedTextColor.YELLOW));
            case ALREADY_RUNNING -> sender.sendMessage(Component.text(
                    "A marketplace export is already running.",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private void displaySuccess(CommandSender sender, ExportResult result) {
        final ExportCounts counts = result.counts();
        sender.sendMessage(" ");
        sender.sendMessage(Component.text("Marketplace snapshot ready", NamedTextColor.GREEN)
                .append(Component.text(
                        " (ShopChest " + result.sourceVersion() + ")",
                        NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text(
                "Captured " + CAPTURE_TIME.format(result.capturedAt()),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                counts.published() + " published: "
                        + counts.inStock() + " in stock, "
                        + counts.outOfStock() + " out of stock, "
                        + counts.unchecked() + " unchecked.",
                NamedTextColor.AQUA));
        sender.sendMessage(Component.text(
                "Reviewed " + counts.candidates() + " eligible /warp shops rows; excluded "
                        + counts.excludedUnavailable() + " unavailable and "
                        + counts.excludedInvalid() + " invalid/unresolvable.",
                NamedTextColor.GRAY));
        sendPath(sender, "JSON", result.files().json());
        sendPath(sender, "CSV", result.files().csv());
        sender.sendMessage(Component.text(
                "These files are private review artifacts. Publishing still requires an explicit docs update.",
                NamedTextColor.YELLOW));
        sender.sendMessage(" ");
    }

    private void sendPath(CommandSender sender, String label, Path path) {
        final String displayPath = plugin.getDataFolder().toPath().toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString();
        sender.sendMessage(Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(displayPath, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.copyToClipboard(path.toAbsolutePath().toString()))
                        .hoverEvent(Component.text(
                                "Click to copy the full server path",
                                NamedTextColor.GRAY))));
    }

    private void displayFailure(CommandSender sender, Throwable throwable) {
        plugin.getLogger().warning("Marketplace snapshot export failed: "
                + (throwable == null ? "unknown error" : throwable.getMessage()));
        if (throwable != null) {
            plugin.debug(throwable);
        }
        sender.sendMessage(Component.text(
                "The marketplace snapshot could not be completed. Check the server log; no automatic publication occurred.",
                NamedTextColor.RED));
    }
}
