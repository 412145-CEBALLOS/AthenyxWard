package com.athenyx.backend.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationResultsParserTest {

    private final AuthenticationResultsParser parser = new AuthenticationResultsParser();

    @Test
    void parse_fullAuthResults() {
        String header = "mx.google; dkim=pass header.i=@paypal.com header.s=selector1 header.b=abc123; spf=pass (google.com) smtp.mailfrom=paypal.com; dmarc=pass (p=reject dis=n) header.from=paypal.com";
        var result = parser.parse(header);
        assertThat(result.spf()).isEqualTo(AuthStatus.PASS);
        assertThat(result.dkim()).isEqualTo(AuthStatus.PASS);
        assertThat(result.dmarc()).isEqualTo(AuthStatus.PASS);
    }

    @Test
    void parse_spfFail() {
        var result = parser.parse("some.server; spf=fail smtp.mailfrom=fake.com");
        assertThat(result.spf()).isEqualTo(AuthStatus.FAIL);
    }

    @Test
    void parse_dkimNone() {
        var result = parser.parse("mx.google; dkim=none header.i=@test.com");
        assertThat(result.dkim()).isEqualTo(AuthStatus.NONE);
    }

    @Test
    void parse_null() {
        var result = parser.parse(null);
        assertThat(result.spf()).isEqualTo(AuthStatus.NONE);
        assertThat(result.dkim()).isEqualTo(AuthStatus.NONE);
        assertThat(result.dmarc()).isEqualTo(AuthStatus.NONE);
    }

    @Test
    void parse_empty() {
        var result = parser.parse("");
        assertThat(result.spf()).isEqualTo(AuthStatus.NONE);
    }
}
