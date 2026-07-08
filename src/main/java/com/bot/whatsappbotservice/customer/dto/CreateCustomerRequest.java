package com.bot.whatsappbotservice.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCustomerRequest(
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be an E.164 phone number, e.g. +14155552671")
        String phoneNumber,

        String fullName,

        String preferredLanguageCode
) {
}
