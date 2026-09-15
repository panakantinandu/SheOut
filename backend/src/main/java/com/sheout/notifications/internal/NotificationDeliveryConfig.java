package com.sheout.notifications.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Notifications are delivered off the thread that caused them.
 * <p>
 * Every send is a call to somebody else's servers - FCM, an SMTP host,
 * Twilio - and each can take seconds or time out. Run inline, those calls
 * sat on the rider's booking request and inside dispatch's offer round: one
 * slow provider made booking slow, and a round offering three partners paid
 * for three pushes before it returned.
 * <p>
 * A small bounded pool. When it is full the caller runs the task itself
 * rather than dropping it - a notification delayed is better than a
 * notification lost, and backlog here means providers are down anyway.
 */
@Configuration
@EnableAsync
class NotificationDeliveryConfig {

    static final String EXECUTOR = "notificationExecutor";

    @Bean(name = EXECUTOR)
    Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
