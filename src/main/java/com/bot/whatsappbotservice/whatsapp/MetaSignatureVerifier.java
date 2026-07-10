package com.bot.whatsappbotservice.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Verifies Meta's {@code X-Hub-Signature-256} webhook header: HMAC-SHA256 of the raw request body,
 * keyed with the app secret, hex-encoded and prefixed with {@code sha256=}. The HMAC must be
 * computed over the exact bytes Meta sent — deserializing and re-serializing the payload first
 * would silently break verification — which is why the controller hands the raw body here before
 * any JSON parsing.
 *
 * <p>When no app secret is configured, verification is skipped with a startup warning so local
 * development (curl, no Meta app) keeps working. In {@code prod}/{@code docker}, the same missing
 * secret instead fails startup — those profiles are real deployments, so silently accepting
 * unsigned webhook traffic is never the right default there.
 */
@Component
public class MetaSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(MetaSignatureVerifier.class);
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Set<String> PROFILES_REQUIRING_SECRET = Set.of("prod", "docker");

    private final WhatsAppProperties properties;
    private final boolean enabled;

    public MetaSignatureVerifier(WhatsAppProperties properties, Environment environment) {
        this.properties = properties;
        this.enabled = properties.appSecret() != null && !properties.appSecret().isBlank();
        if (!enabled) {
            if (requiresSecret(environment)) {
                throw new IllegalStateException(
                        "app.whatsapp.meta.app-secret must be configured (set the WHATSAPP_APP_SECRET "
                                + "environment variable) when running with profile(s) "
                                + String.join(",", environment.getActiveProfiles()));
            }
            log.warn("app.whatsapp.meta.app-secret is not set — webhook signature verification is DISABLED. "
                    + "Set WHATSAPP_APP_SECRET before exposing this service; without it anyone who knows the "
                    + "webhook URL can forge inbound WhatsApp messages.");
        }
    }

    private static boolean requiresSecret(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (PROFILES_REQUIRING_SECRET.contains(profile)) {
                return true;
            }
        }
        return false;
    }

    public boolean verify(byte[] rawBody, String signatureHeader) {
        if (!enabled) {
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(computeHmac(rawBody), provided);
    }

    private byte[] computeHmac(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.appSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(rawBody);
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256 is mandated by the JCA spec, and the key is non-blank — unreachable.
            throw new IllegalStateException("Unable to compute webhook HMAC", e);
        }
    }
}
