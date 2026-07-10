package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.customer.CustomerStatus;
import com.bot.whatsappbotservice.customer.dto.CreateCustomerRequest;
import com.bot.whatsappbotservice.customer.dto.UpdateCustomerRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CustomerForm {

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be an E.164 phone number, e.g. +14155552671")
    private String phoneNumber;

    @NotBlank
    private String fullName;
    private String preferredLanguageCode;
    private CustomerStatus status = CustomerStatus.ACTIVE;

    public CreateCustomerRequest toCreateRequest() {
        return new CreateCustomerRequest(phoneNumber, fullName, preferredLanguageCode);
    }

    public UpdateCustomerRequest toUpdateRequest() {
        return new UpdateCustomerRequest(fullName, preferredLanguageCode, status);
    }
}
