package com.bot.whatsappbotservice.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Aggregated stock movements for one calendar day — drives the stock IN vs OUT grouped-bar chart
 * on the Inventory report. {@code totalIn} sums positive deltas (receipts/returns);
 * {@code totalOut} sums the absolute value of negative deltas (sales/adjustments).
 */
public record InventoryMovementSummary(LocalDate date, BigDecimal totalIn, BigDecimal totalOut) {

    /** Net stock change for the day (positive = net gain, negative = net loss). */
    public BigDecimal netChange() {
        return totalIn.subtract(totalOut);
    }
}
