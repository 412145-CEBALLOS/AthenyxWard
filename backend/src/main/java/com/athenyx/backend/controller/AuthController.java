package com.athenyx.backend.controller;

import com.athenyx.backend.dto.RefreshResponse;
import com.athenyx.backend.dto.UpdateAccessibilityModeRequest;
import com.athenyx.backend.dto.UserInfo;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.security.RefreshCookieManager;
import com.athenyx.backend.security.RefreshTokenException;
import com.athenyx.backend.service.AuthService;
import com.athenyx.backend.service.RefreshTokenService;
import com.athenyx.backend.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for authentication, session refresh and logout.
 *
 * <p>Base path: {@code /api/auth}. All endpoints under this controller are
 * marked public in {@link com.athenyx.backend.config.SecurityConfig}; the
 * JWT filter also short-circuits for {@code /refresh}, {@code /logout} and
 * {@code /login-url} so they can run unauthenticated.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieManager refreshCookieManager;
    private final JwtUtil jwtUtil;

    @GetMapping("/me")
    public ResponseEntity<UserInfo> getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getUserInfo(userId));
    }

    @PutMapping("/me/accessibility-mode")
    public ResponseEntity<UserInfo> updateAccessibilityMode(
            Authentication authentication,
            @Valid @RequestBody UpdateAccessibilityModeRequest body) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.updateAccessibilityMode(userId, body.accessibilityMode()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request,
                                                   HttpServletResponse response) {
        if (request.getCookies() != null) {
            log.debug("/api/auth/refresh received cookies: {}",
                    java.util.Arrays.stream(request.getCookies())
                            .map(c -> c.getName() + "=" + (c.getValue() != null ? "[present]" : "[empty]"))
                            .reduce((a, b) -> a + "; " + b)
                            .orElse("none"));
        } else {
            log.debug("/api/auth/refresh received no cookies");
        }

        String rawRefresh = readRefreshCookie(request);
        if (rawRefresh == null) {
            throw new RefreshTokenException(RefreshTokenException.Kind.MISSING, "Refresh cookie missing");
        }

        User user = refreshTokenService.resolveUserForRotation(rawRefresh);

        RefreshTokenService.IssuedToken issued = refreshTokenService.rotate(rawRefresh, user, request);

        long ttlMs = jwtUtil.getExpirationMs();
        String newAccess = jwtUtil.generateToken(
                user.getId(), user.getEmail(), user.getRole().name(), issued.newTokenVersion(), ttlMs);

        long maxAgeSeconds = java.time.Duration.between(
                issued.row().getIssuedAt(),
                issued.row().getAbsoluteExpiresAt()).toSeconds();

        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookieManager.build(issued.raw(), maxAgeSeconds).toString());

        Cookie accessCookie = new Cookie("athenyx_token", newAccess);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(false);
        accessCookie.setPath("/");
        accessCookie.setMaxAge((int) (ttlMs / 1000));
        response.addCookie(accessCookie);

        log.info("Refresh success for user={} newTokenVersion={}", user.getId(), issued.newTokenVersion());

        return ResponseEntity.ok(new RefreshResponse(newAccess, ttlMs / 1000));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request,
                                                     HttpServletResponse response) {
        refreshTokenService.revoke(readRefreshCookie(request));

        Cookie cookie = new Cookie("athenyx_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieManager.clear().toString());

        return ResponseEntity.ok(Map.of("message", "Sesi\u00f3n cerrada"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, Object>> logoutAll(HttpServletRequest request,
                                                         HttpServletResponse response) {
        String rawRefresh = readRefreshCookie(request);
        int revoked = 0;
        if (rawRefresh != null && !rawRefresh.isBlank()) {
            try {
                User user = refreshTokenService.resolveUserForRotation(rawRefresh);
                revoked = refreshTokenService.revokeAllForUser(user.getId());
            } catch (RefreshTokenException ignored) {
            }
        }

        Cookie cookie = new Cookie("athenyx_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieManager.clear().toString());

        return ResponseEntity.ok(Map.of(
                "message", "Sesi\u00f3n cerrada en todos los dispositivos",
                "revoked", revoked));
    }

    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> getLoginUrl() {
        return ResponseEntity.ok(Map.of("url", "/oauth2/authorization/google"));
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        String name = refreshCookieManager.getCookieName();
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
