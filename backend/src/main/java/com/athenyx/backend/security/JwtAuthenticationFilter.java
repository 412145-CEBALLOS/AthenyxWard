package com.athenyx.backend.security;

import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * {@link OncePerRequestFilter} that turns a JWT (header or cookie) into a
 * Spring Security {@link Authentication} on the
 * {@link SecurityContextHolder}.
 *
 * <p>Behaviour:
 * <ul>
 *     <li>Skips public auth endpoints so {@code /api/auth/refresh} and
 *         friends can run unauthenticated.</li>
 *     <li>Reads the token from {@code Authorization: Bearer …} first,
 *         then from the {@code athenyx_token} cookie.</li>
 *     <li>On invalid signature/expiry writes a 401 JSON body and
 *         short-circuits the chain.</li>
 *     <li>On a stale {@code tokenVersion} (i.e. the user has since
 *         logged in elsewhere) also writes 401 with a
 *         {@code "Token revoked"} message.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    private static final List<String> JWT_SKIP_PATHS = List.of(
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/login-url"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isPublicAuthPath = path != null && JWT_SKIP_PATHS.contains(path);

        String token = extractToken(request);

        if (token != null && !isPublicAuthPath) {
            if (!jwtUtil.isTokenValid(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token expired or invalid\"}");
                return;
            }

            Long userId = jwtUtil.getUserId(token);
            String email = jwtUtil.getEmail(token);
            String role = jwtUtil.getRole(token);
            Long tokenVersion = jwtUtil.getTokenVersion(token);

            Optional<Long> currentVersion = userRepository.findTokenVersionById(userId);
            if (currentVersion.isEmpty() || !currentVersion.get().equals(tokenVersion)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token revoked\"}");
                return;
            }

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authentication.setDetails(email);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("athenyx_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
