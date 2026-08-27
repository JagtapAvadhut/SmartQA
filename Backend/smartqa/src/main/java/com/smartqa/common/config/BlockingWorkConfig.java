package com.smartqa.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class BlockingWorkConfig {

    public static final String BLOCKING_SCHEDULER = "smartqaBlockingScheduler";

    @Bean(name = BLOCKING_SCHEDULER)
    public Scheduler blockingScheduler() {
        return Schedulers.boundedElastic();
    }
}
