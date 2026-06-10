package com.athenyx.backend.security;

import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.service.RefreshTokenService;
import com.athenyx.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Spring Security success handler invoked after the Google OAuth2 dance.
 *
 * <p>Responsibilities:
 * <ol>
 *     <li>Upsert the {@link User} row, encrypting the Google access and
 *         refresh tokens before persisting them.</li>
 *     <li>For returning users, bump {@code User.tokenVersion} so any
 *         previously-issued access JWT is immediately rejected.</li>
 *     <li>Generate a fresh JWT and a new refresh-token family via
 *         {@link RefreshTokenService#issue}.</li>
 *     <li>Set {@code athenyx_token} and {@code athenyx_refresh} as
 *         HttpOnly cookies (Secure flag follows
 *         {@code app.auth.cookie-secure}).</li>
 *     <li>302-redirect the browser to {@code ${app.frontend.url}/home}.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final TokenEncryptionService tokenEncryptionService;
    private final RefreshTokenService refreshTokenService;
    //private final RefreshCookieManager refreshCookieManager;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.auth.cookie-secure:false}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        String googleId = oauthUser.getAttribute("sub");
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        String picture = oauthUser.getAttribute("picture");

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
        String accessToken = client.getAccessToken().getTokenValue();
        String refreshToken = client.getRefreshToken() != null
                ? client.getRefreshToken().getTokenValue() : null;
        LocalDateTime tokenExpiresAt = client.getAccessToken().getExpiresAt() != null
                ? LocalDateTime.ofInstant(client.getAccessToken().getExpiresAt(), ZoneOffset.UTC)
                : null;

        String encryptedAccessToken = tokenEncryptionService.encrypt(accessToken);
        String encryptedRefreshToken = refreshToken != null ? tokenEncryptionService.encrypt(refreshToken) : null;

        java.util.Optional<User> existingOpt = userRepository.findByGoogleId(googleId);

        User user = existingOpt
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setName(name);
                    existing.setPictureUrl(picture);
                    existing.setGoogleAccessToken(encryptedAccessToken);
                    existing.setGoogleRefreshToken(encryptedRefreshToken);
                    existing.setGoogleAccessTokenExpiresAt(tokenExpiresAt);
                    return existing;
                })
                .orElseGet(() -> User.builder()
                        .googleId(googleId)
                        .email(email)
                        .name(name)
                        .pictureUrl(picture)
                        .role(Role.TRIAL)
                        .trialEndDate(LocalDateTime.now().plusDays(30))
                        .googleAccessToken(encryptedAccessToken)
                        .googleRefreshToken(encryptedRefreshToken)
                        .googleAccessTokenExpiresAt(tokenExpiresAt)
                        .accessibilityMode(true)
                        .build());

        user = userRepository.save(user);

        long tokenVersion;
        if (existingOpt.isPresent()) {
            userRepository.incrementTokenVersion(user.getId());
            tokenVersion = userRepository.findTokenVersionById(user.getId()).orElseThrow();
        } else {
            tokenVersion = 0L;
        }

        String jwt = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), tokenVersion);

        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(user, request, tokenVersion);
        long refreshMaxAge = Duration.between(
                issued.row().getIssuedAt(),
                issued.row().getAbsoluteExpiresAt()).toSeconds();

        String accessCookie = buildSetCookie(
                "athenyx_token", jwt, "/", jwtUtil.getExpirationMs() / 1000, false);
        String refreshCookie = buildSetCookie(
                "athenyx_refresh", issued.raw(), "/", refreshMaxAge, false);

        log.info("OAuth2 success for user={} ({}): writing access cookie + refresh cookie (maxAge={}s)",
                user.getId(), user.getEmail(), refreshMaxAge);
        log.debug("Set-Cookie access: {}", accessCookie);
        log.debug("Set-Cookie refresh: {}", refreshCookie);

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie);

        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/home");
    }

    private String buildSetCookie(String name, String value, String path, long maxAgeSeconds, boolean sameSiteStrict) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(name).append('=').append(value)
                .append("; Path=").append(path)
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly");
        if (cookieSecure) {
            sb.append("; Secure");
        }
        if (sameSiteStrict) {
            sb.append("; SameSite=Strict");
        }
        return sb.toString();
    }
}
