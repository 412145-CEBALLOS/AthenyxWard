package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when an OAuth2 login attempt fails.
 */
@Getter
public class LoginFailedEvent extends ApplicationEvent {

    private final String attemptedEmail;
    private final String reason;

    public LoginFailedEvent(Object source, String attemptedEmail, String reason) {
        super(source);
        this.attemptedEmail = attemptedEmail;
        this.reason = reason;
    }
}
