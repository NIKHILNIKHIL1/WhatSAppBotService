package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.order.PaymentMethod;
import com.bot.whatsappbotservice.order.dto.RecordPaymentRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class PaymentForm {

    @NotNull
    private PaymentMethod method;

    @NotNull
    @Positive
    private BigDecimal amount;

    @Size(max = 150)
    private String reference;

    public RecordPaymentRequest toRequest() {
        return new RecordPaymentRequest(method, amount, reference);
    }
}
