package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RoleChangedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String targetEmail;
    private final String oldRole;
    private final String newRole;

    public RoleChangedEvent(Object source, Long actorId, String actorEmail,
                            String targetEmail, String oldRole, String newRole) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.targetEmail = targetEmail;
        this.oldRole = oldRole;
        this.newRole = newRole;
    }
}
