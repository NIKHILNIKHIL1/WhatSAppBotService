package com.bot.whatsappbotservice.common;

/**
 * Canonicalizes phone numbers to {@code +<digits>} (E.164-shaped). This matters because the same
 * number arrives in different shapes at different boundaries: Meta webhooks send the wa_id without
 * a plus ({@code 919876543210}), Twilio sends {@code whatsapp:+919876543210}, and vendors type
 * numbers with spaces or dashes into the registration form. Customer identity (the tenant+phone
 * unique key) only works if every boundary normalizes through here first.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** Strips everything but digits and prepends {@code +}; returns {@code null} for input with no
     * digits at all. Does not validate country codes — that stays with the form/API validators. */
    public static String toE164(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.isEmpty() ? null : "+" + digits;
    }
}
