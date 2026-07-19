package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserDeletedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String targetEmail;

    public UserDeletedEvent(Object source, Long actorId, String actorEmail, String targetEmail) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.targetEmail = targetEmail;
    }
}
