package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class RiskyKeywordsRule implements HeuristicRule {

    /**
     * Curated list of strongly-correlated risk terms. Generic words
     * such as "tax", "factura", "invoice" were removed because they
     * appear in nearly every purchase receipt, bank statement or
     * utility bill and generated widespread false positives.
     */
    private static final List<Pattern> RISKY_PATTERNS = List.of(
        Pattern.compile("wire transfer", Pattern.CASE_INSENSITIVE),
        Pattern.compile("gift card", Pattern.CASE_INSENSITIVE),
        Pattern.compile("tarjeta de regalo", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bitcoin", Pattern.CASE_INSENSITIVE),
        Pattern.compile("western union", Pattern.CASE_INSENSITIVE),
        Pattern.compile("moneygram", Pattern.CASE_INSENSITIVE),
        Pattern.compile("invoice attached", Pattern.CASE_INSENSITIVE),
        Pattern.compile("factura adjunta", Pattern.CASE_INSENSITIVE),
        Pattern.compile("deuda", Pattern.CASE_INSENSITIVE),
        Pattern.compile("fraude", Pattern.CASE_INSENSITIVE),
        Pattern.compile("lavado de dinero", Pattern.CASE_INSENSITIVE),
        Pattern.compile("money laundering", Pattern.CASE_INSENSITIVE),
        Pattern.compile("terrorist", Pattern.CASE_INSENSITIVE),
        Pattern.compile("pharmaceutical", Pattern.CASE_INSENSITIVE),
        Pattern.compile("farmacéutico", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String name() {
        return "RiskyKeywordsRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.LOW;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String text = (input.subject() != null ? input.subject() : "") + " " +
                      (input.content() != null ? input.content() : "");

        int matches = 0;
        for (Pattern p : RISKY_PATTERNS) {
            if (p.matcher(text).find()) {
                matches++;
            }
        }

        if (matches == 0) {
            return Optional.empty();
        }

        int score = Math.min(40, matches * 8);
        String description = matches >= 2
            ? "Múltiples palabras clave de riesgo financiero detectadas: " + matches + " indicadores"
            : "Palabra clave de riesgo financiero detectada en el correo";

        return Optional.of(new HeuristicFinding(name(), description, score));
    }
}
