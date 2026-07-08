package com.bot.whatsappbotservice.inventory.dto;

import com.bot.whatsappbotservice.inventory.InventoryTransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record InventoryTransactionResponse(
        Long id,
        Long productId,
        InventoryTransactionType transactionType,
        BigDecimal quantityDelta,
        BigDecimal quantityAfter,
        String referenceType,
        Long referenceId,
        String notes,
        Instant createdAt
) {
}
