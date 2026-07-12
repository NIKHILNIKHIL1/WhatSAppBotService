package com.bot.whatsappbotservice.auth;

/**
 * Outcome of a reset request plus, in on-screen delivery mode only, the plaintext code to display.
 * {@code onScreenCode} is {@code null} whenever the code went out over WhatsApp (or nothing was
 * generated at all) — the plaintext must never travel further than the one response that shows it.
 */
public record PasswordResetRequestResult(PasswordResetRequestOutcome outcome, String onScreenCode) {

    public static PasswordResetRequestResult of(PasswordResetRequestOutcome outcome) {
        return new PasswordResetRequestResult(outcome, null);
    }
}
