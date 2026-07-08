package com.bot.whatsappbotservice.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.ACCEPTED)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.PICKING)).isTrue();
        assertThat(OrderStatus.PICKING.canTransitionTo(OrderStatus.PACKED)).isTrue();
        assertThat(OrderStatus.PACKED.canTransitionTo(OrderStatus.DISPATCHED)).isTrue();
        assertThat(OrderStatus.DISPATCHED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void cancellationIsAllowedFromAnyNonTerminalState() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PICKING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PACKED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void terminalStatesAllowNoFurtherTransitions() {
        assertThat(OrderStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target)).isFalse();
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void cannotSkipStagesOrGoBackwards() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.PACKED)).isFalse();
        assertThat(OrderStatus.DISPATCHED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.PICKING.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }
}
