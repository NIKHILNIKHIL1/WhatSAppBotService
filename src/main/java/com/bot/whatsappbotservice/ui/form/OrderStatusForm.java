package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.order.dto.UpdateOrderStatusRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderStatusForm {

    @NotNull
    private OrderStatus status;

    private String notes;

    public UpdateOrderStatusRequest toRequest() {
        return new UpdateOrderStatusRequest(status, notes);
    }
}
