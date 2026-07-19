package com.athenyx.backend.security;

import com.athenyx.backend.audit.AuditEventPublisher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * {@link SimpleUrlAuthenticationFailureHandler} that records every OAuth2
 * login failure to the audit log before redirecting to the error page.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AuditEventPublisher auditEventPublisher;
    private final LoginAttemptService loginAttemptService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationFailure(HttpServletRequest request,
                                     HttpServletResponse response,
                                     AuthenticationException exception) throws IOException, ServletException {
        String email = request.getParameter("email");
        String ip = getClientIp(request);
        loginAttemptService.recordFailedAttempt(email != null ? email : "unknown", ip);
        auditEventPublisher.publishLoginFailed(email, exception.getMessage());
        log.debug("OAuth2 login failure for attempted email={}: {}", email, exception.getMessage());
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=1");
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isBlank()) {
            return xForwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
