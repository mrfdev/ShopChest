package de.epiceric.shopchest.storefront;

import java.util.Locale;

public enum StorefrontProfileField {
    NAME(32),
    TAGLINE(80),
    DESCRIPTION(180),
    DIRECTIONS(120);

    private final int maximumLength;

    StorefrontProfileField(int maximumLength) {
        this.maximumLength = maximumLength;
    }

    public int maximumLength() {
        return maximumLength;
    }

    public static StorefrontProfileField parse(String value) {
        return switch (value.toLowerCase(Locale.ENGLISH)) {
            case "name", "title" -> NAME;
            case "tagline", "advertisement", "advertising", "ad" -> TAGLINE;
            case "description", "about" -> DESCRIPTION;
            case "directions", "location", "location-hint", "hint" -> DIRECTIONS;
            default -> throw new IllegalArgumentException(
                    "Choose name, advertisement, description, or location");
        };
    }
}
