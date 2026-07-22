package com.athenyx.backend.payment;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MpApiClientTest {

    private MercadoPagoProperties properties;
    private ConfigService configService;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private MpApiClient client;

    @BeforeEach
    void setUp() {
        properties = mock(MercadoPagoProperties.class);
        configService = mock(ConfigService.class);
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        client = new MpApiClient(properties, configService, restTemplate, objectMapper);
    }

    @Test
    void fetchPayment_bodyWithExternalReference_returnsSummaryWithExternalRef() {
        when(configService.getString(ConfigKey.MERCADOPAGO_API_BASE_URL)).thenReturn("https://api.mercadopago.com");
        String body = "{\"id\":1349126481,\"status\":\"approved\",\"external_reference\":\"order_6_1784669031790\"}";
        ResponseEntity<String> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(response);

        MpPaymentSummary summary = client.fetchPayment(1349126481L);

        assertThat(summary.id()).isEqualTo(1349126481L);
        assertThat(summary.status()).isEqualTo("approved");
        assertThat(summary.externalReference()).isEqualTo("order_6_1784669031790");
    }

    @Test
    void fetchPayment_bodyWithoutExternalReference_returnsSummaryWithNull() {
        when(configService.getString(ConfigKey.MERCADOPAGO_API_BASE_URL)).thenReturn("https://api.mercadopago.com");
        String body = "{\"id\":1349126481,\"status\":\"pending\"}";
        ResponseEntity<String> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(response);

        MpPaymentSummary summary = client.fetchPayment(1349126481L);

        assertThat(summary.id()).isEqualTo(1349126481L);
        assertThat(summary.status()).isEqualTo("pending");
        assertThat(summary.externalReference()).isNull();
    }

    @Test
    void fetchPayment_bodyWithMaskedCardNumber_ignoresUnknownFields() {
        when(configService.getString(ConfigKey.MERCADOPAGO_API_BASE_URL)).thenReturn("https://api.mercadopago.com");
        String body = "{\"id\":1349126481,\"status\":\"approved\",\"external_reference\":\"order_6_1784669031790\","
                + "\"card\":{\"first_six_digits\":\"400005\",\"last_four_digits\":\"0006\"}}";
        ResponseEntity<String> response = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(response);

        MpPaymentSummary summary = client.fetchPayment(1349126481L);

        assertThat(summary.id()).isEqualTo(1349126481L);
        assertThat(summary.status()).isEqualTo("approved");
        assertThat(summary.externalReference()).isEqualTo("order_6_1784669031790");
    }

    @Test
    void fetchMerchantOrder_returns404_throwsMpApiExceptionWithStatusCode() {
        when(configService.getString(ConfigKey.MERCADOPAGO_API_BASE_URL)).thenReturn("https://api.mercadopago.com");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, "{\"message\":\"Entity not found\"}".getBytes(), null));

        assertThatThrownBy(() -> client.fetchMerchantOrder(42916831939L))
                .isInstanceOf(MpApiException.class)
                .extracting(e -> ((MpApiException) e).getStatusCode())
                .isEqualTo(404);
    }

    @Test
    void fetchPayment_returns404_throwsMpApiExceptionWithStatusCode() {
        when(configService.getString(ConfigKey.MERCADOPAGO_API_BASE_URL)).thenReturn("https://api.mercadopago.com");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, "{\"message\":\"Payment not found\"}".getBytes(), null));

        assertThatThrownBy(() -> client.fetchPayment(9999999L))
                .isInstanceOf(MpApiException.class)
                .extracting(e -> ((MpApiException) e).getStatusCode())
                .isEqualTo(404);
    }
}
