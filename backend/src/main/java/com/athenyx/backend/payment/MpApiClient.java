package com.athenyx.backend.payment;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Slf4j
public class MpApiClient {

    private static final String PAYMENTS_PATH = "/v1/payments";
    private static final String MERCHANT_ORDERS_PATH = "/v1/merchant_orders";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final MercadoPagoProperties properties;
    private final ConfigService configService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private volatile String cachedBaseUrl;
    private volatile long baseUrlCachedAt;

    public MpApiClient(MercadoPagoProperties properties, ConfigService configService) {
        this.properties = properties;
        this.configService = configService;
        this.restTemplate = buildRestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    MpApiClient(MercadoPagoProperties properties, ConfigService configService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.configService = configService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private RestTemplate buildRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) rt.getRequestFactory())
                .setConnectTimeout(java.time.Duration.ofSeconds(10));
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) rt.getRequestFactory())
                .setReadTimeout(java.time.Duration.ofSeconds(10));
        return rt;
    }

    private String getBaseUrl() {
        long now = System.currentTimeMillis();
        if (cachedBaseUrl != null && now - baseUrlCachedAt < CACHE_TTL.toMillis()) {
            return cachedBaseUrl;
        }
        String url = configService.getString(ConfigKey.MERCADOPAGO_API_BASE_URL);
        cachedBaseUrl = url;
        baseUrlCachedAt = now;
        return url;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + properties.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public MpPaymentSummary fetchPayment(Long id) {
        String url = getBaseUrl() + PAYMENTS_PATH + "/" + id;
        long start = System.currentTimeMillis();
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            long duration = System.currentTimeMillis() - start;
            log.info("[MpApiClient] GET /v1/payments/{} durationMs={} statusCode={}",
                    id, duration, response.getStatusCode().value());
            log.info("[MpApiClient] Raw payment body: {}", response.getBody());

            MpPaymentSummary summary = objectMapper.readValue(response.getBody(), MpPaymentSummary.class);
            log.info("[MpApiClient] payment id={} status={} externalRef={}",
                    summary.id(), summary.status(), summary.externalReference());
            return summary;
        } catch (JsonProcessingException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[MpApiClient] GET /v1/payments/{} durationMs={} JSON parse error: {}", id, duration, e.getMessage());
            throw new MpApiException("MP API response parse error for payment " + id + ": " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[MpApiClient] GET /v1/payments/{} durationMs={} client error status={}",
                    id, duration, e.getStatusCode().value());
            throw new MpApiException("MP API client error: " + e.getMessage(),
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[MpApiClient] GET /v1/payments/{} durationMs={} server error status={}",
                    id, duration, e.getStatusCode().value());
            throw new MpApiException("MP API server error: " + e.getMessage(),
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[MpApiClient] GET /v1/payments/{} durationMs={} error: {}",
                    id, duration, e.getMessage());
            throw new MpApiException("MP API error fetching payment " + id + ": " + e.getMessage(), e);
        }
    }

    public MpMerchantOrderSummary fetchMerchantOrder(Long id) {
        String url = getBaseUrl() + MERCHANT_ORDERS_PATH + "/" + id;
        long start = System.currentTimeMillis();
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            long duration = System.currentTimeMillis() - start;
            log.info("[MpApiClient] GET /v1/merchant_orders/{} durationMs={} statusCode={}",
                    id, duration, response.getStatusCode().value());

            MpMerchantOrderSummary summary = objectMapper.readValue(response.getBody(), MpMerchantOrderSummary.class);
            log.info("[MpApiClient] merchant_order id={} payments_count={}",
                    summary.id(), summary.paymentIds().size());
            return summary;
        } catch (JsonProcessingException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[MpApiClient] GET /v1/merchant_orders/{} durationMs={} JSON parse error: {}", id, duration, e.getMessage());
            throw new MpApiException("MP API response parse error for merchant_order " + id + ": " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[MpApiClient] GET /v1/merchant_orders/{} durationMs={} client error status={}",
                    id, duration, e.getStatusCode().value());
            throw new MpApiException("MP API client error: " + e.getMessage(),
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[MpApiClient] GET /v1/merchant_orders/{} durationMs={} server error status={}",
                    id, duration, e.getStatusCode().value());
            throw new MpApiException("MP API server error: " + e.getMessage(),
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[MpApiClient] GET /v1/merchant_orders/{} durationMs={} error: {}",
                    id, duration, e.getMessage());
            throw new MpApiException("MP API error fetching merchant_order " + id + ": " + e.getMessage(), e);
        }
    }
}
