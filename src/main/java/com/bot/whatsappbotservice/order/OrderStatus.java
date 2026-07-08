package com.bot.whatsappbotservice.order;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    NEW,
    CONFIRMED,
    ACCEPTED,
    PICKING,
    PACKED,
    DISPATCHED,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            NEW, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(ACCEPTED, CANCELLED),
            ACCEPTED, Set.of(PICKING, CANCELLED),
            PICKING, Set.of(PACKED, CANCELLED),
            PACKED, Set.of(DISPATCHED, CANCELLED),
            DISPATCHED, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of()
    );

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
