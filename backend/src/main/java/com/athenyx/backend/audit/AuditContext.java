package com.athenyx.backend.audit;

import com.athenyx.backend.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Helper that extracts request-bound context (IP, User-Agent, correlationId)
 * for {@code AuditLog} rows.
 */
@Component
public class AuditContext {

    public String ipAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;

        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    public String userAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;

        String ua = request.getHeader("User-Agent");
        return ua != null && ua.length() > 512 ? ua.substring(0, 512) : ua;
    }

    public String correlationId() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            Object attr = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
            if (attr instanceof String s) return s;
        }
        return MDC.get("correlationId");
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
