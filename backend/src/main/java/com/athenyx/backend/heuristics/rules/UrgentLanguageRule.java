package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class UrgentLanguageRule implements HeuristicRule {

    private static final List<Pattern> URGENT_PATTERNS = List.of(
        Pattern.compile("urgente", Pattern.CASE_INSENSITIVE),
        Pattern.compile("inmediato", Pattern.CASE_INSENSITIVE),
        Pattern.compile("suspendido", Pattern.CASE_INSENSITIVE),
        Pattern.compile("verificar ahora", Pattern.CASE_INSENSITIVE),
        Pattern.compile("24 horas?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("act[uú]e ya", Pattern.CASE_INSENSITIVE),
        Pattern.compile("sin demora", Pattern.CASE_INSENSITIVE),
        Pattern.compile("cuenta bloqueada", Pattern.CASE_INSENSITIVE),
        Pattern.compile("confirme su identidad", Pattern.CASE_INSENSITIVE),
        Pattern.compile("verifique su cuenta", Pattern.CASE_INSENSITIVE),
        Pattern.compile("actualice sus datos", Pattern.CASE_INSENSITIVE),
        Pattern.compile("plazo limitado", Pattern.CASE_INSENSITIVE),
        Pattern.compile("finaliza en", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ha sido comprometido", Pattern.CASE_INSENSITIVE),
        Pattern.compile("actividad sospechosa", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String name() {
        return "UrgentLanguageRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.MEDIUM;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String text = (input.subject() != null ? input.subject() : "") + " " +
                      (input.content() != null ? input.content() : "");

        int matches = 0;
        for (Pattern p : URGENT_PATTERNS) {
            if (p.matcher(text).find()) {
                matches++;
            }
        }

        if (matches == 0) {
            return Optional.empty();
        }

        int score = Math.min(60, matches * 15);
        String description = matches == 1
            ? "Lenguaje de urgencia detectado: 'urgente', 'inmediato' o similar"
            : "Lenguaje de urgencia repetido: " + matches + " indicadores de presión temporal";

        return Optional.of(new HeuristicFinding(name(), description, score));
    }
}
