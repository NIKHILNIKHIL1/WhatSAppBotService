package com.bot.whatsappbotservice.inventory.dto;

import java.math.BigDecimal;

public record InventoryOverviewResponse(
        Long productId,
        String productSku,
        String productName,
        BigDecimal quantityOnHand,
        BigDecimal reorderLevel
) {
}
