package com.athenyx.backend.security;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.entity.Role;
import org.slf4j.MDC;
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
import org.springframework.transaction.annotation.Transactional;
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
    private final AuditEventPublisher auditEventPublisher;
    private final LoginAttemptService loginAttemptService;
    private final com.athenyx.backend.config.ConfigService configService;
    //private final RefreshCookieManager refreshCookieManager;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.auth.cookie-secure:false}")
    private boolean cookieSecure;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        String googleId = oauthUser.getAttribute("sub");
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        String picture = oauthUser.getAttribute("picture");
        Boolean emailVerified = oauthUser.getAttribute("email_verified");
        LocalDateTime now = LocalDateTime.now();

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
        String accessToken = client.getAccessToken().getTokenValue();
        String refreshToken = client.getRefreshToken() != null
                ? client.getRefreshToken().getTokenValue() : null;
        LocalDateTime tokenExpiresAt = client.getAccessToken().getExpiresAt() != null
                ? LocalDateTime.ofInstant(client.getAccessToken().getExpiresAt(), ZoneOffset.UTC)
                : null;

        String ip = getClientIp(request);
        if (loginAttemptService.isIpBlocked(ip)) {
            auditEventPublisher.publishLoginFailed(email, "ip_blocked");
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=ip_blocked");
            return;
        }

        String allowedDomains = configService.getString(com.athenyx.backend.config.ConfigKey.OAUTH_ALLOWED_DOMAINS);
        if (allowedDomains != null && !allowedDomains.isBlank()) {
            String emailDomain = email.substring(email.indexOf('@') + 1);
            boolean allowed = java.util.Arrays.stream(allowedDomains.split(","))
                    .map(String::trim)
                    .anyMatch(d -> d.equalsIgnoreCase(emailDomain));
            if (!allowed) {
                auditEventPublisher.publishLoginFailed(email, "domain_not_allowed");
                getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=domain_not_allowed");
                return;
            }
        }

        String encryptedAccessToken = tokenEncryptionService.encrypt(accessToken);
        String encryptedRefreshToken = refreshToken != null ? tokenEncryptionService.encrypt(refreshToken) : null;

        java.util.Optional<User> existingOpt = userRepository.findByGoogleId(googleId);

        if (existingOpt.isPresent()) {
            User existing = existingOpt.get();
            if (existing.getDeletedAt() != null || !existing.isActive()) {
                auditEventPublisher.publishLoginFailed(email, "account_disabled");
                getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=account_disabled");
                return;
            }
        }

        User user = existingOpt
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setName(name);
                    existing.setPictureUrl(picture);
                    existing.setGoogleAccessToken(encryptedAccessToken);
                    existing.setGoogleRefreshToken(encryptedRefreshToken);
                    existing.setGoogleAccessTokenExpiresAt(tokenExpiresAt);
                    existing.setLastLoginAt(now);
                    existing.setEmailVerified(emailVerified);
                    return existing;
                })
                .orElseGet(() -> User.builder()
                        .googleId(googleId)
                        .email(email)
                        .name(name)
                        .pictureUrl(picture)
                        .role(Role.TRIAL)
                        .trialEndDate(now.plusDays(30))
                        .googleAccessToken(encryptedAccessToken)
                        .googleRefreshToken(encryptedRefreshToken)
                        .googleAccessTokenExpiresAt(tokenExpiresAt)
                        .accessibilityMode(true)
                        .lastLoginAt(now)
                        .emailVerified(emailVerified)
                        .build());

        user = userRepository.save(user);

        String correlationId = extractCorrelationId(request);
        auditEventPublisher.publishLoginSuccess(user, correlationId);

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
                "athenyx_token", jwt, "/", jwtUtil.getExpirationMs() / 1000, "Lax");
        String refreshCookie = buildSetCookie(
                "athenyx_refresh", issued.raw(), "/", refreshMaxAge, "Lax");

        log.info("OAuth2 success for user={} ({}): writing access cookie + refresh cookie (maxAge={}s)",
                user.getId(), user.getEmail(), refreshMaxAge);
        log.debug("Set-Cookie access: {}", accessCookie);
        log.debug("Set-Cookie refresh: {}", refreshCookie);

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie);

        String redirectUrl = determineRedirectUrl(user, frontendUrl);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String determineRedirectUrl(User user, String frontendUrl) {
        if (user.getTermsAcceptedAt() == null) {
            return frontendUrl + "/legal/terms?from=oauth&next=/home";
        }
        return frontendUrl + "/home";
    }

    private String extractCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        String mdc = MDC.get("correlationId");
        log.debug("extractCorrelationId: request_attr={} mdc={}", attr, mdc);
        if (attr instanceof String s) return s;
        return mdc;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isBlank()) {
            return xForwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String buildSetCookie(String name, String value, String path, long maxAgeSeconds, String sameSite) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(name).append('=').append(value)
                .append("; Path=").append(path)
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly");
        if (cookieSecure) {
            sb.append("; Secure");
        }
        if (sameSite != null && !sameSite.isEmpty()) {
            sb.append("; SameSite=").append(sameSite);
        }
        return sb.toString();
    }
}
