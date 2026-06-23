package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SuspiciousDomainRule implements HeuristicRule {

    private static final Set<String> KNOWN_BRANDS = Set.of(
        "paypal", "amazon", "apple", "microsoft", "google", "facebook", "instagram",
        "twitter", "linkedin", "netflix", "spotify", "dropbox", "adobe",
        "bancomer", "banorte", "santander", "bbva", "hsbc", "citibank",
        "chase", "wellsfargo", "bankofamerica", "scotiabank", "itau", "bradesco",
        " whatsapp", "telegram", "signal", "discord", "slack", "zoom",
        "fedex", "dhl", "ups", "usps", "correos"
    );

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("@[^@]+\\.[^@]+$");

    @Override
    public String name() {
        return "SuspiciousDomainRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        if (input.sender() == null) {
            return Optional.empty();
        }
        String senderDomain = extractDomain(input.sender());
        if (senderDomain == null || senderDomain.isBlank()) {
            return Optional.empty();
        }

        String normalized = senderDomain.toLowerCase();

        if (isSuspiciousDomain(normalized)) {
            int score = calculateSuspiciousScore(normalized);
            return Optional.of(new HeuristicFinding(
                name(),
                "Dominio sospecho detectado: '" + senderDomain + "' similar a marca conocida",
                score
            ));
        }

        return Optional.empty();
    }

    private String extractDomain(String sender) {
        var matcher = DOMAIN_PATTERN.matcher(sender);
        if (matcher.find()) {
            return matcher.group().substring(1);
        }
        return null;
    }

    private boolean isSuspiciousDomain(String domain) {
        for (String brand : KNOWN_BRANDS) {
            if (domain.contains(brand) && !domain.equals(brand + ".com") && !domain.endsWith("." + brand + ".com")) {
                return true;
            }
            if (damerauLevenshteinDistance(domain.replace(".", ""), brand) <= 2) {
                return true;
            }
        }
        return false;
    }

    private int calculateSuspiciousScore(String domain) {
        for (String brand : KNOWN_BRANDS) {
            if (domain.contains(brand)) {
                return 60;
            }
            int dist = damerauLevenshteinDistance(domain.replace(".", ""), brand);
            if (dist == 1) return 90;
            if (dist == 2) return 75;
        }
        return 60;
    }

    private int damerauLevenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
                if (i > 1 && j > 1
                    && a.charAt(i - 1) == b.charAt(j - 2)
                    && a.charAt(i - 2) == b.charAt(j - 1)) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + cost);
                }
            }
        }
        return dp[a.length()][b.length()];
    }
}
