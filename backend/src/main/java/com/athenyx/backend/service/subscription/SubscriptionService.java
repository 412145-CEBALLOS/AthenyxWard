package com.athenyx.backend.service.subscription;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.dto.PaymentHistoryResponse;
import com.athenyx.backend.dto.PaymentResponse;
import com.athenyx.backend.dto.SubscriptionResponse;
import com.athenyx.backend.dto.UserInfo;
import com.athenyx.backend.entity.*;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SubscriptionService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ConfigService configService;
    private final AuditEventPublisher auditEventPublisher;
    private final SubscriptionEmailService emailService;

    public SubscriptionResponse getCurrent(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getRole() == Role.TRIAL) {
            int trialLimit = configService.getInt(ConfigKey.TRIAL_ANALYSIS_LIMIT);
            String enabledProviders = configService.getString(ConfigKey.PAYMENT_ENABLED_PROVIDERS);
            return new SubscriptionResponse(
                    "TRIAL",
                    user.isTrialExpired() ? "EXPIRED" : "ACTIVE",
                    user.getTrialEndDate(),
                    null,
                    null,
                    null,
                    false,
                    null,
                    BigDecimal.ZERO,
                    configService.getString(ConfigKey.SUBSCRIPTION_CURRENCY),
                    configService.getString(ConfigKey.SUBSCRIPTION_ANNUAL_SAVINGS_PERCENT),
                    enabledProviders
            );
        }

        Optional<Payment> latestCompleted = paymentRepository.findLatestCompletedByUserId(userId);
        if (latestCompleted.isEmpty()) {
            return noSubscription(user);
        }

        Payment payment = latestCompleted.get();
        boolean isActive = payment.getExpiresAt() != null && payment.getExpiresAt().isAfter(LocalDateTime.now());

        return new SubscriptionResponse(
                payment.getPlanTier(),
                isActive ? "ACTIVE" : "EXPIRED",
                payment.getCompletedAt(),
                payment.getExpiresAt(),
                payment.getCanceledAt(),
                payment.getProvider().name(),
                true,
                payment.getBillingCycle(),
                payment.getAmount(),
                payment.getCurrency(),
                configService.getString(ConfigKey.SUBSCRIPTION_ANNUAL_SAVINGS_PERCENT),
                configService.getString(ConfigKey.PAYMENT_ENABLED_PROVIDERS)
        );
    }

    @Transactional
    public UserInfo cancel(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getRole() != Role.PREMIUM) {
            throw new RuntimeException("Solo los usuarios Premium pueden cancelar");
        }

        Optional<Payment> latestCompleted = paymentRepository.findLatestCompletedByUserId(userId);
        if (latestCompleted.isEmpty()) {
            throw new RuntimeException("No se encontró una suscripción activa");
        }

        Payment payment = latestCompleted.get();
        payment.setCanceledAt(LocalDateTime.now());
        paymentRepository.save(payment);

        Role oldRole = user.getRole();
        user.setRole(Role.TRIAL);
        user.setTrialEndDate(LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
        user.setAnalysisCount(0);
        userRepository.save(user);
        userRepository.incrementTokenVersion(userId);

        auditEventPublisher.publishSubscriptionCanceled(
                userId, user.getEmail(), payment.getId(), payment.getPlanTier());

        auditEventPublisher.publishRoleChanged(
                userId, user.getEmail(), user.getEmail(), oldRole.name(), Role.TRIAL.name());

        emailService.sendCancellationEmail(userId, payment.getId());

        return new UserInfo(
                user.getId(), user.getName(), user.getEmail(), user.getPictureUrl(),
                user.getRole(), user.getTrialEndDate(), user.isTrialExpired(),
                user.isAccessibilityMode(), user.getTermsAcceptedAt(), user.getTermsVersion(),
                user.getLastLoginAt(), user.getEmailVerified());
    }

    public PaymentHistoryResponse getHistory(Long userId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        Page<Payment> result = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<PaymentResponse> items = result.getContent().stream()
                .map(this::toPaymentResponse)
                .toList();

        return new PaymentHistoryResponse(items, result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }

    private SubscriptionResponse noSubscription(User user) {
        return new SubscriptionResponse(
                "NONE", "NONE", null, null, null, null, false,
                null, BigDecimal.ZERO,
                configService.getString(ConfigKey.SUBSCRIPTION_CURRENCY),
                configService.getString(ConfigKey.SUBSCRIPTION_ANNUAL_SAVINGS_PERCENT),
                configService.getString(ConfigKey.PAYMENT_ENABLED_PROVIDERS)
        );
    }

    private PaymentResponse toPaymentResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getPlanTier(), p.getStatus(),
                p.getAmount(), p.getCurrency(), p.getProvider(),
                p.getProviderRef(), p.getBillingCycle(),
                p.getCreatedAt(), p.getCompletedAt(),
                p.getExpiresAt(), p.getCanceledAt(),
                p.getFailureReason()
        );
    }
}
