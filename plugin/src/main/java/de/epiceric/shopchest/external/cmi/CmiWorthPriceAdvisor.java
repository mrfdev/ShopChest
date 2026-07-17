package de.epiceric.shopchest.external.cmi;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.MessageRegistry;
import de.epiceric.shopchest.language.Replacement;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.ShopProduct;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.OptionalDouble;

public final class CmiWorthPriceAdvisor {

    private final ShopChest plugin;
    private CmiWorthLookup lookup;
    private State state = State.DISABLED;
    private boolean lookupFailureLogged;

    public CmiWorthPriceAdvisor(ShopChest plugin) {
        this.plugin = plugin;
    }

    /**
     * Re-evaluates the optional CMI integration. CMI remains responsible for
     * loading and refreshing Worth.yml; ShopChest reads its current in-memory
     * value only when a valid normal shop is proposed.
     */
    public void refresh() {
        lookup = null;
        lookupFailureLogged = false;

        if (!Config.cmiWorthPriceWarningEnabled) {
            state = State.DISABLED;
            return;
        }

        Plugin cmi = plugin.getServer().getPluginManager().getPlugin("CMI");
        if (cmi == null || !cmi.isEnabled()) {
            state = State.CMI_UNAVAILABLE;
            return;
        }

        try {
            lookup = new CmiApiWorthLookup();
            state = State.ACTIVE;
        } catch (RuntimeException | LinkageError exception) {
            state = State.API_UNAVAILABLE;
            logLookupFailure(exception);
        }
    }

    public void advise(
            Player player,
            ShopProduct product,
            double buyPrice,
            double sellPrice,
            Shop.ShopType shopType
    ) {
        if (shopType != Shop.ShopType.NORMAL || !Config.cmiWorthPriceWarningEnabled || lookup == null) {
            return;
        }

        final OptionalDouble configuredWorth;
        try {
            configuredWorth = lookup.findSellWorth(product.getItemStack());
        } catch (RuntimeException | LinkageError exception) {
            lookup = null;
            state = State.API_UNAVAILABLE;
            logLookupFailure(exception);
            return;
        }

        if (configuredWorth.isEmpty()) {
            return;
        }

        CmiWorthPriceAssessment assessment = CmiWorthPriceAssessment.assess(
                configuredWorth.getAsDouble(),
                product.getAmount(),
                buyPrice,
                sellPrice,
                Config.cmiWorthWarnResaleRisk,
                Config.cmiWorthLowMultiplier,
                Config.cmiWorthHighMultiplier);
        if (!assessment.hasWarnings()) {
            return;
        }

        MessageRegistry messages = plugin.getLanguageManager().getMessageRegistry();
        String itemName = product.getLocalizedName();
        String formattedWorth = plugin.getEconomy().format(assessment.cmiWorth());

        for (CmiWorthPriceAssessment.Warning warning : assessment.warnings()) {
            player.sendMessage(messages.getMessage(
                    messageFor(warning),
                    new Replacement(Placeholder.ITEM_NAME, itemName),
                    new Replacement(
                            Placeholder.UNIT_PRICE,
                            plugin.getEconomy().format(assessment.unitPrice(warning))),
                    new Replacement(Placeholder.CMI_WORTH, formattedWorth),
                    new Replacement(
                            Placeholder.MULTIPLIER,
                            formatMultiplier(assessment.multiplier(warning)))));
        }
    }

    public Status status() {
        return new Status(
                state,
                Config.cmiWorthWarnResaleRisk,
                Config.cmiWorthLowMultiplier,
                Config.cmiWorthHighMultiplier);
    }

    private void logLookupFailure(Throwable throwable) {
        if (lookupFailureLogged) {
            return;
        }
        lookupFailureLogged = true;
        plugin.getLogger().warning("CMI worth price warnings are unavailable: "
                + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        plugin.debug(throwable);
    }

    private static Message messageFor(CmiWorthPriceAssessment.Warning warning) {
        return switch (warning) {
            case CUSTOMER_RESALE_RISK -> Message.CMI_WORTH_RESALE_RISK;
            case CUSTOMER_HIGH -> Message.CMI_WORTH_CUSTOMER_HIGH;
            case SHOP_LOW -> Message.CMI_WORTH_SHOP_LOW;
            case SHOP_HIGH -> Message.CMI_WORTH_SHOP_HIGH;
        };
    }

    private static String formatMultiplier(double multiplier) {
        if (multiplier >= 100.0D) {
            return String.format(Locale.ROOT, "%.0f", multiplier);
        }
        if (multiplier >= 10.0D) {
            return String.format(Locale.ROOT, "%.1f", multiplier);
        }
        return String.format(Locale.ROOT, "%.2f", multiplier);
    }

    public enum State {
        DISABLED("disabled"),
        CMI_UNAVAILABLE("waiting for CMI"),
        ACTIVE("active through CMI WorthManager"),
        API_UNAVAILABLE("CMI API unavailable");

        private final String description;

        State(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    public record Status(
            State state,
            boolean resaleRiskWarning,
            double lowMultiplier,
            double highMultiplier
    ) {

        public boolean active() {
            return state == State.ACTIVE;
        }

        public String summary() {
            return state.description()
                    + " | resale risk " + (resaleRiskWarning ? "on" : "off")
                    + " | unusual below " + String.format(Locale.ROOT, "%.2f", lowMultiplier) + "x"
                    + " / above " + String.format(Locale.ROOT, "%.2f", highMultiplier) + "x";
        }
    }
}
