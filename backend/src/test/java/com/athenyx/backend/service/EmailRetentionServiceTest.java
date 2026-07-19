package com.athenyx.backend.service;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.repository.AiExplanationRepository;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailRetentionServiceTest {

    @Mock
    private EmailRepository emailRepository;

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private EmailAnalysisRepository emailAnalysisRepository;

    @Mock
    private AiExplanationRepository aiExplanationRepository;

    @Mock
    private ConfigService configService;

    private EmailRetentionService service;
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 13, 12, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new EmailRetentionService(
                emailRepository, reminderRepository,
                emailAnalysisRepository, aiExplanationRepository,
                configService, clock);
        when(configService.getInt(ConfigKey.EMAIL_RETENTION_DAYS)).thenReturn(30);
    }

    @Test
    void purgeNow_noOldEmails_returnsZero() {
        when(emailRepository.findIdsOlderThan(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        EmailRetentionService.PurgeResult result = service.purgeNow();

        assertThat(result.purgedCount()).isZero();
        assertThat(result.skippedDueToReminders()).isZero();
        verify(emailRepository, never()).deleteAllByIdIn(anyList());
        verify(reminderRepository, never()).deleteByEmailIdIn(anyList());
        verify(emailAnalysisRepository, never()).deleteByEmailIdIn(anyList());
        verify(aiExplanationRepository, never()).deleteByEmailIdIn(anyList());
    }

    @Test
    void purgeNow_allEmailsProtectedByActiveReminders_returnsZero() {
        List<Long> ids = List.of(1L, 2L, 3L);
        when(emailRepository.findIdsOlderThan(any(LocalDateTime.class))).thenReturn(ids);
        when(reminderRepository.findActiveEmailIds(ids)).thenReturn(ids);

        EmailRetentionService.PurgeResult result = service.purgeNow();

        assertThat(result.purgedCount()).isZero();
        assertThat(result.skippedDueToReminders()).isEqualTo(3);
        verify(emailRepository, never()).deleteAllByIdIn(anyList());
        verify(reminderRepository, never()).deleteByEmailIdIn(anyList());
    }

    @Test
    void purgeNow_someEmailsProtected_deletesOnlyUnprotected() {
        List<Long> ids = List.of(1L, 2L, 3L);
        when(emailRepository.findIdsOlderThan(any(LocalDateTime.class))).thenReturn(ids);
        when(reminderRepository.findActiveEmailIds(ids)).thenReturn(List.of(2L));
        when(reminderRepository.deleteByEmailIdIn(List.of(1L, 3L))).thenReturn(0);
        when(emailAnalysisRepository.deleteByEmailIdIn(List.of(1L, 3L))).thenReturn(0);
        when(aiExplanationRepository.deleteByEmailIdIn(List.of(1L, 3L))).thenReturn(0);
        when(emailRepository.deleteAllByIdIn(List.of(1L, 3L))).thenReturn(2);

        EmailRetentionService.PurgeResult result = service.purgeNow();

        assertThat(result.purgedCount()).isEqualTo(2);
        assertThat(result.skippedDueToReminders()).isEqualTo(1);
        verify(emailRepository).deleteAllByIdIn(List.of(1L, 3L));
    }

    @Test
    void purgeNow_noReminders_deletesAllOldEmails() {
        List<Long> ids = List.of(1L, 2L);
        when(emailRepository.findIdsOlderThan(any(LocalDateTime.class))).thenReturn(ids);
        when(reminderRepository.findActiveEmailIds(ids)).thenReturn(Collections.emptyList());
        when(reminderRepository.deleteByEmailIdIn(ids)).thenReturn(0);
        when(emailAnalysisRepository.deleteByEmailIdIn(ids)).thenReturn(0);
        when(aiExplanationRepository.deleteByEmailIdIn(ids)).thenReturn(0);
        when(emailRepository.deleteAllByIdIn(ids)).thenReturn(2);

        EmailRetentionService.PurgeResult result = service.purgeNow();

        assertThat(result.purgedCount()).isEqualTo(2);
        assertThat(result.skippedDueToReminders()).isZero();
    }

    @Test
    void purgeNow_usesRetentionDaysFromConfigService() {
        when(configService.getInt(ConfigKey.EMAIL_RETENTION_DAYS)).thenReturn(7);
        when(emailRepository.findIdsOlderThan(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        service.purgeNow();

        verify(emailRepository).findIdsOlderThan(LocalDateTime.of(2026, 7, 6, 12, 0));
    }

    @Test
    void purgeNow_deletesChildrenBeforeEmails_inOrder() {
        List<Long> ids = List.of(1L, 2L);
        when(emailRepository.findIdsOlderThan(any(LocalDateTime.class))).thenReturn(ids);
        when(reminderRepository.findActiveEmailIds(ids)).thenReturn(Collections.emptyList());
        when(reminderRepository.deleteByEmailIdIn(ids)).thenReturn(3);
        when(emailAnalysisRepository.deleteByEmailIdIn(ids)).thenReturn(2);
        when(aiExplanationRepository.deleteByEmailIdIn(ids)).thenReturn(1);
        when(emailRepository.deleteAllByIdIn(ids)).thenReturn(2);

        service.purgeNow();

        InOrder inOrder = inOrder(reminderRepository, aiExplanationRepository, emailAnalysisRepository, emailRepository);
        inOrder.verify(reminderRepository).deleteByEmailIdIn(ids);
        inOrder.verify(aiExplanationRepository).deleteByEmailIdIn(ids);
        inOrder.verify(emailAnalysisRepository).deleteByEmailIdIn(ids);
        inOrder.verify(emailRepository).deleteAllByIdIn(ids);
    }

    @Test
    void purgeNow_purgedCountReflectsEmailsOnly() {
        List<Long> ids = List.of(1L, 2L);
        when(emailRepository.findIdsOlderThan(any(LocalDateTime.class))).thenReturn(ids);
        when(reminderRepository.findActiveEmailIds(ids)).thenReturn(Collections.emptyList());
        when(reminderRepository.deleteByEmailIdIn(ids)).thenReturn(5);
        when(emailAnalysisRepository.deleteByEmailIdIn(ids)).thenReturn(4);
        when(aiExplanationRepository.deleteByEmailIdIn(ids)).thenReturn(3);
        when(emailRepository.deleteAllByIdIn(ids)).thenReturn(2);

        EmailRetentionService.PurgeResult result = service.purgeNow();

        assertThat(result.purgedCount()).isEqualTo(2);
    }
}
