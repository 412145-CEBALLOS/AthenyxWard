package com.athenyx.backend.metadata;

public enum SenderTrustLevel {
    TRUSTED,
    UNKNOWN,
    SUSPICIOUS;

    public static SenderTrustLevel fromScore(int score) {
        if (score >= 70) return TRUSTED;
        if (score >= 40) return UNKNOWN;
        return SUSPICIOUS;
    }
}
