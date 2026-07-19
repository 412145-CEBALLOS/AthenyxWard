package com.athenyx.backend.security;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

    private final ConfigService configService;

    private static final long WINDOW_MS = 3600 * 1000L;
    private static final int ANALYSIS_PER_HOUR = 60;

    private record Window(Long userId, String endpoint, AtomicInteger count, Instant windowStart) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public OptionalLong tryAcquire(Long userId, String endpoint) {
        String key = userId + ":" + endpoint;
        long now = System.currentTimeMillis();
        int limit = getLimit(endpoint);

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart().toEpochMilli() >= WINDOW_MS) {
                return new Window(userId, endpoint, new AtomicInteger(1), Instant.ofEpochMilli(now));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (window.count().get() > limit) {
            long retryAfterMs = WINDOW_MS - (now - window.windowStart().toEpochMilli());
            return OptionalLong.of(java.time.Duration.ofMillis(retryAfterMs).toSeconds());
        }
        return OptionalLong.empty();
    }

    private int getLimit(String endpoint) {
        return switch (endpoint) {
            case "analysis" -> ANALYSIS_PER_HOUR;
            case "explain" -> configService.getInt(ConfigKey.RATELIMIT_EXPLAIN_PER_HOUR);
            default -> 1000;
        };
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        windows.entrySet().removeIf(e -> e.getValue().windowStart().toEpochMilli() < cutoff);
    }
}
