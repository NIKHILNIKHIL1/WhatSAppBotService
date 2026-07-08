package com.bot.whatsappbotservice.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
}
