package com.athenyx.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled-task execution.
 *
 * <p>Registered jobs:
 * <ul>
 *   <li>{@link com.athenyx.backend.service.audit.AuditRetentionService}
 *       — runs at 03:00 on the 1st of every month.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
