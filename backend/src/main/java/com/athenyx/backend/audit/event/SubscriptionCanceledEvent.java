package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionCanceledEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Long paymentId;
    private final String planTier;

    public SubscriptionCanceledEvent(Object source, Long actorId, String actorEmail,
                                    Long paymentId, String planTier) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.paymentId = paymentId;
        this.planTier = planTier;
    }
}
