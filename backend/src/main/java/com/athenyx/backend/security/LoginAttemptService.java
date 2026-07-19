package com.athenyx.backend.security;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final ConfigService configService;

    private record Attempt(String emailIp, AtomicInteger count, Instant blockedUntil) {}

    private static final long BLOCK_DURATION_MS = 15 * 60 * 1000L;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void recordFailedAttempt(String email, String ip) {
        String key = emailKey(email, ip);
        int max = configService.getInt(ConfigKey.SECURITY_MAX_FAILED_LOGINS);
        attempts.compute(key, (k, existing) -> {
            if (existing == null || isExpired(existing)) {
                return new Attempt(email, new AtomicInteger(1), Instant.now().plusMillis(BLOCK_DURATION_MS));
            }
            existing.count().incrementAndGet();
            return existing;
        });
    }

    public boolean isBlocked(String email, String ip) {
        String key = emailKey(email, ip);
        Attempt attempt = attempts.get(key);
        if (attempt == null) return false;
        if (isExpired(attempt)) {
            attempts.remove(key);
            return false;
        }
        return attempt.count().get() >= configService.getInt(ConfigKey.SECURITY_MAX_FAILED_LOGINS);
    }

    public void clear(String email, String ip) {
        attempts.remove(emailKey(email, ip));
    }

    public boolean isIpBlocked(String ip) {
        String blocklist = configService.getString(ConfigKey.SECURITY_IP_BLOCKLIST);
        if (blocklist == null || blocklist.isBlank()) return false;
        String[] blocked = blocklist.split(",");
        for (String b : blocked) {
            if (b.trim().equals(ip)) return true;
        }
        return false;
    }

    private String emailKey(String email, String ip) {
        return email.toLowerCase() + ":" + ip;
    }

    private boolean isExpired(Attempt a) {
        return Instant.now().isAfter(a.blockedUntil());
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> {
            long remaining = e.getValue().blockedUntil().toEpochMilli() - now;
            return remaining < 0;
        });
    }
}
