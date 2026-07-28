package com.saleha.reviewservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableScheduling
@EnableAsync
public class AsyncConfig {

    // Dedicated executor for @Async notification work, instead of Spring's
    // default SimpleAsyncTaskExecutor (which creates a new thread per task
    // with no pooling/limits).
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notif-");
        executor.initialize();

        return executor;
    }
}
