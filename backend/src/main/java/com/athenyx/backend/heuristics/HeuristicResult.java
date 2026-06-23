package com.athenyx.backend.heuristics;

import java.util.List;

public record HeuristicResult(
    List<HeuristicFinding> findings,
    int riskPercentage,
    ThreatLevel threatLevel
) {
    public static HeuristicResult safe() {
        return new HeuristicResult(List.of(), 0, ThreatLevel.GREEN);
    }
}
