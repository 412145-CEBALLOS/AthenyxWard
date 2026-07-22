package com.athenyx.backend.payment;

import com.athenyx.backend.entity.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
public class CardProvider implements PaymentGatewayProvider {

    @Override
    public PaymentProvider getName() {
        return PaymentProvider.CARD;
    }

    @Override
    public CheckoutSession createCheckout(String orderRef, BigDecimal amount, String currency) {
        String ref = "card_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[Card Stub] createCheckout orderRef={} amount={} {}", orderRef, amount, currency);
        return new CheckoutSession(ref, orderRef, "/plan/checkout/stub", LocalDateTime.now().plusMinutes(15));
    }

    @Override
    public PaymentResult capture(String providerRef, String token) {
        log.info("[Card Stub] capture providerRef={} token={}", providerRef, token != null ? "[present]" : "[absent]");
        if ("FAIL".equals(token)) {
            return new PaymentResult(false, providerRef, "user_simulated_failure");
        }
        if (token == null || token.isBlank()) {
            return new PaymentResult(false, providerRef, "token_required");
        }
        if (!luhnValid(token.replaceAll("\\s", ""))) {
            return new PaymentResult(false, providerRef, "invalid_card_number");
        }
        try {
            Thread.sleep(1200 + (long) (Math.random() * 800));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new PaymentResult(true, providerRef, null);
    }

    @Override
    public void refund(String providerRef) {
        log.info("[Card Stub] refund providerRef={}", providerRef);
    }

    private boolean luhnValid(String number) {
        if (number == null || number.isEmpty()) return false;
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            char c = number.charAt(i);
            if (!Character.isDigit(c)) return false;
            int digit = Character.getNumericValue(c);
            if (alternate) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
