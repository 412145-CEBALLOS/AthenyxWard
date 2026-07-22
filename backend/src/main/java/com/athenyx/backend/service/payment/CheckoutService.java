package com.athenyx.backend.service.payment;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.dto.*;
import com.athenyx.backend.entity.*;
import com.athenyx.backend.exception.*;
import com.athenyx.backend.payment.CheckoutSession;
import com.athenyx.backend.payment.MPStatus;
import com.athenyx.backend.payment.PaymentResult;
import com.athenyx.backend.payment.PaymentGatewayProvider;
import com.athenyx.backend.payment.PaymentProviderRegistry;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.service.subscription.SubscriptionEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CheckoutService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentProviderRegistry registry;
    private final ConfigService configService;
    private final AuditEventPublisher auditEventPublisher;
    private final SubscriptionEmailService emailService;

    public CheckoutStatusResponse getStatus(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CheckoutNotFoundException(paymentId));
        if (!payment.getUser().getId().equals(userId)) {
            throw new CheckoutNotFoundException(paymentId);
        }
        return toCheckoutStatus(payment);
    }

    public CheckoutStatusResponse getStatusByClaim(Long paymentId, String claimToken) {
        Payment payment = paymentRepository.findByClaimToken(claimToken)
                .orElseThrow(() -> new CheckoutNotFoundException(paymentId));
        if (!payment.getId().equals(paymentId)) {
            throw new CheckoutNotFoundException(paymentId);
        }
        return toCheckoutStatus(payment);
    }

    public UserInfo confirmPaymentByClaim(String claimToken) {
        Payment payment = paymentRepository.findByClaimToken(claimToken)
                .orElseThrow(() -> new RuntimeException("Token de verificación inválido"));
        return doConfirmPayment(payment, "");
    }

    @Transactional
    public CreateCheckoutResponse createCheckout(Long userId, CreateCheckoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getRole() == Role.PREMIUM) {
            throw new CheckoutAlreadyPremiumException();
        }

        String providerName = request.provider().toUpperCase();
        List<String> enabled = parseCsv(configService.getString(ConfigKey.PAYMENT_ENABLED_PROVIDERS));
        if (!enabled.contains(providerName)) {
            throw new CheckoutInvalidProviderException(request.provider());
        }

        PaymentGatewayProvider provider = registry.get(providerName)
                .orElseThrow(() -> new CheckoutInvalidProviderException(request.provider()));

        BigDecimal price = getPrice(request.billingCycle());
        String orderRef = "order_" + userId + "_" + System.currentTimeMillis();

        CheckoutSession session = provider.createCheckout(orderRef, price, configService.getString(ConfigKey.SUBSCRIPTION_CURRENCY));

        Payment payment = Payment.builder()
                .user(user)
                .planTier(request.planTier() != null ? request.planTier() : "PREMIUM")
                .billingCycle(request.billingCycle())
                .status(PaymentStatus.PENDING)
                .amount(price)
                .currency(configService.getString(ConfigKey.SUBSCRIPTION_CURRENCY))
                .provider(PaymentProvider.valueOf(providerName))
                .providerRef(session.providerRef())
                .externalReference(session.externalReference())
                .expiresAt(session.expiresAt())
                .claimToken(UUID.randomUUID().toString())
                .build();

        Payment saved = paymentRepository.save(payment);

        auditEventPublisher.publishPaymentInitiated(
                userId, user.getEmail(), saved.getId(),
                providerName, price.toString(), payment.getCurrency());

        return new CreateCheckoutResponse(saved.getId(), session.redirectUrl(), session.expiresAt(), saved.getClaimToken());
    }

    @Transactional
    public UserInfo confirmPayment(Long userId, ConfirmPaymentRequest request) {
        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new CheckoutNotFoundException(request.paymentId()));

        if (!payment.getUser().getId().equals(userId)) {
            throw new CheckoutNotFoundException(request.paymentId());
        }

        return doConfirmPayment(payment, request.token());
    }

    private UserInfo doConfirmPayment(Payment payment, String token) {
        switch (payment.getStatus()) {
            case COMPLETED -> throw new CheckoutAlreadyCompletedException();
            case FAILED, CANCELED, REFUNDED, EXPIRED -> throw new CheckoutNotPendingException();
            case PENDING -> {} // fall through to capture
        }

        if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CheckoutPaymentExpiredException();
        }

        Long userId = payment.getUser().getId();

        PaymentGatewayProvider provider = registry.get(payment.getProvider().name())
                .orElseThrow(() -> new RuntimeException("Provider no encontrado"));

        PaymentResult result = provider.capture(payment.getProviderRef(), token);

        if (!result.success()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            paymentRepository.save(payment);

            auditEventPublisher.publishPaymentFailed(
                    userId, payment.getUser().getEmail(), payment.getId(),
                    payment.getProvider().name(), payment.getAmount().toString(),
                    payment.getCurrency(), result.failureReason());

            throw new CheckoutPaymentFailedException(result.failureReason());
        }

        return markPaymentCompleted(payment, userId);
    }

    @Transactional
    public void processMPResult(Long userId, Long paymentId, MPStatus mpStatus) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CheckoutNotFoundException(paymentId));

        if (!payment.getUser().getId().equals(userId)) {
            throw new CheckoutNotFoundException(paymentId);
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED && mpStatus == MPStatus.APPROVED) {
            log.info("[CheckoutService] Payment {} already completed, skipping MP result", paymentId);
            return;
        }

        if (mpStatus == MPStatus.APPROVED) {
            markPaymentCompleted(payment, userId);
        } else if (mpStatus == MPStatus.REFUNDED) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            auditEventPublisher.publishPaymentFailed(
                    userId, payment.getUser().getEmail(), payment.getId(),
                    payment.getProvider().name(), payment.getAmount().toString(),
                    payment.getCurrency(), "mp_refunded");
        } else if (mpStatus == MPStatus.REJECTED) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("mp_rejected");
            paymentRepository.save(payment);
            auditEventPublisher.publishPaymentFailed(
                    userId, payment.getUser().getEmail(), payment.getId(),
                    payment.getProvider().name(), payment.getAmount().toString(),
                    payment.getCurrency(), "mp_rejected");
        } else {
            log.info("[CheckoutService] Payment {} MP status={}, no action taken", paymentId, mpStatus);
        }
    }

    private UserInfo markPaymentCompleted(Payment payment, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int durationDays = payment.getBillingCycle() == BillingCycle.MONTHLY ? 30 : 365;

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(now);
        payment.setExpiresAt(now.plusDays(durationDays));
        paymentRepository.save(payment);

        User user = payment.getUser();
        Role oldRole = user.getRole();
        user.setRole(Role.PREMIUM);
        user.setAnalysisCount(0);
        userRepository.save(user);
        userRepository.incrementTokenVersion(userId);

        auditEventPublisher.publishPaymentCompleted(
                userId, user.getEmail(), payment.getId(),
                payment.getProvider().name(), payment.getAmount().toString(),
                payment.getCurrency());

        auditEventPublisher.publishRoleChanged(
                userId, user.getEmail(), user.getEmail(), oldRole.name(), Role.PREMIUM.name());

        emailService.sendWelcomeEmail(userId, payment.getId());

        return new UserInfo(
                user.getId(), user.getName(), user.getEmail(), user.getPictureUrl(),
                user.getRole(), user.getTrialEndDate(), user.isTrialExpired(),
                user.isAccessibilityMode(), user.getTermsAcceptedAt(), user.getTermsVersion(),
                user.getLastLoginAt(), user.getEmailVerified());
    }

    @Transactional
    public void cancelPending(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CheckoutNotFoundException(paymentId));

        if (!payment.getUser().getId().equals(userId)) {
            throw new CheckoutNotFoundException(paymentId);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        payment.setStatus(PaymentStatus.CANCELED);
        payment.setCanceledAt(LocalDateTime.now());
        payment.setFailureReason("user_canceled");
        paymentRepository.save(payment);

        auditEventPublisher.publishPaymentFailed(
                userId, payment.getUser().getEmail(), payment.getId(),
                payment.getProvider().name(), payment.getAmount().toString(),
                payment.getCurrency(), "user_canceled");
    }

    private BigDecimal getPrice(BillingCycle cycle) {
        String key = cycle == BillingCycle.MONTHLY
                ? ConfigKey.SUBSCRIPTION_PRICE_MONTHLY.name()
                : ConfigKey.SUBSCRIPTION_PRICE_ANNUAL.name();
        return new BigDecimal(configService.getRaw(ConfigKey.valueOf(key)));
    }

    private List<String> parseCsv(String csv) {
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .toList();
    }

    private CheckoutStatusResponse toCheckoutStatus(Payment payment) {
        return new CheckoutStatusResponse(
                payment.getId(),
                payment.getStatus().name(),
                payment.getProvider().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getBillingCycle().name(),
                payment.getCreatedAt(),
                payment.getExpiresAt()
        );
    }
}
