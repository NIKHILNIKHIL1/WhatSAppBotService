package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.inventory.InventoryTransactionType;
import com.bot.whatsappbotservice.inventory.dto.AdjustStockRequest;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class InventoryAdjustmentForm {

    @NotNull
    private InventoryTransactionType transactionType;

    @NotNull
    private BigDecimal quantityDelta;

    private String notes;

    public AdjustStockRequest toRequest() {
        return new AdjustStockRequest(transactionType, quantityDelta, "MANUAL", null, notes);
    }
}
