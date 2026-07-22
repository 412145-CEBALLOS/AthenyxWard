package com.athenyx.backend.service.payment;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.dto.ConfirmPaymentRequest;
import com.athenyx.backend.dto.CreateCheckoutRequest;
import com.athenyx.backend.dto.CreateCheckoutResponse;
import com.athenyx.backend.dto.UserInfo;
import com.athenyx.backend.entity.*;
import com.athenyx.backend.exception.*;
import com.athenyx.backend.payment.CheckoutSession;
import com.athenyx.backend.payment.PaymentGatewayProvider;
import com.athenyx.backend.payment.PaymentProviderRegistry;
import com.athenyx.backend.payment.PaymentResult;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.service.subscription.SubscriptionEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentProviderRegistry registry;

    @Mock
    private ConfigService configService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private SubscriptionEmailService emailService;

    @InjectMocks
    private CheckoutService checkoutService;

    private User user;
    private Payment payment;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .googleId("gid")
                .email("u@example.com")
                .name("User")
                .role(Role.TRIAL)
                .accessibilityMode(true)
                .build();

        payment = Payment.builder()
                .id(100L)
                .user(user)
                .planTier("PREMIUM")
                .billingCycle(BillingCycle.MONTHLY)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("9.99"))
                .currency("ARS")
                .provider(PaymentProvider.MERCADOPAGO)
                .providerRef("pref_123")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void confirmPayment_alreadyCompleted_throwsAlreadyCompleted() {
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(100L, "token", null);

        assertThatThrownBy(() -> checkoutService.confirmPayment(1L, request))
                .isInstanceOf(CheckoutAlreadyCompletedException.class)
                .hasMessageContaining("ya fue completado");
    }

    @Test
    void confirmPayment_statusFailed_throwsNotPending() {
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(100L, "token", null);

        assertThatThrownBy(() -> checkoutService.confirmPayment(1L, request))
                .isInstanceOf(CheckoutNotPendingException.class)
                .hasMessageContaining("ya no está pendiente");
    }

    @Test
    void confirmPayment_statusCanceled_throwsNotPending() {
        payment.setStatus(PaymentStatus.CANCELED);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(100L, "token", null);

        assertThatThrownBy(() -> checkoutService.confirmPayment(1L, request))
                .isInstanceOf(CheckoutNotPendingException.class)
                .hasMessageContaining("ya no está pendiente");
    }

    @Test
    void confirmPayment_statusRefunded_throwsNotPending() {
        payment.setStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(100L, "token", null);

        assertThatThrownBy(() -> checkoutService.confirmPayment(1L, request))
                .isInstanceOf(CheckoutNotPendingException.class)
                .hasMessageContaining("ya no está pendiente");
    }

    @Test
    void confirmPayment_statusExpired_throwsNotPending() {
        payment.setStatus(PaymentStatus.EXPIRED);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(100L, "token", null);

        assertThatThrownBy(() -> checkoutService.confirmPayment(1L, request))
                .isInstanceOf(CheckoutNotPendingException.class)
                .hasMessageContaining("ya no está pendiente");
    }

    @Test
    void confirmPayment_pendingPayment_proceedsToCapture() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        PaymentGatewayProvider mockProvider = mock(PaymentGatewayProvider.class);
        PaymentResult successResult = new PaymentResult(true, "cap_123", null);
        when(mockProvider.capture("pref_123", "token")).thenReturn(successResult);
        when(registry.get("MERCADOPAGO")).thenReturn(Optional.of(mockProvider));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest(100L, "token", null);
        UserInfo result = checkoutService.confirmPayment(1L, request);

        assertThat(result.role()).isEqualTo(Role.PREMIUM);
        verify(mockProvider).capture("pref_123", "token");
        verify(userRepository).incrementTokenVersion(1L);
    }

    @Test
    void createCheckout_savesExternalReferenceInPayment() {
        when(configService.getString(ConfigKey.PAYMENT_ENABLED_PROVIDERS)).thenReturn("MERCADOPAGO");
        when(configService.getString(ConfigKey.SUBSCRIPTION_CURRENCY)).thenReturn("ARS");
        when(configService.getRaw(ConfigKey.SUBSCRIPTION_PRICE_MONTHLY)).thenReturn("9.99");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        PaymentGatewayProvider mockProvider = mock(PaymentGatewayProvider.class);
        CheckoutSession session = new CheckoutSession(
                "pref_123",
                "order_1_" + System.currentTimeMillis(),
                "https://example.com/checkout",
                LocalDateTime.now().plusMinutes(15)
        );
        when(mockProvider.createCheckout(any(), any(), any())).thenReturn(session);
        when(registry.get("MERCADOPAGO")).thenReturn(Optional.of(mockProvider));

        CreateCheckoutRequest request = new CreateCheckoutRequest("MERCADOPAGO", BillingCycle.MONTHLY, null);
        CreateCheckoutResponse response = checkoutService.createCheckout(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(100L);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getProviderRef()).isEqualTo("pref_123");
        assertThat(savedPayment.getExternalReference()).isEqualTo(session.externalReference());
    }
}
