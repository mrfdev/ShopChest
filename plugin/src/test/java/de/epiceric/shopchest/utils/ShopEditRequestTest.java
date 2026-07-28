package de.epiceric.shopchest.utils;

import de.epiceric.shopchest.shop.ShopTerms;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopEditRequestTest {

    private static final ShopTerms CURRENT = new ShopTerms(5, 10, 4);

    @Test
    void editsOnlyTheSelectedField() {
        assertEquals(
                new ShopTerms(8, 10, 4),
                ShopEditRequest.parse("amount", "8").applyTo(CURRENT));
        assertEquals(
                new ShopTerms(5, 12.5, 4),
                ShopEditRequest.parse("buy", "12.5").applyTo(CURRENT));
        assertEquals(
                new ShopTerms(5, 10, 0),
                ShopEditRequest.parse("sell", "0").applyTo(CURRENT));
    }

    @Test
    void amountMustBeAWholeInteger() {
        assertThrows(
                NumberFormatException.class,
                () -> ShopEditRequest.parse("amount", "2.5"));
    }

    @Test
    void rejectsUnknownFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ShopEditRequest.parse("owner", "1"));
    }
}
