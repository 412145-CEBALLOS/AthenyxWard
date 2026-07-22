package com.athenyx.backend.audit.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentFailedEvent extends ApplicationEvent {

    private final Long actorId;
    private final String actorEmail;
    private final Long paymentId;
    private final String provider;
    private final String amount;
    private final String currency;
    private final String reason;

    public PaymentFailedEvent(Object source, Long actorId, String actorEmail,
                              Long paymentId, String provider, String amount,
                              String currency, String reason) {
        super(source);
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.paymentId = paymentId;
        this.provider = provider;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
    }
}
