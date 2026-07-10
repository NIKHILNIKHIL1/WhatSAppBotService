package com.bot.whatsappbotservice.whatsapp.twilio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Validates Twilio's {@code X-Twilio-Signature} header: HMAC-SHA1 over the full request URL
 * concatenated with every POST parameter name+value sorted alphabetically by name, keyed with the
 * account's auth token, Base64-encoded. Twilio credentials live per tenant, so the caller resolves
 * the tenant (by the "To" number) and passes that tenant's auth token in.
 */
@Component
public class TwilioSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA1";

    public boolean verify(String url, Map<String, String> params, String signatureHeader, String authToken) {
        if (signatureHeader == null || signatureHeader.isBlank()
                || authToken == null || authToken.isBlank()
                || url == null || url.isBlank()) {
            return false;
        }

        StringBuilder data = new StringBuilder(url);
        new TreeMap<>(params).forEach((name, value) -> data.append(name).append(value != null ? value : ""));

        byte[] provided;
        try {
            provided = Base64.getDecoder().decode(signatureHeader);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(computeHmac(data.toString(), authToken), provided);
    }

    private byte[] computeHmac(String data, String authToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA1 is mandated by the JCA spec, and the key is non-blank — unreachable.
            throw new IllegalStateException("Unable to compute Twilio webhook HMAC", e);
        }
    }
}
