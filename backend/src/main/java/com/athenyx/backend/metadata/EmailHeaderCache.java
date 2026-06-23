package com.athenyx.backend.metadata;

import com.athenyx.backend.entity.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailHeaderCache {

    private final MetadataAnalyzer metadataAnalyzer;
    private final ConcurrentHashMap<Long, CachedMetadata> cache = new ConcurrentHashMap<>();

    public MetadataAnalysisResult getOrAnalyze(Email email) {
        CachedMetadata existing = cache.get(email.getId());
        if (existing != null && !existing.expired()) {
            log.debug("EmailHeaderCache hit for email {}", email.getId());
            return existing.result();
        }
        log.debug("EmailHeaderCache miss for email {}, running metadata analysis", email.getId());
        MetadataAnalysisResult result = metadataAnalyzer.analyze(email);
        cache.put(email.getId(), new CachedMetadata(result, LocalDateTime.now()));
        return result;
    }

    public void evict(Long emailId) {
        cache.remove(emailId);
        log.debug("Evicted cache for email {}", emailId);
    }

    private record CachedMetadata(MetadataAnalysisResult result, LocalDateTime cachedAt) {
        boolean expired() {
            return cachedAt.plusHours(24).isBefore(LocalDateTime.now());
        }
    }
}
