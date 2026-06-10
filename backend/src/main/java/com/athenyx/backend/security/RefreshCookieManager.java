package com.athenyx.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds and clears the {@code athenyx_refresh} cookie.
 *
 * <p>Cookie attributes are sourced from {@code app.auth.*} properties
 * (with safe defaults). The {@code Secure} flag is forced on whenever
 * {@code server.force-https} is set.</p>
 */
@Component
public class RefreshCookieManager {

    private final String cookieName;
    private final String cookiePath;
    private final boolean cookieSecure;

    public RefreshCookieManager(
            @Value("${app.auth.refresh-cookie-name:athenyx_refresh}") String cookieName,
            @Value("${app.auth.refresh-cookie-path:/api/auth}") String cookiePath,
            @Value("${app.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${server.force-https:false}") boolean forceHttps) {
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieSecure = cookieSecure || forceHttps;
    }

    public ResponseCookie build(String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(cookiePath)
                .maxAge(maxAgeSeconds);
        return builder.build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(cookiePath)
                .maxAge(0)
                .build();
    }

    public String getCookieName() {
        return cookieName;
    }
}
