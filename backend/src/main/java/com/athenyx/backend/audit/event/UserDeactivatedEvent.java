package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserDeactivatedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String targetEmail;
    private final boolean reactivated;

    public UserDeactivatedEvent(Object source, Long actorId, String actorEmail,
                                String targetEmail, boolean reactivated) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.targetEmail = targetEmail;
        this.reactivated = reactivated;
    }
}
