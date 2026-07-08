package com.bot.whatsappbotservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot 4.1 no longer auto-configures a {@link RestClient.Builder} bean for
 * {@code spring-boot-starter-webmvc} projects (that wiring moved to a separate module this
 * project doesn't depend on). {@link com.bot.whatsappbotservice.whatsapp.WhatsAppClient} needs
 * one to call the Meta Graph API, so it's supplied explicitly here.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
