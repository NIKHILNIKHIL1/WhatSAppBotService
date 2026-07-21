package com.bot.whatsappbotservice.report.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** One data point in the revenue trend chart: a calendar day, its order count, and total revenue. */
public record DailyRevenueSummary(LocalDate date, long orderCount, BigDecimal revenue) {

    /** Average order value for the day; zero when no orders were placed. */
    public BigDecimal averageOrderValue() {
        if (orderCount == 0) {
            return BigDecimal.ZERO;
        }
        return revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
    }
}
