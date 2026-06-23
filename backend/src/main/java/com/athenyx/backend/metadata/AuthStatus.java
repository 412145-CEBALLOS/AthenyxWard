package com.athenyx.backend.metadata;

public enum AuthStatus {
    PASS,
    FAIL,
    NEUTRAL,
    NONE,
    SOFTFAIL,
    PERMERROR,
    TEMPERROR;

    public static AuthStatus fromString(String value) {
        if (value == null || value.isBlank()) return NONE;
        String lower = value.toUpperCase();
        for (AuthStatus s : values()) {
            if (s.name().equals(lower)) return s;
        }
        return NONE;
    }
}
