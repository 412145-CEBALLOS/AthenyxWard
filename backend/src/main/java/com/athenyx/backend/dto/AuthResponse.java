package com.athenyx.backend.dto;

/**
 * Response returned to the SPA after a successful OAuth2 login.
 *
 * @param token short-lived JWT (also mirrored in the {@code athenyx_token}
 *              cookie for SSR/hydration convenience)
 * @param user  current user profile
 */
public record AuthResponse(
    String token,
    UserInfo user
) {}
