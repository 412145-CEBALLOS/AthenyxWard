package com.athenyx.backend.controller;

import com.athenyx.backend.entity.Payment;
import com.athenyx.backend.entity.PaymentProvider;
import com.athenyx.backend.payment.MPStatus;
import com.athenyx.backend.payment.MercadoPagoProperties;
import com.athenyx.backend.payment.MpApiClient;
import com.athenyx.backend.payment.MpApiException;
import com.athenyx.backend.payment.MpMerchantOrderSummary;
import com.athenyx.backend.payment.MpPaymentSummary;
import com.athenyx.backend.repository.PaymentRepository;
import com.athenyx.backend.service.payment.CheckoutService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/webhooks/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MPWebhookController {

    private final PaymentRepository paymentRepository;
    private final MercadoPagoProperties properties;
    private final CheckoutService checkoutService;
    private final MpApiClient mpApiClient;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestParam(value = "data_id", required = false) String dataId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "id", required = false) String id,
            @RequestBody(required = false) String rawBody) {

        log.info("[MP Webhook] Received data_id={} type={} topic={} id={}", dataId, type, topic, id);

        if (!properties.isConfigured()) {
            log.warn("[MP Webhook] MP not configured, ignoring webhook");
            return ResponseEntity.ok().build();
        }

        String resolvedType = type;
        String resolvedId = dataId;

        if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rawBody);
                if (root.has("type")) {
                    resolvedType = root.get("type").asText();
                }
                if (root.has("data") && root.get("data").has("id")) {
                    resolvedId = root.get("data").get("id").asText();
                }
                log.info("[MP Webhook] Parsed from body: type={} id={}", resolvedType, resolvedId);
            } catch (Exception e) {
                log.warn("[MP Webhook] Failed to parse JSON body: {}", e.getMessage());
            }
        }

        if ("merchant_order".equals(resolvedType) || "merchant_order".equals(topic)) {
            String orderIdStr = resolvedId != null ? resolvedId : id;
            if (orderIdStr == null || orderIdStr.isBlank()) {
                log.warn("[MP Webhook] Missing order ID for merchant_order notification, ignoring");
                return ResponseEntity.ok().build();
            }
            try {
                handleMerchantOrder(Long.parseLong(orderIdStr));
            } catch (NumberFormatException e) {
                log.warn("[MP Webhook] Invalid merchant_order id format: {}", orderIdStr);
            }
            return ResponseEntity.ok().build();
        }

        if ("payment".equals(resolvedType)) {
            if (resolvedId == null || resolvedId.isBlank()) {
                log.warn("[MP Webhook] Missing data_id for payment notification, ignoring");
                return ResponseEntity.ok().build();
            }
            try {
                handlePayment(Long.parseLong(resolvedId));
            } catch (NumberFormatException e) {
                log.warn("[MP Webhook] Invalid payment id format: {}", resolvedId);
            }
            return ResponseEntity.ok().build();
        }

        log.warn("[MP Webhook] Unknown notification type={} topic={}, ignoring", resolvedType, topic);
        return ResponseEntity.ok().build();
    }

    private void handlePayment(Long mpPaymentId) {
        try {
            MpPaymentSummary mpPayment = mpApiClient.fetchPayment(mpPaymentId);

            log.info("[MP Webhook] payment_id={} external_ref={} status={}",
                    mpPayment.id(), mpPayment.externalReference(), mpPayment.status());

            if (mpPayment.externalReference() != null && !mpPayment.externalReference().isBlank()) {
                processPaymentByExternalRef(mpPayment.externalReference(), mpPayment.status());
            }
        } catch (MpApiException e) {
            log.error("[MP Webhook] MP API error fetching payment {}: {}", mpPaymentId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[MP Webhook] Unexpected error fetching payment {}: {}", mpPaymentId, e.getMessage(), e);
        }
    }

    private void handleMerchantOrder(Long mpOrderId) {
        try {
            MpMerchantOrderSummary order = mpApiClient.fetchMerchantOrder(mpOrderId);

            log.info("[MP Webhook] merchant_order_id={} payments_count={}",
                    mpOrderId, order.paymentIds().size());

            if (order.paymentIds().isEmpty()) {
                log.info("[MP Webhook] No payments in merchant_order {}, nothing to process", mpOrderId);
                return;
            }

            for (Long paymentId : order.paymentIds()) {
                log.info("[MP Webhook] Processing embedded payment id={} from merchant_order {}", paymentId, mpOrderId);
                handlePayment(paymentId);
            }

        } catch (MpApiException e) {
            if (e.getStatusCode() != null && e.getStatusCode() == 404) {
                log.warn("[MP Webhook] merchant_order {} not found via API (will rely on payment webhook)", mpOrderId);
                return;
            }
            log.error("[MP Webhook] MP API error fetching merchant_order {}: {}", mpOrderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[MP Webhook] Unexpected error fetching merchant_order {}: {}", mpOrderId, e.getMessage(), e);
        }
    }

    private void processPaymentByExternalRef(String externalRef, String mpStatus) {
        Optional<Payment> optPayment = paymentRepository.findByProviderAndExternalReference(
                PaymentProvider.MERCADOPAGO, externalRef);

        if (optPayment.isEmpty()) {
            log.warn("[MP Webhook] Payment not found for external_ref={}", externalRef);
            return;
        }

        Payment payment = optPayment.get();
        Long userId = payment.getUser().getId();
        MPStatus status = MPStatus.fromMpString(mpStatus);

        log.info("[MP Webhook] Processing payment {} userId={} mpStatus={}", payment.getId(), userId, mpStatus);
        checkoutService.processMPResult(userId, payment.getId(), status);
    }
}
