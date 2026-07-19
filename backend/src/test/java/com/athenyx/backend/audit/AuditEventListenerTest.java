package com.athenyx.backend.audit;

import com.athenyx.backend.audit.event.*;
import com.athenyx.backend.entity.*;
import com.athenyx.backend.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditContext auditContext;
    @Mock
    private ApplicationContext applicationContext;

    private AuditEventListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 13, 12, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        listener = new AuditEventListener(auditLogRepository, auditContext, objectMapper, clock);
        listener.setApplicationContext(applicationContext);
        when(applicationContext.getBean(AuditEventListener.class)).thenReturn(listener);
        when(auditContext.ipAddress()).thenReturn("192.168.1.1");
        when(auditContext.userAgent()).thenReturn("TestBrowser/1.0");
        when(auditContext.correlationId()).thenReturn("corr-123");
    }

    @Test
    void loginSuccess_savesLogWithCorrectFields() {
        LoginSuccessEvent event = new LoginSuccessEvent(this, user(1L, "u@test.com", Role.ADMIN));

        listener.onLoginSuccess(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(AuditActionType.LOGIN);
        assertThat(saved.getActorEmail()).isEqualTo("u@test.com");
        assertThat(saved.getActorRole()).isEqualTo("ADMIN");
        assertThat(saved.getSeverity()).isEqualTo(AuditSeverity.INFO);
        assertThat(saved.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-123");
    }

    @Test
    void loginFailed_savesLogWithAnonymousActor() {
        LoginFailedEvent event = new LoginFailedEvent(this, null, "Connection refused");

        listener.onLoginFailed(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(AuditActionType.LOGIN_FAILED);
        assertThat(saved.getActorEmail()).isEqualTo("anonymous");
        assertThat(saved.getSeverity()).isEqualTo(AuditSeverity.WARNING);
        assertThat(saved.getResult()).isEqualTo(AuditResult.FAILURE);
    }

    @Test
    void phishingDetected_savesLogWithCriticalSeverity() {
        PhishingDetectedEvent event = new PhishingDetectedEvent(
                this, 1L, "u@test.com", 10L, "attacker@evil.com", 95, "RED");

        listener.onPhishingDetected(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActionType()).isEqualTo(AuditActionType.PHISHING_DETECTED);
        assertThat(saved.getSeverity()).isEqualTo(AuditSeverity.CRITICAL);
        assertThat(saved.getTargetId()).isEqualTo("10");
        assertThat(saved.getResult()).isEqualTo(AuditResult.SUCCESS);
    }

    @Test
    void persistFailure_doesNotPropagate() {
        LoginSuccessEvent event = new LoginSuccessEvent(this, user(1L, "u@test.com", Role.ADMIN));
        doThrow(new RuntimeException("DB error")).when(auditLogRepository).save(any());

        listener.onLoginSuccess(event);

        verify(auditLogRepository).save(any());
    }

    private User user(Long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        return u;
    }
}
