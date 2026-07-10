package com.bot.whatsappbotservice.ui;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.ui.Model;

/** Model attributes behind the one-click date chips (Today / Yesterday / Last 7 days / This
 * month) shared by the dashboard and the orders list. Uses the system zone, matching how
 * {@code OrderService} interprets whole-day filter bounds. */
final class DateFilterPresets {

    private DateFilterPresets() {
    }

    static void addTo(Model model) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        model.addAttribute("today", today);
        model.addAttribute("yesterday", today.minusDays(1));
        model.addAttribute("weekAgo", today.minusDays(6));
        model.addAttribute("monthStart", today.withDayOfMonth(1));
    }
}
