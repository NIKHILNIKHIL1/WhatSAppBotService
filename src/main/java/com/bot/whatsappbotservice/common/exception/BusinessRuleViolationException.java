package com.bot.whatsappbotservice.common.exception;

/**
 * Thrown when a request is well-formed but violates a domain rule (e.g. an invalid
 * order status transition, or insufficient stock) — maps to HTTP 422.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
