package com.bot.whatsappbotservice.report.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Period-level payment collection metrics — drives the KPI tiles on the Payment Collection report.
 * {@code overdueCount}/{@code overdueAmount} cover orders older than seven days that are still
 * UNPAID or PARTIALLY_PAID.
 */
public record PaymentSummary(
        BigDecimal totalBilled,
        BigDecimal totalCollected,
        long overdueCount,
        BigDecimal overdueAmount
) {

    /** Percentage of billed amount that has been collected; zero when nothing was billed. */
    public BigDecimal collectionRate() {
        if (totalBilled == null || totalBilled.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalCollected
                .multiply(BigDecimal.valueOf(100))
                .divide(totalBilled, 1, RoundingMode.HALF_UP);
    }

}
