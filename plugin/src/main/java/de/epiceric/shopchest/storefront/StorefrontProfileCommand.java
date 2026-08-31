package de.epiceric.shopchest.storefront;

import java.util.Arrays;

public sealed interface StorefrontProfileCommand {

    static StorefrontProfileCommand parse(String[] arguments) {
        if (arguments.length >= 4
                && arguments[0].equalsIgnoreCase("profile")
                && arguments[1].equalsIgnoreCase("set")) {
            return new SetField(
                    StorefrontProfileField.parse(arguments[2]),
                    String.join(" ", Arrays.copyOfRange(arguments, 3, arguments.length)));
        }
        throw new IllegalArgumentException("Invalid profile command");
    }

    record SetField(StorefrontProfileField field, String value)
            implements StorefrontProfileCommand {
    }
}
