package com.bot.whatsappbotservice.ui.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ReorderLevelForm {

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal reorderLevel;
}
