package de.epiceric.shopchest.external.cmi;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

record CmiWorthPriceAssessment(
        double cmiWorth,
        double customerUnitPrice,
        double shopUnitPrice,
        Set<Warning> warnings
) {

    enum Warning {
        CUSTOMER_RESALE_RISK,
        CUSTOMER_HIGH,
        SHOP_LOW,
        SHOP_HIGH
    }

    static CmiWorthPriceAssessment assess(
            double cmiWorth,
            int amount,
            double buyPrice,
            double sellPrice,
            boolean warnResaleRisk,
            double lowMultiplier,
            double highMultiplier
    ) {
        if (!Double.isFinite(cmiWorth) || cmiWorth <= 0.0D || amount <= 0) {
            return empty(cmiWorth);
        }

        final double customerUnitPrice = unitPrice(buyPrice, amount);
        final double shopUnitPrice = unitPrice(sellPrice, amount);
        final EnumSet<Warning> warnings = EnumSet.noneOf(Warning.class);

        if (customerUnitPrice > 0.0D) {
            final double customerMultiplier = customerUnitPrice / cmiWorth;
            if (warnResaleRisk && customerUnitPrice < cmiWorth) {
                warnings.add(Warning.CUSTOMER_RESALE_RISK);
            } else if (customerMultiplier > highMultiplier) {
                warnings.add(Warning.CUSTOMER_HIGH);
            }
        }

        if (shopUnitPrice > 0.0D) {
            final double shopMultiplier = shopUnitPrice / cmiWorth;
            if (shopMultiplier < lowMultiplier) {
                warnings.add(Warning.SHOP_LOW);
            } else if (shopMultiplier > highMultiplier) {
                warnings.add(Warning.SHOP_HIGH);
            }
        }

        return new CmiWorthPriceAssessment(
                cmiWorth,
                customerUnitPrice,
                shopUnitPrice,
                Collections.unmodifiableSet(warnings));
    }

    boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    double multiplier(Warning warning) {
        return switch (warning) {
            case CUSTOMER_RESALE_RISK, CUSTOMER_HIGH -> customerUnitPrice / cmiWorth;
            case SHOP_LOW, SHOP_HIGH -> shopUnitPrice / cmiWorth;
        };
    }

    double unitPrice(Warning warning) {
        return switch (warning) {
            case CUSTOMER_RESALE_RISK, CUSTOMER_HIGH -> customerUnitPrice;
            case SHOP_LOW, SHOP_HIGH -> shopUnitPrice;
        };
    }

    private static double unitPrice(double totalPrice, int amount) {
        return Double.isFinite(totalPrice) && totalPrice > 0.0D
                ? totalPrice / amount
                : 0.0D;
    }

    private static CmiWorthPriceAssessment empty(double cmiWorth) {
        return new CmiWorthPriceAssessment(
                cmiWorth, 0.0D, 0.0D, Collections.emptySet());
    }
}
