package de.epiceric.shopchest.storefront;

import java.util.Objects;
import java.util.UUID;

public record StorefrontProfile(
        UUID ownerId,
        String name,
        String tagline,
        String description,
        String directions,
        boolean textHidden,
        boolean suspended,
        long updatedAt
) {
    public StorefrontProfile {
        Objects.requireNonNull(ownerId, "ownerId");
        name = validated(StorefrontProfileField.NAME, name);
        tagline = validated(StorefrontProfileField.TAGLINE, tagline);
        description = validated(StorefrontProfileField.DESCRIPTION, description);
        directions = validated(StorefrontProfileField.DIRECTIONS, directions);
    }

    public static StorefrontProfile empty(UUID ownerId, long updatedAt) {
        return new StorefrontProfile(
                ownerId, null, null, null, null, false, false, updatedAt);
    }

    public StorefrontProfile withField(
            StorefrontProfileField field,
            String value,
            long timestamp
    ) {
        return switch (field) {
            case NAME -> new StorefrontProfile(
                    ownerId, value, tagline, description, directions,
                    textHidden, suspended, timestamp);
            case TAGLINE -> new StorefrontProfile(
                    ownerId, name, value, description, directions,
                    textHidden, suspended, timestamp);
            case DESCRIPTION -> new StorefrontProfile(
                    ownerId, name, tagline, value, directions,
                    textHidden, suspended, timestamp);
            case DIRECTIONS -> new StorefrontProfile(
                    ownerId, name, tagline, description, value,
                    textHidden, suspended, timestamp);
        };
    }

    public StorefrontProfile withModeration(
            boolean hidden,
            boolean isSuspended,
            long timestamp
    ) {
        return new StorefrontProfile(
                ownerId, name, tagline, description, directions,
                hidden, isSuspended, timestamp);
    }

    private static String validated(StorefrontProfileField field, String value) {
        return value == null ? null : StorefrontTextPolicy.normalize(field, value);
    }
}
