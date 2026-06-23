package com.athenyx.backend.metadata;

import java.util.Set;

public enum MassMailingProvider {
    MAILCHIMP("mailchimp.com", "mailchimp"),
    SENDGRID("sendgrid", "sendgrid.com"),
    MANDRILL("mandrill", "mailchimp mandate"),
    POSTMARK("postmarkapp.com", "postmark"),
    AMAZON_SES("amazonses.com", "amazon ses", "awstrust"),
    MAILGUN("mailgun.org", "mailgun.com", "mailgun.net"),
    SENDINBLUE("sendinblue", "sib"),
    MAILJET("mailjet.com", "mj"),
    HUBSPOT("hubspot", "hs", "hubs"),
    CONSTANTCONTACT("constantcontact", "ctct"),
    INTERCOM("intercom", "icmp"),
    CUSTOM_CAMPAIGN("campaign", "campa", "newsletter"),
    NONE(null);

    private final Set<String> markers;

    MassMailingProvider(String... markers) {
        this.markers = Set.of(markers != null ? markers : new String[]{});
    }

    public static MassMailingProvider detect(String xMailer, String listUnsubscribe, String receivedHeaders, String subject) {
        String combined = String.join(" ",
            (xMailer != null ? xMailer : ""),
            (listUnsubscribe != null ? listUnsubscribe : ""),
            (receivedHeaders != null ? receivedHeaders : ""),
            (subject != null ? subject : "")
        ).toLowerCase();

        for (MassMailingProvider p : values()) {
            if (p == NONE) continue;
            for (String marker : p.markers) {
                if (marker != null && combined.contains(marker)) {
                    return p;
                }
            }
        }
        return NONE;
    }
}
