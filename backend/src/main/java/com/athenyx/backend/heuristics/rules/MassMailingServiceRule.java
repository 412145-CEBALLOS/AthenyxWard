package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class MassMailingServiceRule implements HeuristicRule {

    private static final Set<String> MASS_MAILING_MARKERS = Set.of(
        "mailchimp", "sendgrid", "mandrill", "postmark", "amazonses", "amazon ses",
        "mailgun", "sendinblue", "mailjet", "hubspot", "constantcontact",
        "intercom", "campaign", "newsletter", "list-unsubscribe", "precedence: bulk",
        "bulk mail", "no-reply@", "auto-", "autorespond"
    );

    private static final Set<String> FREE_EMAIL = Set.of(
        "gmail.com", "outlook.com", "hotmail.com", "yahoo.com", "icloud.com",
        "protonmail.com", "aol.com", "mail.com", "yandex.com"
    );

    @Override
    public String name() {
        return "MassMailingServiceRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.LOW;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        boolean hasMassMailingHeader = false;
        String reason = "";

        // The presence of a `List-Unsubscribe` header is REQUIRED by RFC 8058
        // for legitimate bulk senders — it is a STRONG signal of authenticity,
        // not risk. We still emit a finding (score 20) so the UI can surface
        // the fact that the email was sent in bulk, but the engine treats it
        // as informational, not suspicious.
        if (input.listUnsubscribe() != null && !input.listUnsubscribe().isBlank()) {
            hasMassMailingHeader = true;
            reason = "List-Unsubscribe presente";
        }

        if (input.xMailer() != null) {
            String xm = input.xMailer().toLowerCase();
            for (String marker : MASS_MAILING_MARKERS) {
                if (xm.contains(marker)) {
                    hasMassMailingHeader = true;
                    reason = "X-Mailer indica servicio de envío masivo";
                    break;
                }
            }
        }

        String senderDomain = extractDomain(input.sender());
        if (senderDomain != null && !senderDomain.isBlank()) {
            if (FREE_EMAIL.contains(senderDomain.toLowerCase())) {
                String contentLower = ((input.subject() != null ? input.subject() : "") +
                    " " + (input.content() != null ? input.content() : "")).toLowerCase();
                for (String marker : MASS_MAILING_MARKERS) {
                    if (contentLower.contains(marker) && !marker.contains("@")) {
                        hasMassMailingHeader = true;
                        reason = "Dominio gratuito con indicadores de envío masivo";
                        break;
                    }
                }
            }
        }

        if (!hasMassMailingHeader) {
            return Optional.empty();
        }

        return Optional.of(new HeuristicFinding(
            name(),
            "Remitente usa plataforma de envío masivo (RFC 8058: " + reason + ")",
            20
        ));
    }

    private String extractDomain(String sender) {
        if (sender == null || sender.isBlank()) return "";
        int at = sender.indexOf('@');
        if (at < 0) return "";
        int domainEnd = sender.indexOf('>', at);
        String domain = sender.substring(at + 1);
        if (domainEnd > at) {
            domain = sender.substring(at + 1, domainEnd);
        }
        return domain.trim();
    }
}
