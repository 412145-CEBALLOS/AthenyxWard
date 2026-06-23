package com.athenyx.backend.heuristics.whitelist;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedSenderDomainsTest {

    @Test
    void exactMatch_isTrusted() {
        assertThat(TrustedSenderDomains.matches("paypal.com")).isTrue();
        assertThat(TrustedSenderDomains.matches("google.com")).isTrue();
        assertThat(TrustedSenderDomains.matches("facebookmail.com")).isTrue();
    }

    @Test
    void subdomainMatch_isTrusted() {
        assertThat(TrustedSenderDomains.matches("mail.paypal.com")).isTrue();
        assertThat(TrustedSenderDomains.matches("communications.paypal.com")).isTrue();
        assertThat(TrustedSenderDomains.matches("accounts.nintendo.com")).isTrue();
        assertThat(TrustedSenderDomains.matches("email.claude.com")).isTrue();
    }

    @Test
    void caseInsensitive() {
        assertThat(TrustedSenderDomains.matches("PAYPAL.COM")).isTrue();
        assertThat(TrustedSenderDomains.matches("Mail.PayPal.com")).isTrue();
    }

    @Test
    void nullAndEmpty_isNotTrusted() {
        assertThat(TrustedSenderDomains.matches(null)).isFalse();
        assertThat(TrustedSenderDomains.matches("")).isFalse();
        assertThat(TrustedSenderDomains.matches("   ")).isFalse();
    }

    @Test
    void homoglyphLookalike_isNotTrusted() {
        // paypal-secure.com (no leading dot)
        assertThat(TrustedSenderDomains.matches("paypal-secure.com")).isFalse();
        // paypal.com.evil.com (does not end with .paypal.com)
        assertThat(TrustedSenderDomains.matches("paypal.com.evil.com")).isFalse();
        // notpaypal.com (no separator before the brand)
        assertThat(TrustedSenderDomains.matches("notpaypal.com")).isFalse();
    }

    @Test
    void unknownDomain_isNotTrusted() {
        assertThat(TrustedSenderDomains.matches("scammer.ru")).isFalse();
        assertThat(TrustedSenderDomains.matches("paypa1.com")).isFalse();
        assertThat(TrustedSenderDomains.matches("amaz0n-secure.com")).isFalse();
    }
}
