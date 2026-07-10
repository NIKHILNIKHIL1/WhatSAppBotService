package com.bot.whatsappbotservice.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void canonicalizesEveryInboundShapeToTheSameNumber() {
        // Meta wa_id (no plus), Twilio (whatsapp: prefix), vendor-typed (spaces/dashes) — all one
        // customer identity.
        assertThat(PhoneNumbers.toE164("919876543210")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.toE164("+919876543210")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.toE164("whatsapp:+919876543210")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.toE164("+91 98765-43210")).isEqualTo("+919876543210");
    }

    @Test
    void inputWithoutDigitsIsNull() {
        assertThat(PhoneNumbers.toE164(null)).isNull();
        assertThat(PhoneNumbers.toE164("")).isNull();
        assertThat(PhoneNumbers.toE164("not-a-number")).isNull();
    }
}
