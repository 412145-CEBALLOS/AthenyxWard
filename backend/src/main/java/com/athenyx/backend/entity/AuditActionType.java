package com.athenyx.backend.entity;

/**
 * Tipos de acción grabadas en {@link AuditLog}.
 *
 * <ul>
 *   <li>Cada valor corresponde a un {@code ApplicationEvent} publicado
 *       por {@code AuditEventPublisher}.</li>
 *   <li>Los valores {@code *_CHANGE}, {@code *_DEACTIVATED}, {@code *_UPDATED}
 *       son cableados por las US de admin correspondientes (4.3, 4.4).</li>
 * </ul>
 */
public enum AuditActionType {
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    TOKEN_REFRESH_FAILED,
    ROLE_CHANGE,
    USER_DEACTIVATED,
    USER_REACTIVATED,
    USER_DELETED,
    TRIAL_RESET,
    CONFIG_UPDATE,
    PHISHING_DETECTED,
    AUTO_ANALYSIS,
    EMAIL_MARKED_IMPORTANT,
    EMAIL_HIDDEN,
    EMAIL_UNHIDDEN,
    EMAIL_DELETED,
    REMINDER_CREATED,
    REMINDER_UPDATED,
    REMINDER_DELETED,
    REMINDER_DONE,
    EXPORT_CSV,
    CONFIG_PURGE,
    SESSION_REVOKED,
    PAYMENT_INITIATED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    SUBSCRIPTION_CANCELED,
    SUBSCRIPTION_EXPIRED,
    SUBSCRIPTION_EMAIL_SENT
}
