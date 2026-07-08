package com.bot.whatsappbotservice.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class HttpRequestUtils {

    private HttpRequestUtils() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
