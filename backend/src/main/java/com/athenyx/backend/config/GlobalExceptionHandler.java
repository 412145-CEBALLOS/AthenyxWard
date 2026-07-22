package com.athenyx.backend.config;

import com.athenyx.backend.ai.AiPremiumRequiredException;
import com.athenyx.backend.ai.AiUnavailableException;
import com.athenyx.backend.exception.CheckoutAlreadyCompletedException;
import com.athenyx.backend.exception.CheckoutAlreadyPremiumException;
import com.athenyx.backend.exception.CheckoutInvalidProviderException;
import com.athenyx.backend.exception.CheckoutNotFoundException;
import com.athenyx.backend.exception.CheckoutNotPendingException;
import com.athenyx.backend.exception.CheckoutPaymentExpiredException;
import com.athenyx.backend.exception.CheckoutPaymentFailedException;
import com.athenyx.backend.exception.MercadoPagoApiException;
import com.athenyx.backend.heuristics.TrialLimitExceededException;
import com.athenyx.backend.payment.MpApiException;
import com.athenyx.backend.security.FeatureDisabledException;
import com.athenyx.backend.security.RefreshTokenException;
import com.athenyx.backend.service.AdminUserService;
import com.athenyx.backend.service.reminder.ReminderConflictException;
import com.athenyx.backend.service.reminder.ReminderNotFoundException;
import com.athenyx.backend.service.reminder.ReminderPremiumRequiredException;
import com.athenyx.backend.service.reminder.ReminderQuotaExceededException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

import java.util.Map;

/**
 * Centralised {@link RestControllerAdvice} that translates exceptions into
 * stable, Spanish-language JSON error responses.
 *
 * <p>Mappings:
 * <ul>
 *     <li>{@link AuthenticationException} → 401 {@code "No autenticado"}.</li>
 *     <li>{@link AccessDeniedException} → 403 {@code "Acceso denegado"}.</li>
 *     <li>{@link com.athenyx.backend.security.RefreshTokenException} → 401
 *         with the exception message (used to surface specific refresh
 *         failure reasons to the SPA).</li>
 *     <li>{@link LazyInitializationException} → 500 — these indicate a
 *         server-side bug (JPA associations accessed outside a
 *         transaction), not a client error.</li>
 *     <li>Any other {@link RuntimeException} whose message mentions
 *         {@code token} / {@code auth} → 401; everything else → 400.</li>
 *     <li>Any other {@link Exception} → 500 {@code "Error interno del servidor"}.</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "No autenticado"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Acceso denegado"));
    }

    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<Map<String, String>> handleRefreshTokenException(RefreshTokenException ex) {
        if (ex.getKind() == RefreshTokenException.Kind.ACCOUNT_DISABLED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "ACCOUNT_DISABLED"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TrialLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTrialLimitExceeded(TrialLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage(), "remaining", ex.remaining(), "limit", 20));
    }

    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleAiUnavailable(AiUnavailableException ex) {
        log.warn("AI unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "IA no disponible"));
    }

    @ExceptionHandler(AiPremiumRequiredException.class)
    public ResponseEntity<Map<String, String>> handleAiPremiumRequired(AiPremiumRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "ai_premium_required"));
    }

    @ExceptionHandler(ReminderPremiumRequiredException.class)
    public ResponseEntity<Map<String, String>> handleReminderPremiumRequired(ReminderPremiumRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReminderConflictException.class)
    public ResponseEntity<Map<String, String>> handleReminderConflict(ReminderConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReminderQuotaExceededException.class)
    public ResponseEntity<Map<String, String>> handleReminderQuotaExceeded(ReminderQuotaExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReminderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleReminderNotFound(ReminderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AdminUserService.UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(AdminUserService.UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AdminUserService.AdminSelfOperationException.class)
    public ResponseEntity<Map<String, String>> handleAdminSelfOperation(AdminUserService.AdminSelfOperationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getCode()));
    }

    @ExceptionHandler(ConfigValidationException.class)
    public ResponseEntity<Map<String, String>> handleConfigValidation(ConfigValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleConfigNotFound(ConfigNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.exception.CheckoutAlreadyPremiumException.class)
    public ResponseEntity<Map<String, String>> handleCheckoutAlreadyPremium(com.athenyx.backend.exception.CheckoutAlreadyPremiumException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.exception.CheckoutNotPendingException.class)
    public ResponseEntity<Map<String, String>> handleCheckoutNotPending(com.athenyx.backend.exception.CheckoutNotPendingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.exception.CheckoutAlreadyCompletedException.class)
    public ResponseEntity<Map<String, String>> handleCheckoutAlreadyCompleted(com.athenyx.backend.exception.CheckoutAlreadyCompletedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.exception.MercadoPagoApiException.class)
    public ResponseEntity<Map<String, String>> handleMercadoPagoApi(com.athenyx.backend.exception.MercadoPagoApiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.exception.CheckoutInvalidProviderException.class)
    public ResponseEntity<Map<String, String>> handleCheckoutInvalidProvider(com.athenyx.backend.exception.CheckoutInvalidProviderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.exception.CheckoutPaymentExpiredException.class)
    public ResponseEntity<Map<String, String>> handleCheckoutPaymentExpired(com.athenyx.backend.exception.CheckoutPaymentExpiredException ex) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.exception.CheckoutPaymentFailedException.class)
    public ResponseEntity<Map<String, String>> handleCheckoutPaymentFailed(com.athenyx.backend.exception.CheckoutPaymentFailedException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.exception.CheckoutNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCheckoutNotFound(com.athenyx.backend.exception.CheckoutNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.athenyx.backend.security.FeatureDisabledException.class)
    public ResponseEntity<Map<String, String>> handleFeatureDisabled(com.athenyx.backend.security.FeatureDisabledException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MpApiException.class)
    public ResponseEntity<Map<String, String>> handleMpApiException(MpApiException ex) {
        log.warn("[GlobalExceptionHandler] MpApiException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Error al consultar la API de MercadoPago"));
    }

    @ExceptionHandler(ClassCastException.class)
    public ResponseEntity<Map<String, String>> handleClassCastException(ClassCastException ex) {
        log.error("ClassCastException (likely a backend bug)", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno del servidor"));
    }

    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<Map<String, String>> handleLazyInitializationException(LazyInitializationException ex) {
        log.error("Lazy initialization error (server-side bug, not a client error)", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();
        if (message != null && (message.contains("token") || message.contains("Token") || message.contains("JWT") || message.contains("auth"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", message));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message != null ? message : "Error desconocido"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno del servidor"));
    }
}
