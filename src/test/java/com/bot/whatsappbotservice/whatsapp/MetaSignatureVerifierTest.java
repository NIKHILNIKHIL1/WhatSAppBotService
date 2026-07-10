package com.bot.whatsappbotservice.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class MetaSignatureVerifierTest {

    private static final String APP_SECRET = "test-app-secret";
    private static final byte[] BODY = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

    private final MetaSignatureVerifier verifier =
            new MetaSignatureVerifier(new WhatsAppProperties(null, null, APP_SECRET), env("local"));

    @Test
    void acceptsCorrectlySignedBody() {
        assertThat(verifier.verify(BODY, "sha256=" + hmacHex(BODY))).isTrue();
    }

    @Test
    void rejectsTamperedBody() {
        byte[] tampered = "{\"object\":\"tampered\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(tampered, "sha256=" + hmacHex(BODY))).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedHeader() {
        assertThat(verifier.verify(BODY, null)).isFalse();
        assertThat(verifier.verify(BODY, "")).isFalse();
        assertThat(verifier.verify(BODY, hmacHex(BODY))).isFalse(); // missing sha256= prefix
        assertThat(verifier.verify(BODY, "sha256=not-hex!")).isFalse();
    }

    @Test
    void skipsVerificationWhenNoAppSecretConfiguredOutsideProdProfiles() {
        MetaSignatureVerifier disabled =
                new MetaSignatureVerifier(new WhatsAppProperties(null, null, ""), env("local"));
        assertThat(disabled.verify(BODY, null)).isTrue();
    }

    @Test
    void refusesToStartWithoutAppSecretInProdProfile() {
        assertThatThrownBy(() -> new MetaSignatureVerifier(new WhatsAppProperties(null, null, ""), env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_APP_SECRET");
    }

    @Test
    void refusesToStartWithoutAppSecretInDockerProfile() {
        assertThatThrownBy(() -> new MetaSignatureVerifier(new WhatsAppProperties(null, null, ""), env("docker")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_APP_SECRET");
    }

    private static MockEnvironment env(String activeProfile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return environment;
    }

    private String hmacHex(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
