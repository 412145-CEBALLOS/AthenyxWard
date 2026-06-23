package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class SenderImpersonationRule implements HeuristicRule {

    private static final Set<String> KNOWN_BRANDS = Set.of(
        "paypal", "amazon", "apple", "microsoft", "google", "facebook", "meta",
        "instagram", "twitter", "x.com", "linkedin", "netflix", "spotify",
        "dropbox", "adobe", "ebay", "walmart", "target", "bestbuy",
        "bancomer", "banorte", "santander", "bbva", "hsbc", "citibank",
        "chase", "wellsfargo", "bankofamerica", "scotiabank",
        "fedex", "dhl", "ups", "usps",
        " netflix", " spotify"
    );

    private static final Set<String> FREE_EMAIL_PROVIDERS = Set.of(
        "gmail.com", "outlook.com", "hotmail.com", "yahoo.com", "yahoo.es",
        "icloud.com", "mail.com", "aol.com", "protonmail.com"
    );

    @Override
    public String name() {
        return "SenderImpersonationRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String displayName = input.senderName();
        String sender = input.sender();
        String senderDomain = extractDomain(sender);

        if (displayName == null || displayName.isBlank() || senderDomain == null) {
            return Optional.empty();
        }

        String displayLower = displayName.toLowerCase();
        String senderDomainLower = senderDomain.toLowerCase();

        for (String brand : KNOWN_BRANDS) {
            if (!displayLower.contains(brand)) continue;

            // The sender must NOT be the brand's own domain or a
            // legitimate subdomain of it (e.g. `mail.paypal.com`,
            // `communications.paypal.com` are real PayPal marketing
            // subdomains and must not be flagged).
            String brandDomain = brand.replace(" ", "") + ".com";
            boolean senderIsOfficialBrand =
                senderDomainLower.equals(brandDomain) ||
                senderDomainLower.endsWith("." + brandDomain);

            if (senderIsOfficialBrand) continue;

            if (FREE_EMAIL_PROVIDERS.contains(senderDomainLower)) {
                return Optional.of(new HeuristicFinding(
                    name(),
                    "Display name '" + displayName + "' menciona '" + brand + "' pero usa cuenta gratuita",
                    50
                ));
            }
            return Optional.of(new HeuristicFinding(
                name(),
                "Display name '" + displayName + "' menciona '" + brand + "' pero el email no es del dominio oficial",
                90
            ));
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
