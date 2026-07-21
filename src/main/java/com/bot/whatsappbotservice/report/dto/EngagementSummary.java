package com.bot.whatsappbotservice.report.dto;

import java.time.LocalDate;

/**
 * WhatsApp message delivery figures for one calendar day — drives the message-volume bar chart
 * and the delivery funnel on the WhatsApp Engagement report.
 */
public record EngagementSummary(
        LocalDate date,
        long sent,
        long delivered,
        long read,
        long failed,
        long inbound
) {

    /** Percentage of sent messages that were delivered; 0.0 when nothing was sent. */
    public double deliveryRate() {
        return sent == 0 ? 0.0 : (double) delivered / sent * 100.0;
    }

    /** Percentage of sent messages that were read; 0.0 when nothing was sent. */
    public double readRate() {
        return sent == 0 ? 0.0 : (double) read / sent * 100.0;
    }
}
