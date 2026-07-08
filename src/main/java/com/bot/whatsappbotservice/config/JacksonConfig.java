package com.bot.whatsappbotservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4.1's Jackson auto-configuration wires {@code tools.jackson.databind.ObjectMapper}
 * (Jackson 3) as the primary JSON mapper; it does not register a classic
 * {@code com.fasterxml.jackson.databind.ObjectMapper} bean. Several internal services
 * (audit JSON snapshots, WhatsApp payload handling) are written against classic Jackson 2,
 * so this bean supplies it explicitly.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
