package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a user revokes a specific active session (family of refresh tokens).
 */
@Getter
public class SessionRevokedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String familyId;
    private final String userAgent;

    public SessionRevokedEvent(Object source, Long actorId, String actorEmail,
                               String familyId, String userAgent) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.familyId = familyId;
        this.userAgent = userAgent;
    }
}
