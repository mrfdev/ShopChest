package de.epiceric.shopchest.utils;

import de.epiceric.shopchest.shop.ShopTerms;

import java.util.Locale;

/**
 * One field-level edit requested before the player selects a shop.
 */
public record ShopEditRequest(Field field, double value) {

    public static ShopEditRequest parse(String fieldName, String rawValue) {
        final Field field = Field.valueOf(fieldName.toUpperCase(Locale.ROOT));
        final double value = field == Field.AMOUNT
                ? Integer.parseInt(rawValue)
                : Double.parseDouble(rawValue);
        return new ShopEditRequest(field, value);
    }

    public ShopTerms applyTo(ShopTerms current) {
        return switch (field) {
            case AMOUNT -> new ShopTerms((int) value, current.buyPrice(), current.sellPrice());
            case BUY -> new ShopTerms(current.amount(), value, current.sellPrice());
            case SELL -> new ShopTerms(current.amount(), current.buyPrice(), value);
        };
    }

    public enum Field {
        AMOUNT,
        BUY,
        SELL
    }
}
