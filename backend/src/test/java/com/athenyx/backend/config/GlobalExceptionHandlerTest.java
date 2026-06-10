package com.athenyx.backend.config;

import com.athenyx.backend.security.RefreshTokenException;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsAuthenticationException_to401_withSpanishMessage() {
        ResponseEntity<Map<String, String>> res = handler.handleAuthenticationException(
                new BadCredentialsException("nope"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).containsEntry("error", "No autenticado");
    }

    @Test
    void mapsAccessDenied_to403() {
        ResponseEntity<Map<String, String>> res = handler.handleAccessDeniedException(
                new AccessDeniedException("blocked"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).containsEntry("error", "Acceso denegado");
    }

    @Test
    void mapsRefreshTokenException_to401_withExceptionMessage() {
        ResponseEntity<Map<String, String>> res = handler.handleRefreshTokenException(
                new RefreshTokenException(RefreshTokenException.Kind.REUSE_DETECTED, "reused"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).containsEntry("error", "reused");
    }

    @Test
    void mapsLazyInitException_to500() {
        ResponseEntity<Map<String, String>> res = handler.handleLazyInitializationException(
                new LazyInitializationException("oops"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).containsEntry("error", "Internal server error");
    }

    @Test
    void mapsRuntimeExceptionMentioningToken_to401() {
        ResponseEntity<Map<String, String>> res = handler.handleRuntimeException(
                new RuntimeException("token expired"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).containsEntry("error", "token expired");
    }

    @Test
    void mapsRuntimeExceptionMentioningAuth_to401() {
        ResponseEntity<Map<String, String>> res = handler.handleRuntimeException(
                new RuntimeException("auth required"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void mapsPlainRuntimeException_to400() {
        ResponseEntity<Map<String, String>> res = handler.handleRuntimeException(
                new RuntimeException("some validation error"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("error", "some validation error");
    }

    @Test
    void mapsNullMessageRuntimeException_toBadRequestFallback() {
        ResponseEntity<Map<String, String>> res = handler.handleRuntimeException(
                new RuntimeException());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("error", "Error desconocido");
    }

    @Test
    void mapsAnyException_to500() {
        ResponseEntity<Map<String, String>> res = handler.handleException(
                new Exception("boom"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).containsEntry("error", "Error interno del servidor");
    }
}
