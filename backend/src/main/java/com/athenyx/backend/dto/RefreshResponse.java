package com.athenyx.backend.dto;

/**
 * Response payload of {@code POST /api/auth/refresh}.
 *
 * @param accessToken freshly-minted JWT
 * @param expiresIn   lifetime of the access token in seconds
 */
public record RefreshResponse(String accessToken, long expiresIn) {
}
