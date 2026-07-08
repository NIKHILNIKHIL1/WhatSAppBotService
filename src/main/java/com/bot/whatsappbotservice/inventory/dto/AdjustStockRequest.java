package com.bot.whatsappbotservice.inventory.dto;

import com.bot.whatsappbotservice.inventory.InventoryTransactionType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AdjustStockRequest(
        @NotNull InventoryTransactionType transactionType,
        @NotNull BigDecimal quantityDelta,
        String referenceType,
        Long referenceId,
        String notes
) {
}
