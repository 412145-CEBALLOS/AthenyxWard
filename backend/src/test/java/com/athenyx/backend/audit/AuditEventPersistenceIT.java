package com.athenyx.backend.audit;

import com.athenyx.backend.audit.event.*;
import com.athenyx.backend.entity.AuditActionType;
import com.athenyx.backend.entity.AuditLog;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.AuditLogRepository;
import com.athenyx.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuditEventPersistenceIT {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionVerificationService verificationService;

    private User user;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        user = userRepository.findByGoogleId("test-gid")
                .orElseGet(() -> userRepository.save(User.builder()
                        .googleId("test-gid")
                        .email("audit-it@test.com")
                        .name("Audit IT")
                        .role(Role.ADMIN)
                        .build()));
    }

    @Test
    void loginSuccess_eventSavesLog() {
        publisher.publishEvent(new LoginSuccessEvent(this, user));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.LOGIN);
        assertThat(row.getActorEmail()).isEqualTo("audit-it@test.com");
        assertThat(row.getActorRole()).isEqualTo("ADMIN");
        assertThat(row.getSeverity()).isNotNull();
        assertThat(row.getResult()).isNotNull();
    }

    @Test
    void loginFailed_eventSavesLogWithAnonymousActor() {
        publisher.publishEvent(new LoginFailedEvent(this, "attacker@evil.com", "Access denied"));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.LOGIN_FAILED);
        assertThat(row.getActorEmail()).isEqualTo("attacker@evil.com");
        assertThat(row.getSeverity()).isEqualTo(com.athenyx.backend.entity.AuditSeverity.WARNING);
    }

    @Test
    void phishingDetected_eventSavesLogWithCriticalSeverity() {
        publisher.publishEvent(new PhishingDetectedEvent(
                this, user.getId(), user.getEmail(), 99L, "scam@evil.com", 95, "RED"));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.PHISHING_DETECTED);
        assertThat(row.getSeverity()).isEqualTo(com.athenyx.backend.entity.AuditSeverity.CRITICAL);
        assertThat(row.getTargetId()).isEqualTo("99");
    }

    @Test
    void emailMarkedImportant_eventSavesLog() {
        publisher.publishEvent(new EmailMarkedImportantEvent(
                this, user.getId(), user.getEmail(), 10L, true));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.EMAIL_MARKED_IMPORTANT);
        assertThat(row.getActorId()).isEqualTo(user.getId());
    }

    @Test
    void emailHidden_eventSavesLog() {
        publisher.publishEvent(new EmailHiddenEvent(
                this, user.getId(), user.getEmail(), 10L));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.EMAIL_HIDDEN);
    }

    @Test
    void emailUnhidden_eventSavesLog() {
        publisher.publishEvent(new EmailHiddenEvent(
                this, user.getId(), user.getEmail(), 10L, true));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.EMAIL_UNHIDDEN);
    }

    @Test
    void emailDeleted_eventSavesLog() {
        publisher.publishEvent(new EmailDeletedEvent(
                this, user.getId(), user.getEmail(), 10L));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.EMAIL_DELETED);
    }

    @Test
    void logout_eventSavesLog() {
        publisher.publishEvent(new LogoutEvent(this, user.getId(), user.getEmail()));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.LOGOUT);
        assertThat(row.getActorEmail()).isEqualTo("audit-it@test.com");
    }

    @Test
    void logoutWithRevokedCount_eventSavesLogWithPayload() {
        publisher.publishEvent(new LogoutEvent(this, user.getId(), user.getEmail(), 5));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.LOGOUT);
        assertThat(row.getPayload()).contains("revokedCount");
    }

    @Test
    void logoutAnonymous_eventSavesLogWithAnonymousEmail() {
        publisher.publishEvent(new LogoutEvent(this, null, "anonymous"));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.LOGOUT);
        assertThat(row.getActorEmail()).isEqualTo("anonymous");
    }

    @Test
    void tokenRefreshFailed_eventSavesLog() {
        publisher.publishEvent(new TokenRefreshFailedEvent(
                this, user.getId(), user.getEmail(), "EXPIRED"));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.TOKEN_REFRESH_FAILED);
        assertThat(row.getSeverity()).isEqualTo(com.athenyx.backend.entity.AuditSeverity.WARNING);
    }

    @Test
    void exportCsv_eventSavesLog() {
        publisher.publishEvent(new ExportCsvEvent(
                this, user.getId(), user.getEmail(), "from=2026-07-01 to=2026-07-13"));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getActionType()).isEqualTo(AuditActionType.EXPORT_CSV);
    }

    @Test
    void multipleEvents_allSaved() {
        publisher.publishEvent(new LoginSuccessEvent(this, user));
        publisher.publishEvent(new EmailMarkedImportantEvent(this, user.getId(), user.getEmail(), 10L, true));
        publisher.publishEvent(new EmailHiddenEvent(this, user.getId(), user.getEmail(), 10L));
        publisher.publishEvent(new LogoutEvent(this, user.getId(), user.getEmail()));

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(4);
    }

    @Test
    void persist_runsInOwnTransaction_evenWhenOuterTransactionRollsBack() {
        int before = auditLogRepository.findAll().size();

        assertThatThrownBy(() -> verificationService.persistInOuterTransactionThenThrow(
                AuditLog.builder()
                        .actionType(AuditActionType.LOGIN)
                        .actorEmail("requires-new@test.com")
                        .severity(com.athenyx.backend.entity.AuditSeverity.INFO)
                        .result(com.athenyx.backend.entity.AuditResult.SUCCESS)
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("outer tx failure");

        assertThat(auditLogRepository.findAll()).hasSize(before + 1);
        AuditLog saved = auditLogRepository.findAll().get(auditLogRepository.findAll().size() - 1);
        assertThat(saved.getActorEmail()).isEqualTo("requires-new@test.com");
        assertThat(saved.getActionType()).isEqualTo(AuditActionType.LOGIN);
    }

    @Service
    static class TransactionVerificationService {
        @Autowired
        private AuditEventListener auditEventListener;

        @Transactional
        public void persistInOuterTransactionThenThrow(AuditLog log) {
            auditEventListener.persist(log);
            throw new RuntimeException("outer tx failure");
        }
    }
}
