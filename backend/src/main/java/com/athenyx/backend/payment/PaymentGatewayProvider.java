package com.athenyx.backend.payment;

import com.athenyx.backend.entity.PaymentProvider;
import java.math.BigDecimal;

public interface PaymentGatewayProvider {
    PaymentProvider getName();
    CheckoutSession createCheckout(String orderRef, BigDecimal amount, String currency);
    PaymentResult capture(String providerRef, String token);
    void refund(String providerRef);
}
