package com.athenyx.backend.service;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.entity.RefreshToken;
import com.athenyx.backend.entity.RevokedReason;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.RefreshTokenRepository;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.security.RefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private RefreshTokenService service;

    private User user;

    @BeforeEach
    void setUp() throws Exception {
        service.setApplicationContext(applicationContext);
        lenient().when(applicationContext.getBean(RefreshTokenService.class)).thenReturn(service);

        user = User.builder()
                .id(1L)
                .googleId("gid")
                .email("test@example.com")
                .name("Tester")
                .role(Role.TRIAL)
                .build();

        ReflectionTestUtils.setField(service, "refreshExpirationMs", 2_592_000_000L);
        ReflectionTestUtils.setField(service, "refreshAbsoluteExpirationMs", 7_776_000_000L);

        lenient().when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.incrementTokenVersion(1L)).thenReturn(1);
        lenient().when(userRepository.findTokenVersionById(1L)).thenReturn(Optional.of(1L));
    }

    @Test
    void issue_storesHashNotRaw_andAssignsFamily() {
        RefreshTokenService.IssuedToken issued = service.issue(user, null, 0L);

        assertThat(issued.raw()).isNotBlank();
        assertThat(issued.row().getUser()).isSameAs(user);
        assertThat(issued.row().getFamilyId()).isNotBlank();
        assertThat(issued.row().getTokenHash()).isNotNull().hasSize(32);
        assertThat(issued.row().getRevokedAt()).isNull();
        assertThat(issued.row().getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(issued.row().getAbsoluteExpiresAt()).isAfter(issued.row().getExpiresAt());
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void rotate_happyPath_marksOldAsReplaced_andEmitsNewInSameFamily() {
        RefreshTokenService.IssuedToken first = service.issue(user, null, 0L);
        RefreshToken existing = first.row();
        existing.setId(10L);
        existing.setUser(user);

        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(existing));

        RefreshTokenService.IssuedToken rotated = service.rotate(first.raw(), user, null);

        assertThat(rotated.raw()).isNotBlank().isNotEqualTo(first.raw());
        assertThat(rotated.row().getFamilyId()).isEqualTo(existing.getFamilyId());
        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(existing.getRevokedReason()).isEqualTo(RevokedReason.REPLACED);
        assertThat(existing.getReplacedById()).isEqualTo(rotated.row().getId());
    }

    @Test
    void resolveUser_happyPath_returnsUser() {
        RefreshTokenService.IssuedToken first = service.issue(user, null, 0L);
        RefreshToken existing = first.row();
        existing.setId(10L);
        existing.setUser(user);

        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(existing));

        User resolved = service.resolveUserForRotation(first.raw());

        assertThat(resolved).isSameAs(user);
    }

    @Test
    void resolveUser_blank_throwsMissing() {
        assertThatThrownBy(() -> service.resolveUserForRotation(null))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.MISSING);
    }

    @Test
    void resolveUser_reuseDetected_revokesEntireFamily() {
        RefreshTokenService.IssuedToken first = service.issue(user, null, 0L);
        RefreshToken existing = first.row();
        existing.setId(10L);
        existing.setUser(user);
        existing.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        existing.setRevokedReason(RevokedReason.REPLACED);

        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resolveUserForRotation(first.raw()))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.REUSE_DETECTED);

        ArgumentCaptor<RevokedReason> reasonCaptor = ArgumentCaptor.forClass(RevokedReason.class);
        verify(repository).revokeFamily(eq(existing.getFamilyId()), reasonCaptor.capture(), any());
        assertThat(reasonCaptor.getValue()).isEqualTo(RevokedReason.REUSE_DETECTED);
    }

    @Test
    void resolveUser_expired_revokesThatToken_andThrows() {
        RefreshTokenService.IssuedToken first = service.issue(user, null, 0L);
        RefreshToken existing = first.row();
        existing.setId(10L);
        existing.setUser(user);
        existing.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        existing.setAbsoluteExpiresAt(LocalDateTime.now().plusDays(1));

        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resolveUserForRotation(first.raw()))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.EXPIRED);

        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(existing.getRevokedReason()).isEqualTo(RevokedReason.EXPIRED);
        verify(repository, never()).revokeFamily(anyString(), any(), any());
    }

    @Test
    void resolveUser_absoluteLifetimeExceeded_revokesFamily() {
        RefreshTokenService.IssuedToken first = service.issue(user, null, 0L);
        RefreshToken existing = first.row();
        existing.setId(10L);
        existing.setUser(user);
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));
        existing.setAbsoluteExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resolveUserForRotation(first.raw()))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.EXPIRED);

        verify(repository).revokeFamily(eq(existing.getFamilyId()), eq(RevokedReason.EXPIRED), any());
    }

    @Test
    void resolveUser_blankToken_throwsMissing() {
        assertThatThrownBy(() -> service.resolveUserForRotation(null))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.MISSING);

        assertThatThrownBy(() -> service.resolveUserForRotation("  "))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.MISSING);
    }

    @Test
    void resolveUser_unknownToken_throwsMissing() {
        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveUserForRotation("does-not-exist"))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.MISSING);
    }

    @Test
    void revoke_marksRowRevoked() {
        RefreshTokenService.IssuedToken first = service.issue(user, null, 0L);
        RefreshToken existing = first.row();
        existing.setId(10L);

        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(existing));

        service.revoke(first.raw());

        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(existing.getRevokedReason()).isEqualTo(RevokedReason.LOGOUT);
    }

    @Test
    void revoke_nullOrBlank_isNoOp() {
        service.revoke(null);
        service.revoke("   ");
        verify(repository, never()).findByTokenHash(any());
        verify(repository, never()).save(any());
    }

    @Test
    void revokeAllForUser_delegatesToRepository() {
        when(repository.revokeAllForUser(eq(1L), eq(RevokedReason.LOGOUT), any())).thenReturn(3);

        int n = service.revokeAllForUser(1L);

        assertThat(n).isEqualTo(3);
    }

    @Test
    void logTokenRefreshFailed_publishesEvent() {
        service.logTokenRefreshFailed(1L, "u@test.com", "EXPIRED");

        verify(auditEventPublisher).publishTokenRefreshFailed(1L, "u@test.com", "EXPIRED");
    }

    @Test
    void resolveUser_inactiveUser_throwsAccountDisabled() {
        user.setActive(false);
        RefreshTokenService.IssuedToken first = service.issue(user, null, 0L);
        RefreshToken existing = first.row();
        existing.setId(10L);
        existing.setUser(user);

        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resolveUserForRotation(first.raw()))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.ACCOUNT_DISABLED);
    }

    @Test
    void resolveUser_deletedUser_throwsAccountDisabled() {
        user.setDeletedAt(LocalDateTime.now());
        RefreshTokenService.IssuedToken first = service.issue(user, null, 0L);
        RefreshToken existing = first.row();
        existing.setId(10L);
        existing.setUser(user);

        when(repository.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resolveUserForRotation(first.raw()))
                .isInstanceOf(RefreshTokenException.class)
                .extracting("kind").isEqualTo(RefreshTokenException.Kind.ACCOUNT_DISABLED);
    }
}
