package com.bot.whatsappbotservice.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EncryptionKeyInitializer {

    public EncryptionKeyInitializer(@Value("${app.encryption.key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.key must be configured (set the ENCRYPTION_KEY environment variable "
                            + "to a base64-encoded 32-byte AES key)");
        }
        EncryptedStringConverter.initKey(base64Key);
    }
}
