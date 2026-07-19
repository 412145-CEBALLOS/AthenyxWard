package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ConfigUpdateEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final String configKey;
    private final String oldValue;
    private final String newValue;

    public ConfigUpdateEvent(Object source, Long actorId, String actorEmail,
                             String configKey, String oldValue, String newValue) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.configKey = configKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public double deltaPercent() {
        try {
            double oldV = Double.parseDouble(oldValue);
            double newV = Double.parseDouble(newValue);
            if (oldV == 0) return newV == 0 ? 0 : 100;
            return Math.abs((newV - oldV) / oldV * 100);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
