package com.athenyx.backend.service.audit;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditRetentionServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ConfigService configService;

    private AuditRetentionService service;
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 13, 12, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new AuditRetentionService(auditLogRepository, configService, clock);
        when(configService.getInt(ConfigKey.AUDIT_RETENTION_DAYS)).thenReturn(30);
    }

    @Test
    void purgeOldEntries_deletesEntriesOlderThanCutoff() {
        when(auditLogRepository.countByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(5L);

        service.purgeOldEntries();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).deleteByCreatedAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isEqualTo(LocalDateTime.of(2026, 6, 13, 12, 0));
    }

    @Test
    void purgeOldEntries_reportsDeletedCount() {
        when(auditLogRepository.countByCreatedAtBefore(any())).thenReturn(3L);

        service.purgeOldEntries();

        verify(auditLogRepository).countByCreatedAtBefore(any(LocalDateTime.class));
        verify(auditLogRepository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void purgeNow_deletesEntriesOlderThanCutoff() {
        when(auditLogRepository.countByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(5L);

        int result = service.purgeNow();

        assertThat(result).isEqualTo(5);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).deleteByCreatedAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isEqualTo(LocalDateTime.of(2026, 6, 13, 12, 0));
    }

    @Test
    void purgeNow_usesCurrentValueFromConfigService() {
        when(auditLogRepository.countByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(0L);
        when(configService.getInt(ConfigKey.AUDIT_RETENTION_DAYS)).thenReturn(7);

        service.purgeNow();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).countByCreatedAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 12, 0));
    }
}
