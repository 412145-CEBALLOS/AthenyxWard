package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class ScamLanguagePatternRule implements HeuristicRule {

    private static final List<Pattern> SCAM_PATTERNS = List.of(
        Pattern.compile("ha[\\s]+ganado", Pattern.CASE_INSENSITIVE),
        Pattern.compile("felicitaciones", Pattern.CASE_INSENSITIVE),
        Pattern.compile("loter[ií]a", Pattern.CASE_INSENSITIVE),
        Pattern.compile("herencia", Pattern.CASE_INSENSITIVE),
        Pattern.compile("pr[ií]ncipe nigeriano", Pattern.CASE_INSENSITIVE),
        Pattern.compile("millonario", Pattern.CASE_INSENSITIVE),
        Pattern.compile("transferencia bancaria", Pattern.CASE_INSENSITIVE),
        Pattern.compile("beneficiario", Pattern.CASE_INSENSITIVE),
        Pattern.compile("cuenta bancaria", Pattern.CASE_INSENSITIVE),
        Pattern.compile("premio", Pattern.CASE_INSENSITIVE),
        Pattern.compile("sorteo", Pattern.CASE_INSENSITIVE),
        Pattern.compile("coa[c]?tez", Pattern.CASE_INSENSITIVE),
        Pattern.compile("d[oó]lares?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("euros?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("donaci[oó]n", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ayuda financiera", Pattern.CASE_INSENSITIVE),
        Pattern.compile("inversi[oó]n segura", Pattern.CASE_INSENSITIVE),
        Pattern.compile("retorno garantizados?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("cuenta no verificada", Pattern.CASE_INSENSITIVE),
        Pattern.compile("clic aqu[ií]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("reclama tu premio", Pattern.CASE_INSENSITIVE),
        Pattern.compile("tu n[uú]mero ganador", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String name() {
        return "ScamLanguagePatternRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String text = (input.subject() != null ? input.subject() : "") + " " +
                      (input.content() != null ? input.content() : "");

        int matches = 0;
        for (Pattern p : SCAM_PATTERNS) {
            if (p.matcher(text).find()) {
                matches++;
            }
        }

        if (matches == 0) {
            return Optional.empty();
        }

        int score = Math.min(70, 20 + matches * 15);
        String description = matches >= 3
            ? "Patrón de lenguaje de scam muy evidente: " + matches + " indicadores"
            : "Patrón de lenguaje típico de estafas detectado: '" + extractMatchedPattern(input, SCAM_PATTERNS) + "'";

        return Optional.of(new HeuristicFinding(name(), description, score));
    }

    private String extractMatchedPattern(EmailHeuristicsInput input, List<Pattern> patterns) {
        String text = (input.subject() != null ? input.subject() : "") + " " +
                      (input.content() != null ? input.content() : "");
        for (Pattern p : patterns) {
            var m = p.matcher(text);
            if (m.find()) {
                return m.group();
            }
        }
        return "patrón detectado";
    }
}
