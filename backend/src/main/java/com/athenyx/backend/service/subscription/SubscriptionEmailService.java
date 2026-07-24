package com.athenyx.backend.service.subscription;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.entity.Payment;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.gmail.GmailService;
import com.athenyx.backend.gmail.templates.SubscriptionTemplates;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEmailService {

    private final GmailService gmailService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AuditEventPublisher auditEventPublisher;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void sendWelcomeEmail(Long userId, Long paymentId) {
        scheduleAfterCommit(userId, paymentId, "welcome");
    }

    public void sendCancellationEmail(Long userId, Long paymentId) {
        scheduleAfterCommit(userId, paymentId, "cancellation");
    }

    private void scheduleAfterCommit(Long userId, Long paymentId, String template) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doSend(userId, paymentId, template);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doSend(userId, paymentId, template);
            }
        });
    }

    private void doSend(Long userId, Long paymentId, String template) {
        try {
            Payment payment = paymentRepository.findById(paymentId).orElse(null);
            User user = userRepository.findById(userId).orElse(null);
            if (payment == null || user == null) {
                log.warn("Cannot send {} email: payment={}, user={}", template, paymentId, userId);
                return;
            }

            String to = user.getEmail();
            String name = user.getName() != null ? user.getName().split(" ")[0] : "Usuario";
            String plan = payment.getBillingCycle().name().equals("ANNUAL") ? "Premium Anual" : "Premium Mensual";
            String price = payment.getCurrency() + " " + payment.getAmount().toString().replace(".", ",");
            String renewsAt = payment.getExpiresAt() != null
                    ? payment.getExpiresAt().format(DATE_FORMAT) : "N/A";

            String subject;
            String plain;
            String html;

            if ("welcome".equals(template)) {
                subject = SubscriptionTemplates.welcomeSubject();
                plain = SubscriptionTemplates.welcomePlain(name, plan, price, renewsAt);
                html = SubscriptionTemplates.welcomeHtml(name, plan, price, renewsAt);
            } else {
                String canceledAt = payment.getCancelRequestedAt() != null
                        ? payment.getCancelRequestedAt().format(DATE_FORMAT)
                        : (payment.getCanceledAt() != null
                                ? payment.getCanceledAt().format(DATE_FORMAT)
                                : "hoy");
                String effectiveUntil = payment.getExpiresAt() != null
                        ? payment.getExpiresAt().format(DATE_FORMAT) : "N/A";
                subject = SubscriptionTemplates.cancelSubject();
                plain = SubscriptionTemplates.cancelPlain(name, canceledAt, effectiveUntil);
                html = SubscriptionTemplates.cancelHtml(name, canceledAt, effectiveUntil);
            }

            gmailService.sendEmail(userId, to, subject, plain, html);

            auditEventPublisher.publishSubscriptionEmailSent(
                    userId, user.getEmail(), paymentId, template, to);

            log.info("Sent {} email to {}", template, to);

        } catch (Exception e) {
            log.error("Failed to send {} email for user {} payment {}: {}",
                    template, userId, paymentId, e.getMessage());
        }
    }
}
