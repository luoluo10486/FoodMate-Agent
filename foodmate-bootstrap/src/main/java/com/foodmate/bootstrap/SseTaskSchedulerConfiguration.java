package com.foodmate.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 为所有 Server-Sent Event 轮询任务提供统一的有界调度器。 */
@Configuration
public class SseTaskSchedulerConfiguration {
    @Bean
    public ThreadPoolTaskScheduler sseTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("foodmate-sse-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
