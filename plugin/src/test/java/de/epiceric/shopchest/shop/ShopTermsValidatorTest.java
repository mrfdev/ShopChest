package de.epiceric.shopchest.shop;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopTermsValidatorTest {

    @Test
    void acceptsOneOrBothEnabledDirections() {
        assertTrue(validate(new ShopTerms(5, 10, 0)).isEmpty());
        assertTrue(validate(new ShopTerms(5, 0, 4)).isEmpty());
        assertTrue(validate(new ShopTerms(5, 10, 4)).isEmpty());
    }

    @Test
    void rejectsInvalidCompleteTerms() {
        assertEquals(
                ShopTermsValidator.Failure.AMOUNT_NOT_POSITIVE,
                validate(new ShopTerms(0, 10, 4)).orElseThrow());
        assertEquals(
                ShopTermsValidator.Failure.INVALID_PRICE,
                validate(new ShopTerms(5, Double.NaN, 4)).orElseThrow());
        assertEquals(
                ShopTermsValidator.Failure.INVALID_PRICE,
                validate(new ShopTerms(5, -1, 4)).orElseThrow());
        assertEquals(
                ShopTermsValidator.Failure.NO_TRADE_DIRECTION,
                validate(new ShopTerms(5, 0, 0)).orElseThrow());
        assertEquals(
                ShopTermsValidator.Failure.BUY_BELOW_SELL,
                validate(new ShopTerms(5, 3, 4)).orElseThrow());
    }

    @Test
    void honorsDecimalPricePolicy() {
        assertEquals(
                ShopTermsValidator.Failure.DECIMALS_NOT_ALLOWED,
                ShopTermsValidator.validate(
                        new ShopTerms(5, 10.5, 4),
                        false,
                        true).orElseThrow());
        assertTrue(ShopTermsValidator.validate(
                new ShopTerms(5, 10.5, 4),
                true,
                true).isEmpty());
    }

    private static Optional<ShopTermsValidator.Failure> validate(ShopTerms terms) {
        return ShopTermsValidator.validate(terms, true, true);
    }
}
