package com.bot.whatsappbotservice.ui.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordForm {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;
}
