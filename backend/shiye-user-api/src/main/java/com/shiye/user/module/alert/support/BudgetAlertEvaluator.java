package com.shiye.user.module.alert.support;

import java.math.BigDecimal;

public final class BudgetAlertEvaluator {

    private BudgetAlertEvaluator() {
    }

    public static BudgetAlert evaluate(long tokens, BigDecimal cost,
                                       long tokenBudget, BigDecimal costBudget,
                                       double threshold) {
        double tokenRatio = ratio(BigDecimal.valueOf(tokens), tokenBudget);
        double costRatio = ratio(cost, costBudget == null ? 0 : costBudget.doubleValue());
        double max = Math.max(tokenRatio, costRatio);
        if (max >= 1.0) {
            return new BudgetAlert("EXCEEDED", tokenRatio, costRatio);
        }
        if (max >= Math.max(0.1, Math.min(threshold, 0.99))) {
            return new BudgetAlert("WARNING", tokenRatio, costRatio);
        }
        return null;
    }

    private static double ratio(BigDecimal value, long budget) {
        if (budget <= 0) {
            return value != null && value.signum() > 0 ? 1.0 : 0;
        }
        return value == null ? 0 : value.doubleValue() / budget;
    }

    private static double ratio(BigDecimal value, double budget) {
        if (budget <= 0) {
            return value != null && value.signum() > 0 ? 1.0 : 0;
        }
        return value == null ? 0 : value.doubleValue() / budget;
    }

    public record BudgetAlert(String level, double tokenRatio, double costRatio) {
    }
}
