package com.athenyx.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

/**
 * CSRF defence for {@code POST /api/auth/refresh}. Rejects requests
 * whose {@code Origin} (preferred) or {@code Referer} host does not
 * match the configured frontend URL. Non-refresh requests pass through
 * untouched.
 *
 * <p>Hosts are compared on host (port-aware) so that running the SPA
 * on a different port than the API is still allowed.</p>
 */
@Component
@RequiredArgsConstructor
public class RefreshOriginFilter extends OncePerRequestFilter {

    private static final String REFRESH_PATH = "/api/auth/refresh";

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !REFRESH_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String allowedHost = hostOf(frontendUrl);
        if (allowedHost == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            if (!allowedHost.equalsIgnoreCase(hostOf(origin))) {
                reject(response, "Origin not allowed");
                return;
            }
        } else {
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()
                    && !allowedHost.equalsIgnoreCase(hostOf(referer))) {
                reject(response, "Referer not allowed");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + reason + "\"}");
    }

    private static String hostOf(String url) {
        if (url == null) return null;
        try {
            String trimmed = url.trim();
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "http://" + trimmed;
            }
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null) return null;
            if (port > 0 && port != defaultPort(uri.getScheme())) {
                return host + ":" + port;
            }
            return host;
        } catch (Exception e) {
            return null;
        }
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
