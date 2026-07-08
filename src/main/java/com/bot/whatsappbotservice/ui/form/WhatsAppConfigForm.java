package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.tenant.dto.UpdateWhatsAppConfigRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WhatsAppConfigForm {

    @NotBlank
    private String whatsappPhoneNumberId;

    private String whatsappBusinessAccountId;

    @NotBlank
    private String whatsappAccessToken;

    private String vendorNotificationPhoneNumber;

    public UpdateWhatsAppConfigRequest toRequest() {
        return new UpdateWhatsAppConfigRequest(
                whatsappPhoneNumberId, whatsappBusinessAccountId, whatsappAccessToken, vendorNotificationPhoneNumber);
    }
}
