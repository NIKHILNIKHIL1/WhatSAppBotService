package com.bot.whatsappbotservice.report.dto;

import java.math.BigDecimal;

/**
 * All KPI values shown on the Overview dashboard in a single fetch — avoids N+1 controller calls.
 * {@code revenueGrowthPercent} is nullable: null means there is no prior period to compare
 * against (e.g. the tenant is brand new).
 */
public record ReportOverview(
        BigDecimal periodRevenue,
        long periodOrders,
        BigDecimal averageOrderValue,
        BigDecimal outstandingAmount,
        long outstandingOrderCount,
        long newCustomers,
        long activeCustomers,
        BigDecimal itemsSold,
        long openConcerns,
        BigDecimal revenueGrowthPercent
) {}
