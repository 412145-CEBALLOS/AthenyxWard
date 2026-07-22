package com.athenyx.backend.service.payment;

import com.athenyx.backend.entity.Payment;
import com.athenyx.backend.entity.PaymentStatus;
import com.athenyx.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutExpirationService {

    private static final int STALE_MINUTES = 30;

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void expireStaleCheckouts() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(STALE_MINUTES);
        List<Payment> stale = paymentRepository.findByStatusAndCreatedAtBefore(
            PaymentStatus.PENDING, cutoff);
        for (Payment p : stale) {
            p.setStatus(PaymentStatus.EXPIRED);
            p.setFailureReason("abandoned_timeout");
            paymentRepository.save(p);
            log.info("[Checkout] Expired stale payment id={} createdAt={}", p.getId(), p.getCreatedAt());
        }
        if (!stale.isEmpty()) {
            log.info("[Checkout] Expired {} stale payment(s)", stale.size());
        }
    }
}
