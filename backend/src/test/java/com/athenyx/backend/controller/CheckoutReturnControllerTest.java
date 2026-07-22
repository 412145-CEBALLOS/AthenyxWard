package com.athenyx.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutReturnControllerTest {

    private CheckoutReturnController controller;

    @BeforeEach
    void setUp() {
        controller = new CheckoutReturnController();
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:4200");
    }

    @Test
    void handleReturn_returnsHtmlContentType() {
        ResponseEntity<String> result = controller.handleReturn("123", "approved", "order_6_1234567890");
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
    }

    @Test
    void handleReturn_containsPostMessageCall() {
        ResponseEntity<String> result = controller.handleReturn("123", "approved", "order_6_1234567890");
        assertThat(result.getBody()).contains("window.opener.postMessage");
    }

    @Test
    void handleReturn_messageHasCorrectPaymentData() {
        ResponseEntity<String> result = controller.handleReturn("999", "approved", "order_42_9876543210");
        assertThat(result.getBody()).contains("paymentId: '999'");
        assertThat(result.getBody()).contains("status: 'approved'");
        assertThat(result.getBody()).contains("externalRef: 'order_42_9876543210'");
    }

    @Test
    void handleReturn_messageIncludesTypeField() {
        ResponseEntity<String> result = controller.handleReturn("1", "pending", "order_1_ts");
        assertThat(result.getBody()).contains("type: 'mp-return'");
    }

    @Test
    void handleReturn_messageIncludesOriginReference() {
        ResponseEntity<String> result = controller.handleReturn("1", "approved", "order_1_ts");
        assertThat(result.getBody()).contains("origin: FRONTEND_ORIGIN");
    }

    @Test
    void handleReturn_includesFallbackRedirect() {
        ResponseEntity<String> result = controller.handleReturn("74", "approved", "order_6_123");
        assertThat(result.getBody()).contains("window.location.href = FALLBACK");
    }

    @Test
    void handleReturn_handlesMissingParams() {
        ResponseEntity<String> result = controller.handleReturn(null, null, null);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(result.getBody()).contains("window.opener.postMessage");
    }

    @Test
    void handleReturn_escapesQuotesInExternalReference_quotesAreBackslashEscaped() {
        ResponseEntity<String> result = controller.handleReturn("1", "ok", "order_6_\"alert('xss')\"");
        assertThat(result.getBody()).contains("\\\"");
        assertThat(result.getBody()).doesNotContain("\"alert('xss')\"");
    }

    @Test
    void handleReturn_escapesQuotesInPaymentId_quotesAreBackslashEscaped() {
        ResponseEntity<String> result = controller.handleReturn("123\"; evil(); //", "ok", "order_6_123");
        assertThat(result.getBody()).contains("\\\"");
        assertThat(result.getBody()).doesNotContain("\"; evil(); //");
    }

    @Test
    void handleReturn_escapesBackslashes_backslashIsDoubled() {
        ResponseEntity<String> result = controller.handleReturn("1", "ok", "order_6_\\test");
        assertThat(result.getBody()).contains("\\\\");
    }

    @Test
    void handleReturn_fallbackUrlContainsCorrectParams() {
        ResponseEntity<String> result = controller.handleReturn("55", "approved", "order_7_xyz");
        assertThat(result.getBody()).contains("payment_id=55");
        assertThat(result.getBody()).contains("status=approved");
        assertThat(result.getBody()).contains("external_reference=order_7_xyz");
    }

    @Test
    void handleReturn_frontendOriginVariableDeclared() {
        ResponseEntity<String> result = controller.handleReturn("1", "approved", "order_1_t");
        assertThat(result.getBody()).contains("var FRONTEND_ORIGIN");
    }
}
