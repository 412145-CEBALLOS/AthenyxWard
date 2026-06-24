package com.athenyx.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Centralised {@link Clock} bean so any service that needs the
 * current time (notifications, token refresh, …) can be unit-tested
 * with a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
