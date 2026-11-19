package com.shiye.user.module.alert.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BudgetAlertEvaluatorTest {

    @Test
    void noAlertBelowThreshold() {
        assertNull(BudgetAlertEvaluator.evaluate(
                100, new BigDecimal("1.00"), 1000, new BigDecimal("10.00"), 0.8));
    }

    @Test
    void warningAtTokenThreshold() {
        BudgetAlertEvaluator.BudgetAlert alert = BudgetAlertEvaluator.evaluate(
                800, BigDecimal.ZERO, 1000, new BigDecimal("10.00"), 0.8);

        assertEquals("WARNING", alert.level());
        assertEquals(0.8, alert.tokenRatio(), 0.0001);
        assertEquals(0, alert.costRatio(), 0.0001);
    }

    @Test
    void warningWhenCostRatioIsHigher() {
        BudgetAlertEvaluator.BudgetAlert alert = BudgetAlertEvaluator.evaluate(
                10, new BigDecimal("9.00"), 1000, new BigDecimal("10.00"), 0.8);

        assertEquals("WARNING", alert.level());
        assertEquals(0.9, alert.costRatio(), 0.0001);
    }

    @Test
    void exceededWhenCostOverBudget() {
        BudgetAlertEvaluator.BudgetAlert alert = BudgetAlertEvaluator.evaluate(
                0, new BigDecimal("10.01"), 1000, new BigDecimal("10.00"), 0.8);

        assertEquals("EXCEEDED", alert.level());
    }

    @Test
    void zeroBudgetWithUsageIsExceeded() {
        BudgetAlertEvaluator.BudgetAlert alert = BudgetAlertEvaluator.evaluate(
                1, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0.8);

        assertEquals("EXCEEDED", alert.level());
    }

    @Test
    void zeroBudgetWithoutUsageIsQuiet() {
        assertNull(BudgetAlertEvaluator.evaluate(
                0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0.8));
    }
}
