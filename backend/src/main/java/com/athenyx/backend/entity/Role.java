package com.athenyx.backend.entity;

/**
 * Subscription role assigned to a {@link User}.
 *
 * <ul>
 *     <li>{@link #ADMIN} — platform operators. Can access the admin
 *         dashboard and user management features.</li>
 *     <li>{@link #PREMIUM} — paying subscribers. Unlimited analyses and
 *         access to productivity features (important emails, reminders).</li>
 *     <li>{@link #TRIAL} — default role on first login. Limited to 20
 *         analyses during a 30-day window; productivity features
 *         disabled.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    PREMIUM,
    TRIAL
}
