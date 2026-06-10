package com.athenyx.backend.security;

/**
 * Unchecked exception thrown by {@link RefreshTokenService} when a
 * presented refresh token is missing, expired, revoked, or appears to
 * have been replayed. Translated to HTTP 401 by
 * {@link com.athenyx.backend.config.GlobalExceptionHandler}.
 */
public class RefreshTokenException extends RuntimeException {
    /**
     * Reason categories. The {@code Kind} is exposed via
     * {@link #getKind()} so clients/tests can branch on the cause.
     */
    public enum Kind { MISSING, EXPIRED, REUSE_DETECTED, REVOKED }

    private final Kind kind;

    public RefreshTokenException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
