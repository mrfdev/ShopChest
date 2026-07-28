package de.epiceric.shopchest.shop;

import java.util.Optional;

/**
 * Version-independent validation for a shop's complete trading terms.
 */
public final class ShopTermsValidator {

    private ShopTermsValidator() {
    }

    public static Optional<Failure> validate(
            ShopTerms terms,
            boolean allowDecimalPrices,
            boolean requireBuyAtLeastSell
    ) {
        if (terms.amount() <= 0) {
            return Optional.of(Failure.AMOUNT_NOT_POSITIVE);
        }
        if (!Double.isFinite(terms.buyPrice())
                || !Double.isFinite(terms.sellPrice())
                || terms.buyPrice() < 0
                || terms.sellPrice() < 0) {
            return Optional.of(Failure.INVALID_PRICE);
        }
        if (!allowDecimalPrices
                && (terms.buyPrice() != Math.rint(terms.buyPrice())
                || terms.sellPrice() != Math.rint(terms.sellPrice()))) {
            return Optional.of(Failure.DECIMALS_NOT_ALLOWED);
        }

        final boolean buyEnabled = terms.buyPrice() > 0;
        final boolean sellEnabled = terms.sellPrice() > 0;
        if (!buyEnabled && !sellEnabled) {
            return Optional.of(Failure.NO_TRADE_DIRECTION);
        }
        if (requireBuyAtLeastSell
                && buyEnabled
                && sellEnabled
                && terms.buyPrice() < terms.sellPrice()) {
            return Optional.of(Failure.BUY_BELOW_SELL);
        }
        return Optional.empty();
    }

    public enum Failure {
        AMOUNT_NOT_POSITIVE,
        INVALID_PRICE,
        DECIMALS_NOT_ALLOWED,
        NO_TRADE_DIRECTION,
        BUY_BELOW_SELL
    }
}
