package de.epiceric.shopchest.storefront;

import java.util.Objects;
import java.util.regex.Pattern;

public final class StorefrontTextPolicy {

    private static final Pattern LEGACY_FORMATTING = Pattern.compile(
            "(?i)(?:\\u00a7|&[0-9a-fk-orx])");
    private static final Pattern MINI_MESSAGE = Pattern.compile(
            "(?i)<[/!?]?[a-z][^>]*>");
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:https?://|www\\.|\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)+\\b)");
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?:%[^%\\s]+%|\\{[^{}\\s]+})");
    private static final Pattern REPEATED_SPACES = Pattern.compile(" {2,}");

    private StorefrontTextPolicy() {
    }

    public static String normalize(StorefrontProfileField field, String input) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(input, "input");

        if (input.codePoints().anyMatch(StorefrontTextPolicy::isUnsafeCodePoint)
                || LEGACY_FORMATTING.matcher(input).find()
                || MINI_MESSAGE.matcher(input).find()
                || URL.matcher(input).find()
                || PLACEHOLDER.matcher(input).find()) {
            throw new IllegalArgumentException(
                    "Profile text must be plain text without formatting, links, or placeholders");
        }

        final String normalized = REPEATED_SPACES.matcher(input.strip()).replaceAll(" ");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Profile text cannot be empty");
        }
        if (normalized.codePointCount(0, normalized.length()) > field.maximumLength()) {
            throw new IllegalArgumentException(
                    field.name().toLowerCase() + " is limited to "
                            + field.maximumLength() + " characters");
        }
        return normalized;
    }

    private static boolean isUnsafeCodePoint(int codePoint) {
        final int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.PRIVATE_USE
                || type == Character.SURROGATE;
    }
}
