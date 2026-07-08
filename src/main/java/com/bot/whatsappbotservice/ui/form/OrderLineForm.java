package com.bot.whatsappbotservice.ui.form;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderLineForm {

    private Long productId;
    private BigDecimal quantity;
}
