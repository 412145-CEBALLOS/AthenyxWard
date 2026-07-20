package com.athenyx.backend.audit;

import com.athenyx.backend.audit.event.*;
import com.athenyx.backend.entity.AuditActionType;
import com.athenyx.backend.entity.AuditLog;
import com.athenyx.backend.entity.AuditResult;
import com.athenyx.backend.entity.AuditSeverity;
import com.athenyx.backend.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;
    private final AuditContext auditContext;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    ApplicationContext applicationContext;

    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private AuditEventListener self() {
        return applicationContext.getBean(AuditEventListener.class);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLoginSuccess(LoginSuccessEvent event) {
        String corrId = event.getCorrelationId() != null
                ? event.getCorrelationId()
                : auditContext.correlationId();

        self().persist(builder(event.getSource())
                .actionType(AuditActionType.LOGIN)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .actorRole(event.getActorRole())
                .correlationId(corrId)
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of("timestamp", LocalDateTime.now(clock).toString())))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLoginFailed(LoginFailedEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.LOGIN_FAILED)
                .actorEmail(event.getAttemptedEmail() != null ? event.getAttemptedEmail() : "anonymous")
                .severity(AuditSeverity.WARNING)
                .result(AuditResult.FAILURE)
                .payload(json(Map.of("reason", event.getReason())))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLogout(LogoutEvent event) {
        var builder = builder(event.getSource())
                .actionType(AuditActionType.LOGOUT)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS);

        if (event.getRevokedCount() != null) {
            builder.payload(json(Map.of("revokedCount", event.getRevokedCount())));
        }

        self().persist(builder.build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTokenRefreshFailed(TokenRefreshFailedEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.TOKEN_REFRESH_FAILED)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail() != null ? event.getActorEmail() : "anonymous")
                .severity(AuditSeverity.WARNING)
                .result(AuditResult.FAILURE)
                .payload(json(Map.of("kind", event.getFailureKind())))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPhishingDetected(PhishingDetectedEvent event) {
        String ip = event.getIpAddress() != null ? event.getIpAddress() : auditContext.ipAddress();
        String ua = event.getUserAgent() != null ? event.getUserAgent() : auditContext.userAgent();
        String corrId = event.getCorrelationId() != null ? event.getCorrelationId() : auditContext.correlationId();

        self().persist(builder(event.getSource())
                .actionType(AuditActionType.PHISHING_DETECTED)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("EMAIL")
                .targetId(String.valueOf(event.getEmailId()))
                .ipAddress(ip)
                .userAgent(ua)
                .correlationId(corrId)
                .severity(AuditSeverity.CRITICAL)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of(
                        "emailId", event.getEmailId(),
                        "sender", event.getSender(),
                        "riskPercentage", event.getRiskPercentage(),
                        "riskLevel", event.getRiskLevel()
                )))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAutoAnalysis(AutoAnalysisCompletedEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.AUTO_ANALYSIS)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("EMAIL")
                .targetId(String.valueOf(event.getEmailId()))
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of(
                        "emailId", event.getEmailId(),
                        "sender", event.getSender(),
                        "riskPercentage", event.getRiskPercentage(),
                        "riskLevel", event.getRiskLevel()
                )))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailMarkedImportant(EmailMarkedImportantEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.EMAIL_MARKED_IMPORTANT)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("EMAIL")
                .targetId(String.valueOf(event.getEmailId()))
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of(
                        "emailId", event.getEmailId(),
                        "nowImportant", event.isNowImportant()
                )))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailHidden(EmailHiddenEvent event) {
        AuditActionType actionType = event.isUnhidden()
                ? AuditActionType.EMAIL_UNHIDDEN
                : AuditActionType.EMAIL_HIDDEN;
        self().persist(builder(event.getSource())
                .actionType(actionType)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("EMAIL")
                .targetId(String.valueOf(event.getEmailId()))
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS)
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailDeleted(EmailDeletedEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.EMAIL_DELETED)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("EMAIL")
                .targetId(String.valueOf(event.getEmailId()))
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS)
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExportCsv(ExportCsvEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.EXPORT_CSV)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of("filters", event.getFilters())))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoleChanged(RoleChangedEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.ROLE_CHANGE)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("USER")
                .targetId(event.getTargetEmail())
                .severity(AuditSeverity.WARNING)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of(
                        "targetEmail", event.getTargetEmail(),
                        "oldRole", event.getOldRole(),
                        "newRole", event.getNewRole()
                )))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserDeactivated(UserDeactivatedEvent event) {
        AuditActionType actionType = event.isReactivated()
                ? AuditActionType.USER_REACTIVATED
                : AuditActionType.USER_DEACTIVATED;
        AuditSeverity severity = event.isReactivated()
                ? AuditSeverity.INFO
                : AuditSeverity.WARNING;
        self().persist(builder(event.getSource())
                .actionType(actionType)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("USER")
                .targetId(event.getTargetEmail())
                .severity(severity)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of("targetEmail", event.getTargetEmail())))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserDeleted(UserDeletedEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.USER_DELETED)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("USER")
                .targetId(event.getTargetEmail())
                .severity(AuditSeverity.CRITICAL)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of("targetEmail", event.getTargetEmail())))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTrialReset(TrialResetEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.TRIAL_RESET)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("USER")
                .targetId(event.getTargetEmail())
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of(
                        "targetEmail", event.getTargetEmail(),
                        "previousTrialEndDate", event.getPreviousTrialEndDate() != null ? event.getPreviousTrialEndDate() : "null"
                )))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigUpdate(ConfigUpdateEvent event) {
        double deltaPct = event.deltaPercent();
        AuditSeverity severity = deltaPct > 50 ? AuditSeverity.WARNING : AuditSeverity.INFO;
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.CONFIG_UPDATE)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType("CONFIG")
                .targetId(event.getConfigKey())
                .severity(severity)
                .result(AuditResult.SUCCESS)
                .payload(json(Map.of(
                        "key", event.getConfigKey(),
                        "oldValue", event.getOldValue() != null ? event.getOldValue() : "",
                        "newValue", event.getNewValue(),
                        "deltaPercent", String.format("%.1f", deltaPct)
                )))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfigPurge(ConfigPurgeEvent event) {
        AuditSeverity severity = "EMAIL".equals(event.getTargetType())
                ? AuditSeverity.CRITICAL
                : AuditSeverity.WARNING;
        var payload = new java.util.HashMap<String, Object>();
        payload.put("key", event.getConfigKey());
        payload.put("purgedCount", event.getPurgedCount());
        if (event.getSkippedDueToReminders() != null) {
            payload.put("skippedDueToReminders", event.getSkippedDueToReminders());
        }
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.CONFIG_PURGE)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .targetType(event.getTargetType())
                .targetId(event.getConfigKey())
                .severity(severity)
                .result(AuditResult.SUCCESS)
                .payload(json(payload))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSessionRevoked(SessionRevokedEvent event) {
        self().persist(builder(event.getSource())
                .actionType(AuditActionType.SESSION_REVOKED)
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .severity(AuditSeverity.INFO)
                .result(AuditResult.SUCCESS)
                .payload(json(java.util.Map.of(
                        "familyId", event.getFamilyId() != null ? event.getFamilyId() : "",
                        "userAgent", event.getUserAgent() != null ? event.getUserAgent() : ""
                )))
                .build());
    }

    private AuditLog.AuditLogBuilder builder(Object source) {
        return AuditLog.builder()
                .createdAt(LocalDateTime.now(clock))
                .ipAddress(auditContext.ipAddress())
                .userAgent(auditContext.userAgent())
                .correlationId(auditContext.correlationId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.error("audit.persist failed: {}", ex.getMessage());
        }
    }

    private String json(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
