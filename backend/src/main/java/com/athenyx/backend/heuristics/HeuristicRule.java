package com.athenyx.backend.heuristics;

import java.util.Optional;

public interface HeuristicRule {
    String name();
    RuleSeverity severity();
    Optional<HeuristicFinding> apply(EmailHeuristicsInput input);
}
