package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a JWT refresh token rotation fails.
 */
@Getter
public class TokenRefreshFailedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String failureKind;

    public TokenRefreshFailedEvent(Object source, Long actorId, String actorEmail, String failureKind) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.failureKind = failureKind;
    }
}
