package com.bot.whatsappbotservice.whatsapp.twilio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TwilioSignatureVerifierTest {

    private static final String AUTH_TOKEN = "12345";
    // The request from Twilio's security documentation ("Validating Signatures from Twilio").
    // Algorithm: HMAC-SHA1 over URL + params sorted by name (name immediately followed by value),
    // keyed with the auth token, Base64-encoded. The expected signature below was computed with an
    // independent implementation (.NET HMACSHA1) of that documented algorithm, guarding against
    // the Java implementation and this test sharing the same mistake.
    private static final String URL = "https://mycompany.com/myapp.php?foo=1&bar=2";
    private static final Map<String, String> PARAMS = Map.of(
            "CallSid", "CA1234567890ABCDE",
            "Caller", "+12349013030",
            "Digits", "1234",
            "From", "+12349013030",
            "To", "+18005551212");
    private static final String EXPECTED_SIGNATURE = "0/KCTR6DLpKmkAf8muzZqo1nDgQ=";

    private final TwilioSignatureVerifier verifier = new TwilioSignatureVerifier();

    @Test
    void acceptsTwilioDocumentedKnownVector() {
        assertThat(verifier.verify(URL, PARAMS, EXPECTED_SIGNATURE, AUTH_TOKEN)).isTrue();
    }

    @Test
    void rejectsWhenAnyParamChanges() {
        Map<String, String> tampered = new java.util.HashMap<>(PARAMS);
        tampered.put("Digits", "9999");
        assertThat(verifier.verify(URL, tampered, EXPECTED_SIGNATURE, AUTH_TOKEN)).isFalse();
    }

    @Test
    void rejectsWhenUrlDiffers() {
        assertThat(verifier.verify("https://evil.example/other", PARAMS, EXPECTED_SIGNATURE, AUTH_TOKEN)).isFalse();
    }

    @Test
    void rejectsMissingSignatureOrToken() {
        assertThat(verifier.verify(URL, PARAMS, null, AUTH_TOKEN)).isFalse();
        assertThat(verifier.verify(URL, PARAMS, "", AUTH_TOKEN)).isFalse();
        assertThat(verifier.verify(URL, PARAMS, EXPECTED_SIGNATURE, null)).isFalse();
        assertThat(verifier.verify(URL, PARAMS, EXPECTED_SIGNATURE, "")).isFalse();
        assertThat(verifier.verify(URL, PARAMS, "not base64 !!", AUTH_TOKEN)).isFalse();
    }
}
