package org.jds.edgar4j.job;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class for enabling scheduled jobs.
 */
@Configuration
@EnableScheduling
public class JobSchedulerConfig {
    // Scheduling is enabled via @EnableScheduling annotation
    // Individual jobs use @Scheduled annotations
}
