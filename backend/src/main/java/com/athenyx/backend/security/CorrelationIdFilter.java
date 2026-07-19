package com.athenyx.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * {@link OncePerRequestFilter} that guarantees every incoming HTTP request
 * carries a {@code correlationId} for end-to-end tracing.
 *
 * <ul>
 *   <li>If the request contains the header {@code X-Correlation-Id}, that
 *       value is used verbatim.</li>
 *   <li>Otherwise a {@link UUID#randomUUID()} is generated.</li>
 * </ul>
 *
 * <p>The value is stored in:
 * <ul>
 *   <li>{@link MDC} — propagates to all log statements in the request thread.</li>
 *   <li>{@code request.setAttribute} — read by {@link AuditContext} when
 *       building {@code AuditLog} rows.</li>
 * </ul>
 *
 * <p>The MDC is cleared in {@code finally} so that threads returned to the
 * pool do not leak context to unrelated requests.
 */
@Component
@Order(0)
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);

        log.debug("CorrelationIdFilter ran path={} correlationId={}", request.getRequestURI(), correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
