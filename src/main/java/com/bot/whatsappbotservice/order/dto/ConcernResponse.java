package com.bot.whatsappbotservice.order.dto;

import com.bot.whatsappbotservice.order.ConcernStatus;
import java.time.Instant;

public record ConcernResponse(
        Long id,
        String caption,
        ConcernStatus status,
        Instant createdAt
) {
}
