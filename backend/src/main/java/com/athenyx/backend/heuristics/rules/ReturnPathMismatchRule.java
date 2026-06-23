package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import com.athenyx.backend.heuristics.whitelist.BulkSenderReturnPathDomains;
import com.athenyx.backend.heuristics.whitelist.TrustedSenderDomains;
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
        return RuleSeverity.MEDIUM;
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

        if (returnPathDomain == null || returnPathDomain.isBlank()
                || senderDomain == null || senderDomain.isBlank()) {
            return Optional.empty();
        }

        if (returnPathDomain.equalsIgnoreCase(senderDomain)) {
            return Optional.empty();
        }

        // Trusted corporate senders (Nintendo, Google, PayPal, Apple, ...)
        // legitimately relay through Amazon SES, SendGrid, scoutcamp, etc.
        // Their Return-Path will never match the visible From domain.
        if (TrustedSenderDomains.matches(senderDomain)) {
            return Optional.empty();
        }

        // If the Return-Path itself belongs to a known ESP / bulk-sender
        // infrastructure, treat the mismatch as benign.
        if (BulkSenderReturnPathDomains.matches(returnPathDomain)) {
            return Optional.empty();
        }

        return Optional.of(new HeuristicFinding(
            name(),
            "Return-Path ('" + returnPath + "') tiene dominio diferente al From ('" + sender
                + "'): posible suplantación de origen",
            45
        ));
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
