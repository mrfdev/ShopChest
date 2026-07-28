package de.epiceric.shopchest.utils;

import de.epiceric.shopchest.shop.ShopDisplayOrientation;

import java.util.Objects;

/**
 * A validated edit request waiting for the player to select a shop.
 */
public sealed interface ShopEditOperation {

    record Terms(ShopEditRequest request) implements ShopEditOperation {
        public Terms {
            Objects.requireNonNull(request, "request");
        }
    }

    record Holograms(ShopDisplayOrientation orientation) implements ShopEditOperation {
        public Holograms {
            Objects.requireNonNull(orientation, "orientation");
        }
    }
}
