package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TrialResetEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String targetEmail;
    private final String previousTrialEndDate;

    public TrialResetEvent(Object source, Long actorId, String actorEmail,
                           String targetEmail, String previousTrialEndDate) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.targetEmail = targetEmail;
        this.previousTrialEndDate = previousTrialEndDate;
    }
}
