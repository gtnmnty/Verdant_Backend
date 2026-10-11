package com.verdant.salon_ecomm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class NewsletterAsyncConfig {

    // One dedicated thread: campaigns run strictly one after another and a long send can never starve the app's
    // other @Async work (verification emails, notifications). A second campaign waits in the small queue.
    @Bean(name = "newsletterExecutor")
    public Executor newsletterExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("newsletter-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Let an in-flight email finish when the app shuts down instead of cutting it off mid-send.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
