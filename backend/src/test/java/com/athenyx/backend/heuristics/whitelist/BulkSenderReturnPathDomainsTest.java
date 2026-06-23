package com.athenyx.backend.heuristics.whitelist;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BulkSenderReturnPathDomainsTest {

    @Test
    void amazonSes_isTrusted() {
        assertThat(BulkSenderReturnPathDomains.matches("amazonses.com")).isTrue();
        assertThat(BulkSenderReturnPathDomains.matches("amazonaws.com")).isTrue();
    }

    @Test
    void googleBounce_isTrusted() {
        assertThat(BulkSenderReturnPathDomains.matches("scoutcamp.bounces.google.com")).isTrue();
        assertThat(BulkSenderReturnPathDomains.matches("bounces.google.com")).isTrue();
    }

    @Test
    void sendgrid_isTrusted() {
        assertThat(BulkSenderReturnPathDomains.matches("sendgrid.net")).isTrue();
        assertThat(BulkSenderReturnPathDomains.matches("bounce.sendgrid.net")).isTrue();
    }

    @Test
    void exactMatchOnly_noSubdomainMatching() {
        // 'sendgrid.net' is trusted, but 'evil.sendgrid.net' is NOT
        // because we use exact-match only on Return-Path.
        assertThat(BulkSenderReturnPathDomains.matches("sendgr1d.net")).isFalse();
        assertThat(BulkSenderReturnPathDomains.matches("amazon-ses.com")).isFalse();
        assertThat(BulkSenderReturnPathDomains.matches("scammer-amazonses.com")).isFalse();
    }

    @Test
    void nullAndEmpty_isNotTrusted() {
        assertThat(BulkSenderReturnPathDomains.matches(null)).isFalse();
        assertThat(BulkSenderReturnPathDomains.matches("")).isFalse();
    }

    @Test
    void caseInsensitive() {
        assertThat(BulkSenderReturnPathDomains.matches("AMAZONSES.COM")).isTrue();
    }
}
