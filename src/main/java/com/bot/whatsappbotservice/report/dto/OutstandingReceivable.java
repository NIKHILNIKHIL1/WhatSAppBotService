package com.bot.whatsappbotservice.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row in the outstanding-receivables table on the Payment Collection report.
 * Exported as CSV this becomes a ready-to-use call list for the vendor.
 */
public record OutstandingReceivable(
        Long customerId,
        String customerName,
        String phoneNumber,
        long unpaidOrderCount,
        BigDecimal outstandingAmount,
        Instant oldestUnpaidOrderAt
) {}
