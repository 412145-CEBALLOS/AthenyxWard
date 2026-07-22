package com.athenyx.backend.payment;

import com.athenyx.backend.entity.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
public class PayPalProvider implements PaymentGatewayProvider {

    @Override
    public PaymentProvider getName() {
        return PaymentProvider.PAYPAL;
    }

    @Override
    public CheckoutSession createCheckout(String orderRef, BigDecimal amount, String currency) {
        String ref = "pp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[PayPal Stub] createCheckout orderRef={} amount={} {}", orderRef, amount, currency);
        return new CheckoutSession(ref, orderRef, "/plan/checkout/stub", LocalDateTime.now().plusMinutes(15));
    }

    @Override
    public PaymentResult capture(String providerRef, String token) {
        log.info("[PayPal Stub] capture providerRef={} token={}", providerRef, token != null ? "[present]" : "[absent]");
        if ("FAIL".equals(token)) {
            return new PaymentResult(false, providerRef, "user_simulated_failure");
        }
        try {
            Thread.sleep(800 + (long) (Math.random() * 1200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new PaymentResult(true, providerRef, null);
    }

    @Override
    public void refund(String providerRef) {
        log.info("[PayPal Stub] refund providerRef={}", providerRef);
    }
}
