package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ConfigPurgeEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String targetType;
    private final String configKey;
    private final long purgedCount;
    private final Long skippedDueToReminders;

    public ConfigPurgeEvent(Object source, Long actorId, String actorEmail,
                             String targetType, String configKey,
                             long purgedCount, Long skippedDueToReminders) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.targetType = targetType;
        this.configKey = configKey;
        this.purgedCount = purgedCount;
        this.skippedDueToReminders = skippedDueToReminders;
    }
}
