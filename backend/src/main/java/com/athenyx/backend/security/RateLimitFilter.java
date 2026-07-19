package com.athenyx.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final SecurityUtils securityUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String endpoint = classifyEndpoint(path);
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Long> userId = securityUtils.getCurrentUserId();
        if (userId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        OptionalLong retryAfter = rateLimiter.tryAcquire(userId.get(), endpoint);
        if (retryAfter.isPresent()) {
            long seconds = Math.max(1, retryAfter.getAsLong());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(seconds));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"retryAfter\":" + seconds + "}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String classifyEndpoint(String path) {
        if (path.contains("/analyze")) return "analysis";
        if (path.contains("/explain")) return "explain";
        return null;
    }
}
