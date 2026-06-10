package com.athenyx.backend.entity;

/**
 * Reason a {@link RefreshToken} was revoked. Stored for audit and for the
 * reuse-detection flow.
 *
 * <ul>
 *     <li>{@link #LOGOUT} — explicit logout from a single device.</li>
 *     <li>{@link #REUSE_DETECTED} — a previously-rotated token was
 *         presented again; the entire family is revoked.</li>
 *     <li>{@link #REPLACED} — the token was rotated to a successor
 *         (normal refresh flow).</li>
 *     <li>{@link #EXPIRED} — sliding or absolute lifetime exceeded.</li>
 *     <li>{@link #ADMIN} — manually revoked by an administrator.</li>
 * </ul>
 */
public enum RevokedReason {
    LOGOUT,
    REUSE_DETECTED,
    REPLACED,
    EXPIRED,
    ADMIN
}
