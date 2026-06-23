package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class FreeEmailProviderBrandRule implements HeuristicRule {

    private static final Set<String> FREE_PROVIDERS = Set.of(
        "gmail.com", "outlook.com", "hotmail.com", "yahoo.com", "yahoo.es",
        "yahoo.com.mx", "yahoo.com.ar", "yahoo.com.co", "yahoo.com.br",
        "icloud.com", "mail.com", "aol.com", "protonmail.com", "proton.me",
        "zoho.com", "yandex.com", "mail.ru", "qq.com", "163.com"
    );

    private static final Set<String> KNOWN_BRANDS_IN_SUBJECT = Set.of(
        "paypal", "amazon", "apple", "microsoft", "google", "facebook", "meta",
        "netflix", "spotify", "bancomer", "banorte", "santander", "bbva", "hsbc",
        "fedex", "dhl", "ups", "whatsapp", "instagram"
    );

    @Override
    public String name() {
        return "FreeEmailProviderBrandRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String senderDomain = extractDomain(input.sender());
        if (senderDomain == null) {
            return Optional.empty();
        }

        String senderDomainLower = senderDomain.toLowerCase();
        if (!FREE_PROVIDERS.contains(senderDomainLower)) {
            return Optional.empty();
        }

        String subjectLower = input.subject() != null ? input.subject().toLowerCase() : "";
        String senderNameLower = input.senderName() != null ? input.senderName().toLowerCase() : "";
        String combined = subjectLower + " " + senderNameLower;

        for (String brand : KNOWN_BRANDS_IN_SUBJECT) {
            if (combined.contains(brand)) {
                return Optional.of(new HeuristicFinding(
                    name(),
                    "Correo de cuenta gratuita (" + senderDomain + ") menciona la marca '" + brand + "' en el asunto o nombre del remitente",
                    90
                ));
            }
        }

        return Optional.empty();
    }

    private String extractDomain(String sender) {
        int at = sender.indexOf('@');
        if (at < 0) return null;
        int domainEnd = sender.indexOf('>', at);
        String domain = sender.substring(at + 1);
        if (domainEnd > at) {
            domain = sender.substring(at + 1, domainEnd);
        }
        return domain.trim();
    }
}
