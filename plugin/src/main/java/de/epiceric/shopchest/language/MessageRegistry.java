package de.epiceric.shopchest.language;

import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.utils.LegacyColorUtils;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class MessageRegistry {

    private final String[] messages;
    private final Function<Double, String> priceFormatter;

    public MessageRegistry(@NotNull String[] messages, @NotNull Function<Double, String> priceFormatter) {
        this.messages = messages;
        this.priceFormatter = priceFormatter;
    }

    /**
     * @param message      Message which should be translated
     * @param replacements Replacements of placeholders which might be required to be replaced in the message
     * @return Localized Message
     */
    @NotNull
    public String getMessage(@NotNull Message message, @NotNull Replacement... replacements) {
        final String storedMessage = messages[message.ordinal()];
        if (storedMessage == null) {
            // Mimic old behavior -> send an error message to the player
            return LegacyColorUtils.color('c', "An error occurred: Message not found: " + message);
        }

        return applyReplacements(storedMessage, replacements);
    }

    @NotNull
    private String applyReplacements(@NotNull String message, @NotNull Replacement... replacements) {
        for (Replacement replacement : replacements) {
            final Placeholder placeholder = replacement.getPlaceholder();
            String toReplace = replacement.getReplacement();

            if ((placeholder == Placeholder.BUY_PRICE || placeholder == Placeholder.SELL_PRICE || placeholder == Placeholder.MIN_PRICE || placeholder == Placeholder.CREATION_PRICE || placeholder == Placeholder.REVENUE)
                    && !toReplace.equals(getMessage(Message.SHOP_INFO_DISABLED))
            ) {
                double price = Double.parseDouble(toReplace);
                toReplace = priceFormatter.apply(price);
            }

            message = message.replace(placeholder.toString(), toReplace);
        }
        return message;
    }

}
