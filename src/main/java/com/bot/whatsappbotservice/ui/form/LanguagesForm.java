package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.tenant.dto.UpdateSupportedLanguagesRequest;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;

@Data
public class LanguagesForm {

    private Set<String> supportedLanguageCodes = new LinkedHashSet<>();

    public UpdateSupportedLanguagesRequest toRequest() {
        return new UpdateSupportedLanguagesRequest(supportedLanguageCodes);
    }
}
