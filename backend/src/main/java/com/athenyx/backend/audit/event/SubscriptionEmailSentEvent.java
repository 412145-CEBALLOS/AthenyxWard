package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionEmailSentEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Long paymentId;
    private final String template;
    private final String recipient;

    public SubscriptionEmailSentEvent(Object source, Long actorId, String actorEmail,
                                      Long paymentId, String template, String recipient) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.paymentId = paymentId;
        this.template = template;
        this.recipient = recipient;
    }
}
