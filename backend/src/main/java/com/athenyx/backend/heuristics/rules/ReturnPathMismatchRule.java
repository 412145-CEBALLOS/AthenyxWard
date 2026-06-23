package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ReturnPathMismatchRule implements HeuristicRule {

    @Override
    public String name() {
        return "ReturnPathMismatchRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String returnPath = input.returnPath();
        String sender = input.sender();

        if (returnPath == null || returnPath.isBlank()) {
            return Optional.empty();
        }

        String returnPathDomain = extractDomain(returnPath);
        String senderDomain = extractDomain(sender);

        if (returnPathDomain == null || senderDomain == null || returnPathDomain.isBlank()) {
            return Optional.empty();
        }

        if (!returnPathDomain.equalsIgnoreCase(senderDomain)) {
            return Optional.of(new HeuristicFinding(
                name(),
                "Return-Path ('" + returnPath + "') tiene dominio diferente al From ('" + sender + "'): posible suplantación de origen",
                85
            ));
        }

        return Optional.empty();
    }

    private String extractDomain(String address) {
        if (address == null || address.isBlank()) return "";
        String cleaned = address.trim();
        if (cleaned.startsWith("<") && cleaned.endsWith(">")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        int at = cleaned.indexOf('@');
        if (at >= 0) {
            int domainEnd = cleaned.indexOf('>', at);
            String domain = cleaned.substring(at + 1);
            if (domainEnd > at) {
                domain = cleaned.substring(at + 1, domainEnd);
            }
            return domain.trim().toLowerCase();
        }
        return cleaned.toLowerCase();
    }
}
