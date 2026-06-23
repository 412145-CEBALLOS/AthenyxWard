package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SuspiciousTldRule implements HeuristicRule {

    private static final List<String> SUSPICIOUS_TLDS = List.of(
        "zip", "mov", "top", "click", "country", "xyz", "work", "kim", "loan",
        "download", "racing", "review", "stream", "accountant", "cricket",
        "date", "faith", "loan", "men", "party", "race", "science", "win",
        "bid", "date", ".download", ".racing", ".stream"
    );

    @Override
    public String name() {
        return "SuspiciousTldRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.MEDIUM;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        boolean senderTld = false;
        boolean urlTld = false;
        String offendingDomain = null;

        String senderDomain = extractDomain(input.sender());
        if (senderDomain != null) {
            String senderTldValue = extractTld(senderDomain);
            if (senderTldValue != null && SUSPICIOUS_TLDS.contains(senderTldValue.toLowerCase())) {
                senderTld = true;
                offendingDomain = senderDomain;
            }
        }

        for (String url : input.urls()) {
            if (url == null) continue;
            String urlDomain = extractDomainFromUrl(url);
            if (urlDomain != null) {
                String tld = extractTld(urlDomain);
                if (tld != null && SUSPICIOUS_TLDS.contains(tld.toLowerCase())) {
                    urlTld = true;
                    if (offendingDomain == null) offendingDomain = urlDomain;
                    break;
                }
            }
        }

        if (!senderTld && !urlTld) {
            return Optional.empty();
        }

        int score = senderTld ? 75 : 40;
        String description = senderTld
            ? "Dominio del remitente usa TLD sospechoso: ." + extractTld(offendingDomain)
            : "URL en el correo usa TLD sospechoso: ." + extractTld(offendingDomain);

        return Optional.of(new HeuristicFinding(name(), description, score));
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

    private String extractDomainFromUrl(String url) {
        try {
            String withoutScheme = url.replaceFirst("https?://", "");
            int slash = withoutScheme.indexOf('/');
            if (slash > 0) {
                return withoutScheme.substring(0, slash);
            }
            return withoutScheme;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTld(String domain) {
        if (domain == null) return null;
        int lastDot = domain.lastIndexOf('.');
        if (lastDot < 0 || lastDot == domain.length() - 1) return null;
        return domain.substring(lastDot + 1);
    }
}
