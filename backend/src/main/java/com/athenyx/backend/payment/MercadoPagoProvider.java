package com.athenyx.backend.payment;

import com.athenyx.backend.entity.PaymentProvider;
import com.athenyx.backend.exception.MercadoPagoApiException;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePaymentMethodsRequest;
import com.mercadopago.client.preference.PreferencePaymentTypeRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.preference.Preference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class MercadoPagoProvider implements PaymentGatewayProvider {

    private final MercadoPagoProperties properties;

    @Override
    public PaymentProvider getName() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    public CheckoutSession createCheckout(String orderRef, BigDecimal amount, String currency) {
        if (!properties.isConfigured()) {
            return createStubSession(orderRef);
        }

        try {
            MercadoPagoConfig.setAccessToken(properties.getAccessToken());

            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title("Suscripcion Premium AthenyxWard")
                    .quantity(1)
                    .unitPrice(amount)
                    .currencyId(currency)
                    .build();

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(properties.getBackUrlSuccessBackend())
                    .failure(properties.getBackUrlFailure())
                    .pending(properties.getBackUrlPending())
                    .build();

            PreferencePaymentMethodsRequest paymentMethods = PreferencePaymentMethodsRequest.builder()
                    .excludedPaymentTypes(List.of(
                            PreferencePaymentTypeRequest.builder().id("ticket").build(),
                            PreferencePaymentTypeRequest.builder().id("bank_transfer").build(),
                            PreferencePaymentTypeRequest.builder().id("atm").build()
                    ))
                    .installments(12)
                    .build();

            PreferenceRequest.PreferenceRequestBuilder builder = PreferenceRequest.builder()
                    .items(List.of(item))
                    .backUrls(backUrls)
                    .autoReturn("approved")
                    .paymentMethods(paymentMethods)
                    .externalReference(orderRef);

            String notificationUrl = properties.getNotificationUrl();
            if (notificationUrl != null && !notificationUrl.isBlank()) {
                builder.notificationUrl(notificationUrl);
            }

            PreferenceRequest request = builder.build();

            log.info("[MercadoPago] Creating preference: orderRef={} amount={} currency={} siteId=MLA sandbox={} notificationUrl=[{}]",
                    orderRef, amount, currency, properties.isSandbox(),
                    notificationUrl != null ? notificationUrl : "not set");

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(request);

            String initPoint = preference.getInitPoint();
            if (properties.isSandbox()) {
                String sandboxPoint = preference.getSandboxInitPoint();
                if (sandboxPoint != null && !sandboxPoint.isBlank()) {
                    initPoint = sandboxPoint;
                }
            }

            log.info("[MercadoPago] Created preference id={} initPoint={}", preference.getId(), initPoint);

            return new CheckoutSession(
                    preference.getId(),
                    orderRef,
                    initPoint,
                    LocalDateTime.now().plusMinutes(15)
            );

        } catch (MPApiException apiEx) {
            int code = apiEx.getStatusCode();
            String body = apiEx.getApiResponse() != null ? apiEx.getApiResponse().getContent() : "N/A";
            log.error("[MercadoPago] API error status={} body={}", code, body);
            throw new MercadoPagoApiException(code, body);
        } catch (Exception e) {
            log.error("[MercadoPago] createCheckout failed for orderRef={}: {}", orderRef, e.getMessage(), e);
            throw new MercadoPagoApiException(0, e.getMessage());
        }
    }

    private CheckoutSession createStubSession(String orderRef) {
        String ref = "mp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[MercadoPago Stub] createCheckout orderRef={}", orderRef);
        return new CheckoutSession(ref, orderRef, "/checkout/mp-stub", LocalDateTime.now().plusMinutes(15));
    }

    @Override
    public PaymentResult capture(String providerRef, String token) {
        if (!properties.isConfigured()) {
            return captureStub(providerRef, token);
        }

        try {
            MercadoPagoConfig.setAccessToken(properties.getAccessToken());

            com.mercadopago.client.payment.PaymentClient client =
                    new com.mercadopago.client.payment.PaymentClient();

            Long paymentId = parsePaymentId(providerRef);
            com.mercadopago.resources.payment.Payment mpPayment = client.get(paymentId);

            String mpStatus = mpPayment.getStatus();
            MPStatus status = MPStatus.fromMpString(mpStatus);

            log.info("[MercadoPago] capture providerRef={} mpStatus={}", providerRef, mpStatus);

            if (status == MPStatus.APPROVED) {
                return new PaymentResult(true, providerRef, null);
            } else {
                return new PaymentResult(false, providerRef, "mp_" + mpStatus);
            }

        } catch (Exception e) {
            log.error("[MercadoPago] capture failed providerRef={}: {}", providerRef, e.getMessage(), e);
            return new PaymentResult(false, providerRef, "mp_error: " + e.getMessage());
        }
    }

    private Long parsePaymentId(String providerRef) {
        try {
            return Long.parseLong(providerRef);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid MP payment ID: " + providerRef);
        }
    }

    private PaymentResult captureStub(String providerRef, String token) {
        log.info("[MercadoPago Stub] capture providerRef={} token={}", providerRef, token != null ? "[present]" : "[absent]");
        if ("FAIL".equals(token)) {
            return new PaymentResult(false, providerRef, "user_simulated_failure");
        }
        try {
            Thread.sleep(1000 + (long) (Math.random() * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new PaymentResult(true, providerRef, null);
    }

    @Override
    public void refund(String providerRef) {
        if (!properties.isConfigured()) {
            log.info("[MercadoPago Stub] refund providerRef={}", providerRef);
            return;
        }

        try {
            MercadoPagoConfig.setAccessToken(properties.getAccessToken());

            com.mercadopago.client.payment.PaymentClient client =
                    new com.mercadopago.client.payment.PaymentClient();
            Long paymentId = parsePaymentId(providerRef);
            client.refund(paymentId);

            log.info("[MercadoPago] Refund processed paymentId={}", paymentId);
        } catch (Exception e) {
            log.error("[MercadoPago] refund failed providerRef={}: {}", providerRef, e.getMessage(), e);
        }
    }
}
