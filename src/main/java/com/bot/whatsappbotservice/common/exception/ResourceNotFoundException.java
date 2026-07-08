package com.bot.whatsappbotservice.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String entityName, Object identifier) {
        return new ResourceNotFoundException(entityName + " not found: " + identifier);
    }
}
