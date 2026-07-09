package com.bot.whatsappbotservice.storefront.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyForm {

    @NotBlank
    private String phoneNumber;

    @NotBlank
    private String code;
}
