package com.bot.whatsappbotservice.order;

/**
 * Declaration order IS the fulfillment sequence — {@link #canTransitionTo} compares ordinals, so a
 * new status must be inserted at its chain position, with CANCELLED kept last (it sits outside the
 * chain and is special-cased).
 */
public enum OrderStatus {
    NEW,
    CONFIRMED,
    ACCEPTED,
    PICKING,
    PACKED,
    DISPATCHED,
    DELIVERED,
    CANCELLED;

    /**
     * Any forward move along the fulfillment chain is allowed, including skipping stages — a
     * vendor who hands the order over at the counter shouldn't have to click through PICKING and
     * PACKED to reach DELIVERED. Backward moves and leaving a terminal state are not allowed, and
     * cancelling stops being possible once goods are DISPATCHED (a cancel releases stock, which
     * would wrongly restock goods already out the door).
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (isTerminal() || target == this) {
            return false;
        }
        if (target == CANCELLED) {
            return ordinal() < DISPATCHED.ordinal();
        }
        return target.ordinal() > ordinal();
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
