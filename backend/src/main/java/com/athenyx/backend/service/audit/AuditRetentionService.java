package com.athenyx.backend.service.audit;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRetentionService {

    private final AuditLogRepository auditLogRepository;
    private final ConfigService configService;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional
    public void purgeOldEntries() {
        int retentionDays = configService.getInt(ConfigKey.AUDIT_RETENTION_DAYS);
        LocalDateTime cutoff = cutoffFor(retentionDays);
        long deleted = auditLogRepository.countByCreatedAtBefore(cutoff);
        auditLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("audit.purge deleted={} cutoff={} retention-days={}", deleted, cutoff, retentionDays);
    }

    @Transactional
    public int purgeNow() {
        int retentionDays = configService.getInt(ConfigKey.AUDIT_RETENTION_DAYS);
        LocalDateTime cutoff = cutoffFor(retentionDays);
        long count = auditLogRepository.countByCreatedAtBefore(cutoff);
        auditLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("audit.purge-now deleted={} cutoff={} retention-days={}", count, cutoff, retentionDays);
        return (int) count;
    }

    private LocalDateTime cutoffFor(int days) {
        return LocalDateTime.now(clock).minusDays(days);
    }
}
