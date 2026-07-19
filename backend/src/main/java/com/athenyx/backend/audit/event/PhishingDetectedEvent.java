package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when {@code HeuristicAnalysisService.analyze} finishes with
 * {@code threatLevel == ThreatLevel.RED}.
 *
 * <p>This event is <strong>passive testimony only</strong>: it records the
 * detection for audit purposes and does <strong>not</strong> mutate any entity
 * (no {@code Email.sender} change, no blacklist, no notification).</p>
 *
 * <p>The {@code ipAddress}, {@code userAgent} and {@code correlationId} fields
 * are optional and carry the request context when the analysis was triggered
 * via the REST API.  They are used by the listener when
 * {@code AuditContext} cannot resolve them (e.g. the service runs
 * {@code @Async}).</p>
 */
@Getter
public class PhishingDetectedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Long emailId;
    private final String sender;
    private final int riskPercentage;
    private final String riskLevel;
    private final String ipAddress;
    private final String userAgent;
    private final String correlationId;

    public PhishingDetectedEvent(Object source, Long actorId, String actorEmail,
                                 Long emailId, String sender, int riskPercentage, String riskLevel) {
        this(source, actorId, actorEmail, emailId, sender, riskPercentage, riskLevel,
             null, null, null);
    }

    public PhishingDetectedEvent(Object source, Long actorId, String actorEmail,
                                 Long emailId, String sender, int riskPercentage, String riskLevel,
                                 String ipAddress, String userAgent, String correlationId) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.emailId = emailId;
        this.sender = sender;
        this.riskPercentage = riskPercentage;
        this.riskLevel = riskLevel;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
    }
}
