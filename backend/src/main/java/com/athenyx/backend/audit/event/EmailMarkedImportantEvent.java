package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a user marks an email as important / not important.
 */
@Getter
public class EmailMarkedImportantEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Long emailId;
    private final boolean nowImportant;

    public EmailMarkedImportantEvent(Object source, Long actorId, String actorEmail,
                                     Long emailId, boolean nowImportant) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.emailId = emailId;
        this.nowImportant = nowImportant;
    }
}
