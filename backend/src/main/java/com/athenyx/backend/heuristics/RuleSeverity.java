package com.athenyx.backend.heuristics;

public enum RuleSeverity {
    HIGH(1.2),
    MEDIUM(1.0),
    LOW(0.7);

    private final double weight;

    RuleSeverity(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }
}
