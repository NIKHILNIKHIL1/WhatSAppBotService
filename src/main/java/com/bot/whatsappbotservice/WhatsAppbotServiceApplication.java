package com.bot.whatsappbotservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WhatsAppbotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsAppbotServiceApplication.class, args);
    }

}
