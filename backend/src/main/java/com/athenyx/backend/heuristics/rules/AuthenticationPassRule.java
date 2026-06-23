package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Legitimacy signal: subtracts from the overall risk score when
 * SPF / DKIM / DMARC authentication passes for the incoming message.
 *
 * <p>This rule is special-cased with {@link RuleSeverity#TRUST}
 * (weight {@code -1.0}) so its finding score is treated as a
 * <em>negative</em> contribution by {@link ThreatScorer}.</p>
 *
 * <p>Each successful authentication header reduces the raw score by
 * 10 (SPF), 10 (DKIM) and 15 (DMARC). The biggest trust gain is DMARC
 * because DMARC pass implies both SPF and DKIM aligned to the visible
 * From domain.</p>
 */
@Component
public class AuthenticationPassRule implements HeuristicRule {

    private static final String PASS = "pass";

    @Override
    public String name() {
        return "AuthenticationPassRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.TRUST;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        int boost = 0;
        StringBuilder desc = new StringBuilder("Autenticación válida: ");

        if (PASS.equalsIgnoreCase(input.spfStatus())) {
            boost += 10;
            desc.append("SPF pass, ");
        }
        if (PASS.equalsIgnoreCase(input.dkimStatus())) {
            boost += 10;
            desc.append("DKIM pass, ");
        }
        if (PASS.equalsIgnoreCase(input.dmarcStatus())) {
            boost += 15;
            desc.append("DMARC pass, ");
        }

        if (boost == 0) {
            return Optional.empty();
        }

        String description = desc.substring(0, desc.length() - 2);
        // Positive score — the TRUST weight (-1.0) in ThreatScorer
        // flips the sign at aggregation time so it subtracts from
        // the overall risk.
        return Optional.of(new HeuristicFinding(name(), description, boost));
    }
}
