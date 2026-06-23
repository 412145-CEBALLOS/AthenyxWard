package com.athenyx.backend.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MassMailingProviderTest {

    @ParameterizedTest
    @CsvSource({
        "'Mailchimp MTA version #123', MAILCHIMP",
        "'SendGrid API', SENDGRID",
        "'Amazon SES mailer', AMAZON_SES",
        "'X-Mailer: Mailchimp', MAILCHIMP",
        "'X-Mailer: SendGrid', SENDGRID",
        "'X-Mailer: Mandrill', MANDRILL",
    })
    void detect_fromXMailer(String xMailer, MassMailingProvider expected) {
        MassMailingProvider result = MassMailingProvider.detect(xMailer, null, null, null);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void detect_noMatch_returnsNone() {
        MassMailingProvider result = MassMailingProvider.detect(
            "Microsoft Outlook", null, null, "Reunión mañana");
        assertThat(result).isEqualTo(MassMailingProvider.NONE);
    }

    @Test
    void detect_fromSubject() {
        MassMailingProvider result = MassMailingProvider.detect(
            null, null, null, "Mailchimp newsletter");
        assertThat(result).isEqualTo(MassMailingProvider.MAILCHIMP);
    }
}
