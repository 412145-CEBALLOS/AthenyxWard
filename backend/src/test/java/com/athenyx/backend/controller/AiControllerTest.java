package com.athenyx.backend.controller;

import com.athenyx.backend.ai.AiExplanationService;
import com.athenyx.backend.ai.AiOrigin;
import com.athenyx.backend.ai.AiPremiumRequiredException;
import com.athenyx.backend.dto.AiExplanationResponse;
import com.athenyx.backend.heuristics.TrialLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiController}.
 *
 * <p>Drives the controller directly with a mocked {@link AiExplanationService}
 * and a fake {@link Authentication}. Verifies status codes, response payloads,
 * the {@code CompletionException} unwrapping path for trial-limit, and
 * the {@code @PreAuthorize} annotation.
 */
@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiExplanationService service;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AiController controller;

    // --- Security annotation ---

    @Test
    void explain_endpoint_requiresAuthentication() throws Exception {
        Method m = AiController.class.getMethod("explain", Long.class, Authentication.class);
        PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }

    // --- Happy path: 200 + response body ---

    @Test
    void explain_returns200WithLlmResponse() throws Exception {
        when(authentication.getPrincipal()).thenReturn(1L);
        AiExplanationResponse response = new AiExplanationResponse(
                1L,
                "Resumen del correo.",
                "Explicación heurística.",
                "Segunda opinión.",
                AiOrigin.LLM,
                "llama3",
                LocalDateTime.now());
        when(service.explain(1L, 10L)).thenReturn(CompletableFuture.completedFuture(response));

        ResponseEntity<AiExplanationResponse> result = controller.explain(10L, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().origin()).isEqualTo(AiOrigin.LLM);
        assertThat(result.getBody().modelName()).isEqualTo("llama3");
    }

    // --- Trial limit: CompletionException wraps TrialLimitExceeded → rethrown ---

    @Test
    void explain_throwsTrialLimitExceeded_whenCompletionExceptionWrapsIt() {
        when(authentication.getPrincipal()).thenReturn(1L);
        CompletionException cause = new CompletionException(
                new TrialLimitExceededException("Límite de análisis alcanzado", 0));
        CompletableFuture<AiExplanationResponse> failed = CompletableFuture.failedFuture(cause);
        when(service.explain(1L, 10L)).thenReturn(failed);

        assertThatThrownBy(() -> controller.explain(10L, authentication))
                .isInstanceOf(TrialLimitExceededException.class);
    }

    // --- Premium required: CompletionException wraps AiPremiumRequired → rethrown ---

    @Test
    void explain_throwsAiPremiumRequired_whenCompletionExceptionWrapsIt() {
        when(authentication.getPrincipal()).thenReturn(1L);
        CompletionException cause = new CompletionException(
                new AiPremiumRequiredException("La función \"Explicar con IA\" requiere plan Premium o Admin."));
        CompletableFuture<AiExplanationResponse> failed = CompletableFuture.failedFuture(cause);
        when(service.explain(1L, 10L)).thenReturn(failed);

        assertThatThrownBy(() -> controller.explain(10L, authentication))
                .isInstanceOf(AiPremiumRequiredException.class);
    }

    // --- Generic RuntimeException: unwrapped and rethrown ---

    @Test
    void explain_throwsRuntimeException_whenCauseIsRuntimeException() {
        when(authentication.getPrincipal()).thenReturn(1L);
        CompletionException cause = new CompletionException(
                new RuntimeException("Acceso denegado"));
        CompletableFuture<AiExplanationResponse> failed = CompletableFuture.failedFuture(cause);
        when(service.explain(1L, 10L)).thenReturn(failed);

        assertThatThrownBy(() -> controller.explain(10L, authentication))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Acceso denegado");
    }
}
