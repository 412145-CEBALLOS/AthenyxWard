package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when an email is automatically analyzed by the system
 * (e.g. during first-login onboarding).
 *
 * <p>The trigger for this event is not yet implemented; the publisher
 * method is wired and ready for the US that implements auto-analysis.</p>
 */
@Getter
public class AutoAnalysisCompletedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Long emailId;
    private final String sender;
    private final int riskPercentage;
    private final String riskLevel;

    public AutoAnalysisCompletedEvent(Object source, Long actorId, String actorEmail,
                                      Long emailId, String sender, int riskPercentage, String riskLevel) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.emailId = emailId;
        this.sender = sender;
        this.riskPercentage = riskPercentage;
        this.riskLevel = riskLevel;
    }
}
