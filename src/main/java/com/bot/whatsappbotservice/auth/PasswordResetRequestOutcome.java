package com.bot.whatsappbotservice.auth;

/**
 * Result of a password-reset request. {@code UNKNOWN_ACCOUNT} exists so the UI can show the same
 * neutral "if an account exists…" message as {@code CODE_SENT} (no account enumeration), while
 * the delivery-problem outcomes are surfaced honestly — a real vendor stuck without a configured
 * WhatsApp number needs to know why no code will ever arrive.
 */
public enum PasswordResetRequestOutcome {
    CODE_SENT,
    UNKNOWN_ACCOUNT,
    NO_DELIVERY_CHANNEL,
    SEND_FAILED
}
