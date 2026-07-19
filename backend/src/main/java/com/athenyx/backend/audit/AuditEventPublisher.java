package com.athenyx.backend.audit;

import com.athenyx.backend.audit.event.*;
import com.athenyx.backend.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Central entry point for publishing audit events.
 *
 * <p>All audited actions flow through this component.  Callers never publish
 * {@code ApplicationEvent} directly — they call the typed helper method that
 * corresponds to their action.</p>
 *
 * <p>The actual persistence is performed asynchronously by
 * {@link AuditEventListener} inside a {@code AFTER_COMMIT} transaction
 * listener, so a failure in the audit database never propagates to the
 * caller's transaction.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishLoginSuccess(User user) {
        eventPublisher.publishEvent(new LoginSuccessEvent(this, user));
    }

    public void publishLoginSuccess(User user, String correlationId) {
        eventPublisher.publishEvent(new LoginSuccessEvent(this, user, correlationId));
    }

    public void publishLoginFailed(String attemptedEmail, String reason) {
        eventPublisher.publishEvent(new LoginFailedEvent(this, attemptedEmail, reason));
    }

    public void publishLogout(Long actorId, String actorEmail) {
        eventPublisher.publishEvent(new LogoutEvent(this, actorId, actorEmail));
    }

    public void publishLogout(Long actorId, String actorEmail, Integer revokedCount) {
        eventPublisher.publishEvent(new LogoutEvent(this, actorId, actorEmail, revokedCount));
    }

    public void publishTokenRefreshFailed(Long actorId, String actorEmail, String failureKind) {
        eventPublisher.publishEvent(new TokenRefreshFailedEvent(this, actorId, actorEmail, failureKind));
    }

    public void publishPhishingDetected(Long actorId, String actorEmail,
                                        Long emailId, String sender,
                                        int riskPercentage, String riskLevel) {
        eventPublisher.publishEvent(new PhishingDetectedEvent(
                this, actorId, actorEmail, emailId, sender, riskPercentage, riskLevel));
    }

    public void publishPhishingDetected(Long actorId, String actorEmail,
                                        Long emailId, String sender,
                                        int riskPercentage, String riskLevel,
                                        String ipAddress, String userAgent, String correlationId) {
        eventPublisher.publishEvent(new PhishingDetectedEvent(
                this, actorId, actorEmail, emailId, sender, riskPercentage, riskLevel,
                ipAddress, userAgent, correlationId));
    }

    public void publishAutoAnalysis(Long actorId, String actorEmail,
                                    Long emailId, String sender,
                                    int riskPercentage, String riskLevel) {
        eventPublisher.publishEvent(new AutoAnalysisCompletedEvent(
                this, actorId, actorEmail, emailId, sender, riskPercentage, riskLevel));
    }

    public void publishEmailMarkedImportant(Long actorId, String actorEmail,
                                            Long emailId, boolean nowImportant) {
        eventPublisher.publishEvent(new EmailMarkedImportantEvent(
                this, actorId, actorEmail, emailId, nowImportant));
    }

    public void publishEmailHidden(Long actorId, String actorEmail, Long emailId) {
        eventPublisher.publishEvent(new EmailHiddenEvent(this, actorId, actorEmail, emailId));
    }

    public void publishEmailUnhidden(Long actorId, String actorEmail, Long emailId) {
        eventPublisher.publishEvent(new EmailHiddenEvent(this, actorId, actorEmail, emailId, true));
    }

    public void publishEmailDeleted(Long actorId, String actorEmail, Long emailId) {
        eventPublisher.publishEvent(new EmailDeletedEvent(this, actorId, actorEmail, emailId));
    }

    public void publishExportCsv(Long actorId, String actorEmail, String filters) {
        eventPublisher.publishEvent(new ExportCsvEvent(this, actorId, actorEmail, filters));
    }

    // TODO US 4.3 (user management)
    public void publishRoleChanged(Long actorId, String actorEmail, String targetEmail, String oldRole, String newRole) {
        eventPublisher.publishEvent(new RoleChangedEvent(this, actorId, actorEmail, targetEmail, oldRole, newRole));
    }

    // TODO US 4.3 (user management)
    public void publishUserDeactivated(Long actorId, String actorEmail, String targetEmail, boolean reactivated) {
        eventPublisher.publishEvent(new UserDeactivatedEvent(this, actorId, actorEmail, targetEmail, reactivated));
    }

    public void publishUserDeleted(Long actorId, String actorEmail, String targetEmail) {
        eventPublisher.publishEvent(new UserDeletedEvent(this, actorId, actorEmail, targetEmail));
    }

    public void publishTrialReset(Long actorId, String actorEmail, String targetEmail, String previousTrialEndDate) {
        eventPublisher.publishEvent(new TrialResetEvent(this, actorId, actorEmail, targetEmail, previousTrialEndDate));
    }

    public void publishConfigUpdate(Long actorId, String actorEmail, String configKey, String oldValue, String newValue) {
        eventPublisher.publishEvent(new ConfigUpdateEvent(this, actorId, actorEmail, configKey, oldValue, newValue));
    }

    public void publishConfigPurge(Long actorId, String actorEmail, String targetType,
                                    String configKey, long purgedCount, Long skippedDueToReminders) {
        eventPublisher.publishEvent(new ConfigPurgeEvent(
                this, actorId, actorEmail, targetType, configKey, purgedCount, skippedDueToReminders));
    }
}
