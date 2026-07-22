package com.athenyx.backend.service.subscription;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.entity.Payment;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpirationService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AuditEventPublisher auditEventPublisher;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void expireSubscriptions() {
        List<Payment> expired = paymentRepository.findExpiredSubscriptions(LocalDateTime.now());
        log.info("SubscriptionExpirationService: found {} expired subscriptions", expired.size());

        for (Payment payment : expired) {
            User user = payment.getUser();
            if (user.getRole() != Role.PREMIUM) {
                continue;
            }

            payment.setCanceledAt(LocalDateTime.now());
            payment.setFailureReason("expired");
            paymentRepository.save(payment);

            user.setRole(Role.TRIAL);
            user.setTrialEndDate(LocalDateTime.now().plusDays(30));
            userRepository.save(user);
            userRepository.incrementTokenVersion(user.getId());

            auditEventPublisher.publishSubscriptionExpired(
                    user.getId(), user.getEmail(), payment.getId(), payment.getPlanTier());

            auditEventPublisher.publishRoleChanged(
                    user.getId(), user.getEmail(), user.getEmail(),
                    Role.PREMIUM.name(), Role.TRIAL.name());

            log.info("Expired subscription for user {} (paymentId={})", user.getEmail(), payment.getId());
        }
    }
}
