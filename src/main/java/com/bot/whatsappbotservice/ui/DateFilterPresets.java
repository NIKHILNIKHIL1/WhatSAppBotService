package com.bot.whatsappbotservice.ui;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.ui.Model;

/** Model attributes behind the one-click date chips (Today / Yesterday / Last 7 days / This
 * month) shared by the dashboard, orders list, and report pages.  The tenant timezone is passed
 * explicitly so preset links resolve to the correct calendar day for tenants outside UTC. */
final class DateFilterPresets {

    static final ZoneId UTC = ZoneId.of("UTC");

    private DateFilterPresets() {
    }

    static void addTo(Model model, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        model.addAttribute("today", today);
        model.addAttribute("yesterday", today.minusDays(1));
        model.addAttribute("weekAgo", today.minusDays(6));
        model.addAttribute("monthStart", today.withDayOfMonth(1));
    }

    /** Resolves a nullable/blank timezone string to a {@code ZoneId}, falling back to UTC. */
    static ZoneId resolveZone(String tz) {
        return (tz != null && !tz.isBlank()) ? ZoneId.of(tz) : UTC;
    }
}
