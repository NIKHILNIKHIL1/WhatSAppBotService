package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.auth.dto.RegisterRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;

/**
 * Mutable Thymeleaf form-backing counterpart to {@link RegisterRequest} — records can't back
 * {@code th:field} two-way binding, which needs real getter/setter pairs.
 */
@Data
public class RegisterForm {

    @NotBlank
    @Size(max = 255)
    private String tenantName;

    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]{3,100}$", message = "must be lowercase letters, numbers and hyphens only")
    private String slug;

    @NotBlank
    @Size(max = 255)
    private String adminFullName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String adminEmail;

    @NotBlank
    @Size(min = 8, max = 100)
    private String adminPassword;

    private String currencyCode;
    private String timezone;
    private String defaultLanguageCode;
    private Set<String> supportedLanguageCodes = new LinkedHashSet<>();

    public RegisterRequest toRequest() {
        return new RegisterRequest(tenantName, slug, adminFullName, adminEmail, adminPassword,
                currencyCode, timezone, defaultLanguageCode, supportedLanguageCodes);
    }
}
