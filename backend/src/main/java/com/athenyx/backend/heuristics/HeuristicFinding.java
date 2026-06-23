package com.athenyx.backend.heuristics;

public record HeuristicFinding(
    String rule,
    String description,
    int score
) {}
