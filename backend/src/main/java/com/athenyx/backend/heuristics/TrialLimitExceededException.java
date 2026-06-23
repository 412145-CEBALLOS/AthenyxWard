package com.athenyx.backend.heuristics;

public class TrialLimitExceededException extends RuntimeException {
    private final int remaining;

    public TrialLimitExceededException(String message, int remaining) {
        super(message);
        this.remaining = remaining;
    }

    public int remaining() {
        return remaining;
    }
}
