package com.athenyx.backend.audit.event;

import com.athenyx.backend.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a user successfully completes the OAuth2 login flow.
 */
@Getter
public class LoginSuccessEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String actorRole;
    private final String correlationId;

    public LoginSuccessEvent(Object source, User user) {
        this(source, user, null);
    }

    public LoginSuccessEvent(Object source, User user, String correlationId) {
        super(source);
        this.actorId = user.getId();
        this.actorEmail = user.getEmail();
        this.actorRole = user.getRole().name();
        this.correlationId = correlationId;
    }
}
