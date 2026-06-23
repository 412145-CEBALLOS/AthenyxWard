package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class MaliciousUrlRule implements HeuristicRule {

    private static final Pattern IP_URL = Pattern.compile("https?://\\d{1,3}(\\.\\d{1,3}){3}(/|:)");
    private static final Pattern AT_IN_URL = Pattern.compile("https?://[^/]*@[^/]");
    private static final Pattern PORT_NUMBER = Pattern.compile("https?://[^:]+:\\d{2,5}/");
    private static final List<String> DANGEROUS_TLDS = List.of(
        ".zip", ".mov", ".top", ".click", ".country", ".xyz", ".work", ".kim", ".loan", ".download"
    );
    private static final Pattern SUBDOMAIN_HEAVY = Pattern.compile("https?://[^/]+\\.[^/]+\\.[^/]+");

    @Override
    public String name() {
        return "MaliciousUrlRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        int indicators = 0;
        boolean hasIpUrl = false;
        boolean hasAtUrl = false;
        boolean hasPortUrl = false;
        boolean hasManySubdomains = false;
        boolean hasDangerousTld = false;

        for (String url : input.urls()) {
            if (url == null) continue;
            String lower = url.toLowerCase();

            if (IP_URL.matcher(url).find()) {
                hasIpUrl = true;
                indicators += 3;
            }
            if (AT_IN_URL.matcher(url).find()) {
                hasAtUrl = true;
                indicators += 3;
            }
            if (PORT_NUMBER.matcher(url).find()) {
                hasPortUrl = true;
                indicators += 2;
            }
            if (hasManySubdomains(url)) {
                hasManySubdomains = true;
                indicators += 2;
            }
            for (String tld : DANGEROUS_TLDS) {
                if (lower.contains(tld)) {
                    hasDangerousTld = true;
                    indicators += 2;
                    break;
                }
            }
        }

        if (indicators == 0) {
            return Optional.empty();
        }

        int score = Math.min(90, 20 + indicators * 10);
        StringBuilder desc = new StringBuilder("URLs con patrones sospechosos: ");
        if (hasIpUrl) desc.append("IP directa, ");
        if (hasAtUrl) desc.append("@ en URL, ");
        if (hasPortUrl) desc.append("puerto inusual, ");
        if (hasManySubdomains) desc.append("muchos subdominios, ");
        if (hasDangerousTld) desc.append("TLD peligroso, ");
        String description = desc.substring(0, desc.length() - 2);

        return Optional.of(new HeuristicFinding(name(), description, score));
    }

    private boolean hasManySubdomains(String url) {
        String withoutScheme = url.replaceFirst("https?://", "");
        int dots = 0;
        for (char c : withoutScheme.toCharArray()) {
            if (c == '.') dots++;
            if (c == '/') break;
        }
        return dots >= 4;
    }
}
