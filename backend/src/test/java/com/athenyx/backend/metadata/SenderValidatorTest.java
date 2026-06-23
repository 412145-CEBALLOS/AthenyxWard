package com.athenyx.backend.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SenderValidatorTest {

    private final SenderValidator validator = new SenderValidator();

    @Test
    void noMismatch_returnsAllFalse() {
        var headers = new ExtractedHeaders(
            "sender@paypal.com", "paypal.com", "PayPal",
            "<paypal.com>", "paypal.com",
            null, null,
            java.util.List.of(), null, null,
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        var result = validator.validate(headers);
        assertThat(result.returnPathMismatch()).isFalse();
        assertThat(result.replyToMismatch()).isFalse();
    }

    @Test
    void returnPathMismatch_detected() {
        var headers = new ExtractedHeaders(
            "sender@paypal.com", "paypal.com", "PayPal",
            "<scammer@gmail.com>", "scammer@gmail.com",
            null, null,
            java.util.List.of(), null, null,
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        var result = validator.validate(headers);
        assertThat(result.returnPathMismatch()).isTrue();
    }

    @Test
    void replyToMismatch_detected() {
        var headers = new ExtractedHeaders(
            "sender@paypal.com", "paypal.com", "PayPal",
            null, null,
            "<attacker@gmail.com>", "attacker@gmail.com",
            java.util.List.of(), null, null,
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        var result = validator.validate(headers);
        assertThat(result.replyToMismatch()).isTrue();
    }

    @Test
    void nullReturnPath_noMismatch() {
        var headers = new ExtractedHeaders(
            "sender@test.com", "test.com", "Test",
            null, null, null, null,
            java.util.List.of(), null, null,
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        var result = validator.validate(headers);
        assertThat(result.returnPathMismatch()).isFalse();
    }
}
