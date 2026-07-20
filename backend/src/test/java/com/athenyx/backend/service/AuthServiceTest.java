package com.athenyx.backend.service;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.dto.ActiveSessionResponse;
import com.athenyx.backend.dto.UserInfo;
import com.athenyx.backend.dto.UserUsageResponse;
import com.athenyx.backend.entity.RefreshToken;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private EmailAnalysisRepository emailAnalysisRepository;
    @Mock private AiExplanationRepository aiExplanationRepository;
    @Mock private ReminderRepository reminderRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ConfigService configService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private AuthService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .googleId("gid")
                .email("u@example.com")
                .name("User")
                .pictureUrl("p")
                .role(Role.TRIAL)
                .accessibilityMode(true)
                .analysisCount(5)
                .trialEndDate(LocalDateTime.now().plusDays(15))
                .lastLoginAt(LocalDateTime.now().minusHours(2))
                .emailVerified(true)
                .build();
    }

    @Test
    void getUserInfo_returnsLastLoginAtAndEmailVerified() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo info = service.getUserInfo(1L);

        assertThat(info.lastLoginAt()).isNotNull();
        assertThat(info.emailVerified()).isTrue();
    }

    @Test
    void getUserInfo_returnsMappedDto() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo info = service.getUserInfo(1L);

        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.name()).isEqualTo("User");
        assertThat(info.email()).isEqualTo("u@example.com");
        assertThat(info.pictureUrl()).isEqualTo("p");
        assertThat(info.role()).isEqualTo(Role.TRIAL);
        assertThat(info.trialExpired()).isFalse();
        assertThat(info.accessibilityMode()).isTrue();
    }

    @Test
    void getUserInfo_trialExpiredFlagReflectsTrialEndDate() {
        user.setRole(Role.TRIAL);
        user.setTrialEndDate(LocalDateTime.now().minusDays(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo info = service.getUserInfo(1L);

        assertThat(info.trialExpired()).isTrue();
    }

    @Test
    void getUserInfo_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserInfo(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void updateAccessibilityMode_persistsAndReturnsUpdatedInfo() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo info = service.updateAccessibilityMode(1L, false);

        assertThat(user.isAccessibilityMode()).isFalse();
        assertThat(info.accessibilityMode()).isFalse();
        verify(userRepository, times(2)).findById(1L);
    }

    @Test
    void getUserUsage_returnsUsageForTrialUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(configService.getInt(ConfigKey.TRIAL_ANALYSIS_LIMIT)).thenReturn(20);
        when(emailRepository.countByUserId(1L)).thenReturn(10L);
        when(emailRepository.countByUserIdAndIsImportantTrue(1L)).thenReturn(2L);
        when(emailRepository.countByUserIdAndIsHiddenTrue(1L)).thenReturn(1L);
        when(emailRepository.countByUserIdAndIsDeletedTrue(1L)).thenReturn(0L);
        when(emailAnalysisRepository.countByUserId(1L)).thenReturn(5L);
        when(aiExplanationRepository.countByUserId(1L)).thenReturn(2L);
        when(reminderRepository.countByUserId(1L)).thenReturn(5L);
        when(reminderRepository.countByUserIdAndDoneFalse(1L)).thenReturn(2L);
        when(auditLogRepository.countByActorId(1L)).thenReturn(20L);
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(1L)).thenReturn(List.of());
        when(emailRepository.findOldestByUserId(1L)).thenReturn(LocalDateTime.now().minusMonths(2));
        when(emailAnalysisRepository.findOldestByUserId(1L)).thenReturn(LocalDateTime.now().minusMonths(1));
        when(aiExplanationRepository.findOldestByUserId(1L)).thenReturn(null);
        when(reminderRepository.findOldestByUserId(1L)).thenReturn(null);

        UserUsageResponse usage = service.getUserUsage(1L);

        assertThat(usage.user().id()).isEqualTo(1L);
        assertThat(usage.analysis().used()).isEqualTo(5);
        assertThat(usage.analysis().limit()).isEqualTo(20);
        assertThat(usage.analysis().expired()).isFalse();
        assertThat(usage.reminders().active()).isEqualTo(2);
        assertThat(usage.emails().total()).isEqualTo(10);
        assertThat(usage.sessions().active()).isEqualTo(0);
        assertThat(usage.dataInventory().emails()).isEqualTo(10);
        assertThat(usage.dataInventory().analyses()).isEqualTo(5);
        assertThat(usage.dataInventory().aiExplanations()).isEqualTo(2);
    }

    @Test
    void getUserUsage_premiumUserHasNullLimit() {
        user.setRole(Role.PREMIUM);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(emailRepository.countByUserId(1L)).thenReturn(0L);
        when(emailRepository.countByUserIdAndIsImportantTrue(1L)).thenReturn(0L);
        when(emailRepository.countByUserIdAndIsHiddenTrue(1L)).thenReturn(0L);
        when(emailRepository.countByUserIdAndIsDeletedTrue(1L)).thenReturn(0L);
        when(emailAnalysisRepository.countByUserId(1L)).thenReturn(0L);
        when(aiExplanationRepository.countByUserId(1L)).thenReturn(0L);
        when(reminderRepository.countByUserId(1L)).thenReturn(0L);
        when(reminderRepository.countByUserIdAndDoneFalse(1L)).thenReturn(0L);
        when(auditLogRepository.countByActorId(1L)).thenReturn(0L);
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(1L)).thenReturn(List.of());
        when(emailRepository.findOldestByUserId(1L)).thenReturn(null);
        when(emailAnalysisRepository.findOldestByUserId(1L)).thenReturn(null);
        when(aiExplanationRepository.findOldestByUserId(1L)).thenReturn(null);
        when(reminderRepository.findOldestByUserId(1L)).thenReturn(null);

        UserUsageResponse usage = service.getUserUsage(1L);

        assertThat(usage.analysis().limit()).isNull();
        assertThat(usage.analysis().expired()).isFalse();
    }

    @Test
    void listActiveSessions_marksCurrentSession() {
        RefreshToken current = RefreshToken.builder()
                .id(1L).familyId("fam-1").userAgent("Chrome").ip("1.1.1.1")
                .issuedAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now())
                .build();
        RefreshToken other = RefreshToken.builder()
                .id(2L).familyId("fam-2").userAgent("Firefox").ip("2.2.2.2")
                .issuedAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now())
                .build();

        when(refreshTokenService.listActiveSessions(1L)).thenReturn(List.of(current, other));
        when(refreshTokenService.hashToken("current-token")).thenReturn(new byte[32]);
        when(refreshTokenService.findByTokenHash(any())).thenReturn(Optional.of(current));

        List<ActiveSessionResponse> sessions = service.listActiveSessions(1L, "current-token");

        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0).current()).isTrue();
        assertThat(sessions.get(1).current()).isFalse();
    }

    @Test
    void listActiveSessions_nullTokenMarksNothing() {
        RefreshToken rt = RefreshToken.builder()
                .id(1L).familyId("fam-1").userAgent("Chrome").ip("1.1.1.1")
                .issuedAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now())
                .build();
        when(refreshTokenService.listActiveSessions(1L)).thenReturn(List.of(rt));

        List<ActiveSessionResponse> sessions = service.listActiveSessions(1L, null);

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).current()).isFalse();
    }

    @Test
    void revokeSession_revokesFamilyAndPublishesAudit() {
        RefreshToken current = RefreshToken.builder()
                .id(1L).familyId("fam-1").userAgent("Chrome").ip("1.1.1.1")
                .issuedAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now())
                .build();
        RefreshToken target = RefreshToken.builder()
                .id(2L).familyId("fam-2").userAgent("Firefox").ip("2.2.2.2")
                .issuedAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenService.listActiveSessions(1L)).thenReturn(List.of(current, target));
        when(refreshTokenService.hashToken("current-token")).thenReturn(new byte[32]);
        when(refreshTokenService.findByTokenHash(any())).thenReturn(Optional.of(current));
        when(refreshTokenService.revokeFamily("fam-2", 1L)).thenReturn(2);

        service.revokeSession(1L, 2L, "current-token");

        verify(refreshTokenService).revokeFamily("fam-2", 1L);
        verify(auditEventPublisher).publishSessionRevoked(eq(1L), eq("u@example.com"), eq("fam-2"), eq("Firefox"));
    }

    @Test
    void revokeSession_cannotRevokeCurrentSession() {
        RefreshToken current = RefreshToken.builder()
                .id(1L).familyId("fam-1").userAgent("Chrome").ip("1.1.1.1")
                .issuedAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenService.listActiveSessions(1L)).thenReturn(List.of(current));
        when(refreshTokenService.hashToken("current-token")).thenReturn(new byte[32]);
        when(refreshTokenService.findByTokenHash(any())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.revokeSession(1L, 1L, "current-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No puedes revocar tu sesión actual");
    }
}
