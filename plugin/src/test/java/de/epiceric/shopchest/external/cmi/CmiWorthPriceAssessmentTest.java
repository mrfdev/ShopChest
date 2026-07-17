package de.epiceric.shopchest.external.cmi;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static de.epiceric.shopchest.external.cmi.CmiWorthPriceAssessment.Warning.CUSTOMER_HIGH;
import static de.epiceric.shopchest.external.cmi.CmiWorthPriceAssessment.Warning.CUSTOMER_RESALE_RISK;
import static de.epiceric.shopchest.external.cmi.CmiWorthPriceAssessment.Warning.SHOP_HIGH;
import static de.epiceric.shopchest.external.cmi.CmiWorthPriceAssessment.Warning.SHOP_LOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CmiWorthPriceAssessmentTest {

    @Test
    void detectsDirectBuyThenSellArbitrage() {
        CmiWorthPriceAssessment assessment = assess(4.0D, 0.0D);

        assertEquals(Set.of(CUSTOMER_RESALE_RISK), assessment.warnings());
        assertEquals(0.8D, assessment.customerUnitPrice());
        assertEquals(0.8D, assessment.multiplier(CUSTOMER_RESALE_RISK));
    }

    @Test
    void doesNotCallEqualWorthAProfitOpportunity() {
        assertFalse(assess(5.0D, 0.0D).hasWarnings());
    }

    @Test
    void detectsUnusuallyHighCustomerPrice() {
        CmiWorthPriceAssessment assessment = assess(101.0D, 0.0D);

        assertEquals(Set.of(CUSTOMER_HIGH), assessment.warnings());
        assertEquals(20.2D, assessment.multiplier(CUSTOMER_HIGH));
    }

    @Test
    void detectsLowAndHighShopPayouts() {
        assertEquals(Set.of(SHOP_LOW), assess(0.0D, 2.0D).warnings());
        assertEquals(Set.of(SHOP_HIGH), assess(0.0D, 101.0D).warnings());
    }

    @Test
    void reportsAtMostOneAdvisoryPerTradeDirection() {
        CmiWorthPriceAssessment assessment = assess(4.0D, 101.0D);

        assertEquals(Set.of(CUSTOMER_RESALE_RISK, SHOP_HIGH), assessment.warnings());
    }

    @Test
    void ignoresDisabledSidesAndUnusableWorth() {
        assertFalse(assess(0.0D, 0.0D).hasWarnings());
        assertFalse(CmiWorthPriceAssessment.assess(
                0.0D, 5, 1.0D, 1.0D, true, 0.5D, 20.0D).hasWarnings());
        assertFalse(CmiWorthPriceAssessment.assess(
                1.0D, 0, 1.0D, 1.0D, true, 0.5D, 20.0D).hasWarnings());
    }

    @Test
    void allowsResaleWarningToBeDisabled() {
        CmiWorthPriceAssessment assessment = CmiWorthPriceAssessment.assess(
                1.0D, 5, 4.0D, 0.0D, false, 0.5D, 20.0D);

        assertFalse(assessment.hasWarnings());
    }

    private static CmiWorthPriceAssessment assess(double buyPrice, double sellPrice) {
        return CmiWorthPriceAssessment.assess(
                1.0D, 5, buyPrice, sellPrice, true, 0.5D, 20.0D);
    }
}
