package com.athenyx.backend.controller;

import com.athenyx.backend.entity.Payment;
import com.athenyx.backend.entity.PaymentProvider;
import com.athenyx.backend.entity.PaymentStatus;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.payment.MPStatus;
import com.athenyx.backend.payment.MercadoPagoProperties;
import com.athenyx.backend.payment.MpApiClient;
import com.athenyx.backend.payment.MpApiException;
import com.athenyx.backend.payment.MpMerchantOrderSummary;
import com.athenyx.backend.payment.MpPaymentSummary;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.service.payment.CheckoutService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MPWebhookControllerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MercadoPagoProperties properties;

    @Mock
    private CheckoutService checkoutService;

    @Mock
    private MpApiClient mpApiClient;

    private ObjectMapper objectMapper;

    @InjectMocks
    private MPWebhookController controller;

    private User user;
    private Payment entityPayment;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new MPWebhookController(paymentRepository, properties, checkoutService, mpApiClient, objectMapper);

        user = User.builder()
                .id(1L)
                .googleId("gid")
                .email("u@example.com")
                .build();

        entityPayment = Payment.builder()
                .id(100L)
                .user(user)
                .status(PaymentStatus.PENDING)
                .provider(PaymentProvider.MERCADOPAGO)
                .providerRef("pref_123")
                .amount(new BigDecimal("9.99"))
                .build();
    }

    @Test
    void handleWebhook_notConfigured_returns200() {
        when(properties.isConfigured()).thenReturn(false);

        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(checkoutService);
        verifyNoInteractions(mpApiClient);
    }

    @Test
    void handleWebhook_missingIdAllFormats_returns200() {
        when(properties.isConfigured()).thenReturn(true);

        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(checkoutService);
        verifyNoInteractions(mpApiClient);
    }

    @Test
    void handleWebhook_unknownType_returns200() {
        when(properties.isConfigured()).thenReturn(true);

        ResponseEntity<Void> result = controller.handleWebhook(null, "unknown", null, null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(checkoutService);
        verifyNoInteractions(mpApiClient);
    }

    @Test
    void handleWebhook_merchantOrderTopicWithoutId_returns200() {
        when(properties.isConfigured()).thenReturn(true);

        ResponseEntity<Void> result = controller.handleWebhook(null, null, "merchant_order", null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(mpApiClient);
    }

    @Test
    void handleWebhook_malformedJsonBody_returns200() {
        when(properties.isConfigured()).thenReturn(true);

        String body = "not valid json at all";

        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(checkoutService);
        verifyNoInteractions(mpApiClient);
    }

    @Test
    void handleWebhook_invalidDataIdFormat_returns200() {
        when(properties.isConfigured()).thenReturn(true);

        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"notanumber\"}}";

        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(checkoutService);
        verifyNoInteractions(mpApiClient);
    }

    @Test
    void handleWebhook_topicParamMerchantOrderWithId_callsMerchantOrderHandler() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchMerchantOrder(50001L))
                .thenReturn(new MpMerchantOrderSummary(50001L, List.of()));

        ResponseEntity<Void> result = controller.handleWebhook(null, null, "merchant_order", "50001", null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(mpApiClient).fetchMerchantOrder(50001L);
    }

    @Test
    void handleWebhook_jsonBodyPayment_happyPath() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchPayment(12345L))
                .thenReturn(new MpPaymentSummary(12345L, "approved", "pref_123"));
        when(paymentRepository.findByProviderAndExternalReference(PaymentProvider.MERCADOPAGO, "pref_123"))
                .thenReturn(Optional.of(entityPayment));

        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"12345\"}}";
        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(mpApiClient).fetchPayment(12345L);
        verify(checkoutService).processMPResult(1L, 100L, MPStatus.APPROVED);
    }

    @Test
    void handleWebhook_queryParamsDataId_legacyFormat() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchPayment(12345L))
                .thenReturn(new MpPaymentSummary(12345L, "approved", "pref_123"));
        when(paymentRepository.findByProviderAndExternalReference(PaymentProvider.MERCADOPAGO, "pref_123"))
                .thenReturn(Optional.of(entityPayment));

        ResponseEntity<Void> result = controller.handleWebhook("12345", "payment", null, null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(mpApiClient).fetchPayment(12345L);
        verify(checkoutService).processMPResult(1L, 100L, MPStatus.APPROVED);
    }

    @Test
    void handleWebhook_merchantOrder_processesAllPayments() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchMerchantOrder(50001L))
                .thenReturn(new MpMerchantOrderSummary(50001L, List.of(99999L)));
        when(mpApiClient.fetchPayment(99999L))
                .thenReturn(new MpPaymentSummary(99999L, "approved", "pref_123"));
        when(paymentRepository.findByProviderAndExternalReference(PaymentProvider.MERCADOPAGO, "pref_123"))
                .thenReturn(Optional.of(entityPayment));

        String body = "{\"type\":\"merchant_order\",\"data\":{\"id\":\"50001\"}}";
        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(mpApiClient).fetchMerchantOrder(50001L);
        verify(mpApiClient).fetchPayment(99999L);
        verify(checkoutService).processMPResult(1L, 100L, MPStatus.APPROVED);
    }

    @Test
    void handleWebhook_merchantOrderWithNoPayments_returns200NoProcess() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchMerchantOrder(50001L))
                .thenReturn(new MpMerchantOrderSummary(50001L, List.of()));

        String body = "{\"type\":\"merchant_order\",\"data\":{\"id\":\"50001\"}}";
        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(mpApiClient).fetchMerchantOrder(50001L);
        verifyNoInteractions(checkoutService);
    }

    @Test
    void handleWebhook_paymentNotFoundInDb_returns200() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchPayment(12345L))
                .thenReturn(new MpPaymentSummary(12345L, "approved", "unknown_ref"));
        when(paymentRepository.findByProviderAndExternalReference(PaymentProvider.MERCADOPAGO, "unknown_ref"))
                .thenReturn(Optional.empty());

        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"12345\"}}";
        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(checkoutService, never()).processMPResult(any(), any(), any());
    }

    @Test
    void handleWebhook_mpApiException_propagates() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchPayment(12345L))
                .thenThrow(new MpApiException("MP API error", 500, "Internal error"));

        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"12345\"}}";

        assertThatThrownBy(() -> controller.handleWebhook(null, null, null, null, body))
                .isInstanceOf(MpApiException.class)
                .hasMessageContaining("MP API error");
    }

    @Test
    void handleWebhook_merchantOrder_404_returns200WithoutPropagating() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchMerchantOrder(50001L))
                .thenThrow(new MpApiException("Not found", 404, "{\"message\":\"Entity not found\"}"));

        ResponseEntity<Void> result = controller.handleWebhook(null, null, "merchant_order", "50001", null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(checkoutService, never()).processMPResult(any(), any(), any());
    }

    @Test
    void handleWebhook_processesCorrectStatusMapping() {
        when(properties.isConfigured()).thenReturn(true);
        when(mpApiClient.fetchPayment(12345L))
                .thenReturn(new MpPaymentSummary(12345L, "pending", "pref_123"));
        when(paymentRepository.findByProviderAndExternalReference(PaymentProvider.MERCADOPAGO, "pref_123"))
                .thenReturn(Optional.of(entityPayment));

        String body = "{\"type\":\"payment\",\"data\":{\"id\":\"12345\"}}";
        ResponseEntity<Void> result = controller.handleWebhook(null, null, null, null, body);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(checkoutService).processMPResult(1L, 100L, MPStatus.PENDING);
    }
}
