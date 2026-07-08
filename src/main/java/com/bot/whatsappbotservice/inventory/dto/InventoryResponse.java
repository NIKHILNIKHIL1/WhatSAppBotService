package com.bot.whatsappbotservice.inventory.dto;

import java.math.BigDecimal;

public record InventoryResponse(
        Long id,
        Long productId,
        BigDecimal quantityOnHand,
        BigDecimal reorderLevel
) {
}
