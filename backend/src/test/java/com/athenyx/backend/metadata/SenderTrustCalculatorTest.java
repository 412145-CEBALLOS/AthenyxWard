package com.athenyx.backend.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SenderTrustCalculatorTest {

    private final SenderTrustCalculator calculator = new SenderTrustCalculator();

    @Test
    void allPass_trusted() {
        var headers = new ExtractedHeaders(
            "sender@paypal.com", "paypal.com", "PayPal",
            "<paypal.com>", "paypal.com",
            null, null,
            java.util.List.of(),
            "Mon, 22 Jun 2026 10:00:00 +0000", "+0000",
            AuthStatus.PASS, AuthStatus.PASS, AuthStatus.PASS,
            null, null, null, false, MassMailingProvider.NONE
        );
        var validation = new SenderValidationResult(false, false, false);
        var timestamp = new TimestampAnalysisResult(false, false, false, 0);
        var massMailing = new MassMailingResult(false, MassMailingProvider.NONE, null);

        var result = calculator.calculate(headers, validation, timestamp, massMailing);
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.level()).isEqualTo(SenderTrustLevel.TRUSTED);
    }

    @Test
    void allFail_suspicious() {
        var headers = new ExtractedHeaders(
            "sender@gmail.com", "gmail.com", "PayPal Support",
            "<scammer.com>", "scammer.com",
            "<other.com>", "other.com",
            java.util.List.of(),
            "Mon, 22 Jun 2026 10:00:00 +0000", "INVALID_TZ",
            AuthStatus.FAIL, AuthStatus.FAIL, AuthStatus.FAIL,
            null, null, null, true, MassMailingProvider.MAILCHIMP
        );
        var validation = new SenderValidationResult(true, true, false);
        var timestamp = new TimestampAnalysisResult(true, false, true, 0);
        var massMailing = new MassMailingResult(true, MassMailingProvider.MAILCHIMP, "reason");

        var result = calculator.calculate(headers, validation, timestamp, massMailing);
        assertThat(result.score()).isLessThan(40);
        assertThat(result.level()).isEqualTo(SenderTrustLevel.SUSPICIOUS);
    }

    @ParameterizedTest
    @CsvSource({
        "PASS",
        "FAIL",
    })
    void spfScore(String spf) {
        var headers = new ExtractedHeaders(
            "sender@test.com", "test.com", "Test",
            null, null, null, null,
            java.util.List.of(),
            "Mon, 22 Jun 2026 10:00:00 +0000", "+0000",
            AuthStatus.fromString(spf), AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        var result = calculator.calculate(headers,
            new SenderValidationResult(false, false, false),
            new TimestampAnalysisResult(false, false, false, 0),
            new MassMailingResult(false, MassMailingProvider.NONE, null));
        assertThat(result.signals().stream()
            .anyMatch(s -> s.name().equals("SPF_PASS") || s.name().equals("SPF_FAIL"))).isTrue();
    }
}
