package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a user explicitly logs out.
 */
@Getter
public class LogoutEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Integer revokedCount;

    public LogoutEvent(Object source, Long actorId, String actorEmail) {
        this(source, actorId, actorEmail, null);
    }

    public LogoutEvent(Object source, Long actorId, String actorEmail, Integer revokedCount) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.revokedCount = revokedCount;
    }
}
