package com.athenyx.backend.heuristics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class HeuristicEngine {

    private static final long TIMEOUT_MS = 900;

    private final List<HeuristicRule> rules;
    private final ThreatScorer scorer;

    public HeuristicEngine(List<HeuristicRule> rules, ThreatScorer scorer) {
        this.rules = rules;
        this.scorer = scorer;
        log.info("HeuristicEngine initialized with {} rules", rules.size());
    }

    public HeuristicResult run(EmailHeuristicsInput input) {
        long start = System.nanoTime();
        List<HeuristicFinding> findings = new ArrayList<>();

        for (HeuristicRule rule : rules) {
            long ruleStart = System.nanoTime();
            try {
                rule.apply(input).ifPresent(findings::add);
            } catch (Exception e) {
                log.warn("Rule {} threw exception: {}", rule.name(), e.getMessage());
            }
            long ruleElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ruleStart);
            if (ruleElapsed > 100) {
                log.debug("Rule {} took {}ms", rule.name(), ruleElapsed);
            }
        }

        HeuristicResult result = scorer.score(findings);

        long totalElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (totalElapsed > TIMEOUT_MS) {
            log.warn("HeuristicEngine exceeded timeout: {}ms > {}ms", totalElapsed, TIMEOUT_MS);
        } else {
            log.debug("HeuristicEngine completed in {}ms with {} findings (risk={}%)",
                totalElapsed, findings.size(), result.riskPercentage());
        }

        return result;
    }

    public int ruleCount() {
        return rules.size();
    }
}
