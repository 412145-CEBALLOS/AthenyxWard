package com.athenyx.backend.ai;

/**
 * Thrown when a TRIAL user attempts to use the AI explanation endpoint
 * (US 3.2 / 3.3). The action is gated to PREMIUM/ADMIN at both the
 * controller level (via {@code @PreAuthorize}) and the service as a
 * safety net. Mapped to HTTP 403 with body {error: "ai_premium_required"}
 * so the SPA can surface a clear upsell instead of the generic
 * "Acceso denegado".
 */
public class AiPremiumRequiredException extends RuntimeException {
    public AiPremiumRequiredException(String message) {
        super(message);
    }
}
