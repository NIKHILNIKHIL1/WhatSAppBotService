package com.bot.whatsappbotservice.report.dto;

import java.math.BigDecimal;

/**
 * Aggregated sales figures for one product within a report period — drives the top-sellers bar
 * chart and the product performance detail table.
 */
public record ProductPerformanceSummary(
        Long productId,
        String sku,
        String productName,
        String categoryName,
        BigDecimal quantitySold,
        BigDecimal revenue,
        long orderCount,
        BigDecimal currentStock
) {}
