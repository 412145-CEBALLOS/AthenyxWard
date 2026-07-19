package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a user deletes an email.
 */
@Getter
public class EmailDeletedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Long emailId;

    public EmailDeletedEvent(Object source, Long actorId, String actorEmail, Long emailId) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.emailId = emailId;
    }
}
