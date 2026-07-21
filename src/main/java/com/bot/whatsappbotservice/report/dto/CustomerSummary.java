package com.bot.whatsappbotservice.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lifetime order and spend figures for one customer — drives the top-customers table on the
 * Customer Analytics report.
 */
public record CustomerSummary(
        Long customerId,
        String fullName,
        String phoneNumber,
        long lifetimeOrders,
        BigDecimal lifetimeSpend,
        Instant lastOrderAt
) {}
