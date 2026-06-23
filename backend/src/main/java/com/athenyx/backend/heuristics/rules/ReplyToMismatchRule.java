package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ReplyToMismatchRule implements HeuristicRule {

    @Override
    public String name() {
        return "ReplyToMismatchRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String replyTo = input.replyTo();
        String sender = input.sender();

        if (replyTo == null || replyTo.isBlank()) {
            return Optional.empty();
        }

        String replyToDomain = extractDomain(replyTo);
        String senderDomain = extractDomain(sender);

        if (replyToDomain == null || senderDomain == null) {
            return Optional.empty();
        }

        if (!replyToDomain.equalsIgnoreCase(senderDomain)) {
            return Optional.of(new HeuristicFinding(
                name(),
                "Reply-To ('" + replyTo + "') tiene dominio diferente al remitente ('" + sender + "'): posible suplantación",
                80
            ));
        }

        return Optional.empty();
    }

    private String extractDomain(String address) {
        int at = address.indexOf('@');
        if (at < 0) return null;
        int domainEnd = address.indexOf('>', at);
        String domain = address.substring(at + 1);
        if (domainEnd > at) {
            domain = address.substring(at + 1, domainEnd);
        }
        return domain.trim().toLowerCase();
    }
}
