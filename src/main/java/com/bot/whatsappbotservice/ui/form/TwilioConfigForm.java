package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.tenant.dto.UpdateTwilioConfigRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwilioConfigForm {

    @NotBlank
    private String twilioAccountSid;

    @NotBlank
    private String twilioAuthToken;

    @NotBlank
    private String twilioWhatsAppNumber;

    private boolean activateTwilio;

    public UpdateTwilioConfigRequest toRequest() {
        return new UpdateTwilioConfigRequest(twilioAccountSid, twilioAuthToken, twilioWhatsAppNumber);
    }
}
