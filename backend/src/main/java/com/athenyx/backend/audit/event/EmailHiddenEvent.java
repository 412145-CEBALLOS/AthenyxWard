package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a user hides or unhides an email.
 */
@Getter
public class EmailHiddenEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Long emailId;
    private final boolean unhidden;

    public EmailHiddenEvent(Object source, Long actorId, String actorEmail, Long emailId) {
        this(source, actorId, actorEmail, emailId, false);
    }

    public EmailHiddenEvent(Object source, Long actorId, String actorEmail, Long emailId, boolean unhidden) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.emailId = emailId;
        this.unhidden = unhidden;
    }
}
