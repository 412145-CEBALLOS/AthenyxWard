package com.athenyx.backend.config;

import com.athenyx.backend.heuristics.TrialLimitExceededException;
import com.athenyx.backend.security.RefreshTokenException;
import com.athenyx.backend.service.reminder.ReminderConflictException;
import com.athenyx.backend.service.reminder.ReminderNotFoundException;
import com.athenyx.backend.service.reminder.ReminderPremiumRequiredException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TrialLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTrialLimitExceeded(TrialLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage(), "remaining", ex.remaining(), "limit", 20));
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

    @ExceptionHandler(ReminderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleReminderNotFound(ReminderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
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
