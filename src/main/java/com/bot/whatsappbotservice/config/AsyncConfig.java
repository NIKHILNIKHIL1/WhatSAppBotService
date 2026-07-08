package com.bot.whatsappbotservice.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Meta expects the webhook to ack within a few seconds or it starts retrying (and can eventually
 * disable the subscription). Processing (DB writes, outbound Graph API calls) happens on this
 * pool so the controller can return 200 immediately.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "whatsappTaskExecutor")
    public Executor whatsappTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("whatsapp-");
        executor.initialize();
        return executor;
    }
}
