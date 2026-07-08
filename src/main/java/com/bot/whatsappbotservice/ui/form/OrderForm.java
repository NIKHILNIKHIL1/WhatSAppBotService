package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderItemRequest;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Fixed 5-line-item layout rather than a JS-driven dynamic add/remove list — simpler, works
 * without JavaScript, and reliably testable. Blank lines (no product or no quantity) are filtered
 * out before building the {@link CreateOrderRequest}.
 */
@Data
public class OrderForm {

    private static final int LINE_COUNT = 5;

    @NotNull
    private Long customerId;

    private List<OrderLineForm> lines = initialLines();

    private String notes;

    public CreateOrderRequest toRequest() {
        List<OrderItemRequest> items = lines.stream()
                .filter(line -> line.getProductId() != null
                        && line.getQuantity() != null
                        && line.getQuantity().signum() > 0)
                .map(line -> new OrderItemRequest(line.getProductId(), line.getQuantity()))
                .toList();
        return new CreateOrderRequest(customerId, OrderChannel.WEB, items, notes, null);
    }

    private static List<OrderLineForm> initialLines() {
        List<OrderLineForm> lines = new ArrayList<>(LINE_COUNT);
        for (int i = 0; i < LINE_COUNT; i++) {
            lines.add(new OrderLineForm());
        }
        return lines;
    }
}
