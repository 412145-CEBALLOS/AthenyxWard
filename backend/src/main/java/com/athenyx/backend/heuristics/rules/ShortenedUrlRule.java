package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ShortenedUrlRule implements HeuristicRule {

    private static final List<String> URL_SHORTENERS = List.of(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
        "buff.ly", "adf.ly", "bit.do", "mcaf.ee", "su.pr", "tiny.cc",
        "tr.im", "cli.gs", "short.to", "shorl.com", "x.co", "v.gd",
        "shorenstein", "cutt.ly", "rb.gy", "linktr.ee"
    );

    @Override
    public String name() {
        return "ShortenedUrlRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.MEDIUM;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        int count = 0;
        for (String url : input.urls()) {
            if (url == null) continue;
            String lower = url.toLowerCase();
            for (String shortener : URL_SHORTENERS) {
                if (lower.contains(shortener)) {
                    count++;
                    break;
                }
            }
        }

        if (count == 0) {
            return Optional.empty();
        }

        int score = count == 1 ? 30 : 60;
        String description = count == 1
            ? "URL acortada detectada: " + count + " uso de servicio de acortamiento de URLs"
            : "Múltiples URLs acortadas detectadas: " + count + " usos de servicios de acortamiento";

        return Optional.of(new HeuristicFinding(name(), description, score));
    }
}
