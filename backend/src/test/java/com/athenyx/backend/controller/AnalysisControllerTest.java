package com.athenyx.backend.controller;

import com.athenyx.backend.dto.HeuristicAnalysisResponse;
import com.athenyx.backend.heuristics.HeuristicAnalysisService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnalysisController}.
 *
 * <p>Drives the controller methods directly with mocked
 * {@link HeuristicAnalysisService} and a fake {@link Authentication}.
 * Verifies status codes, response payloads, security annotations and
 * the trial-limit propagation path introduced in US 2.8.</p>
 */
@ExtendWith(MockitoExtension.class)
class AnalysisControllerTest {

    @Mock
    private HeuristicAnalysisService service;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AnalysisController controller;

    // --- Security annotations ---

    @Test
    void analyze_endpoint_isAuthenticatedOnly() throws Exception {
        Method m = AnalysisController.class.getMethod("analyze", Long.class, Authentication.class);
        PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void getLatest_endpoint_isAuthenticatedOnly() throws Exception {
        Method m = AnalysisController.class.getMethod("getLatest", Long.class, Authentication.class);
        PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void trialLimit_endpoint_isAuthenticatedOnly() throws Exception {
        Method m = AnalysisController.class.getMethod("trialLimit", Authentication.class);
        PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }

    // --- Happy path: analyze ---

    @Test
    void analyze_returns200WithResponseBody() {
        when(authentication.getPrincipal()).thenReturn(1L);
        HeuristicAnalysisResponse response = buildResponse(42, com.athenyx.backend.heuristics.ThreatLevel.YELLOW);
        when(service.analyze(1L, 10L)).thenReturn(CompletableFuture.completedFuture(response));

        ResponseEntity<HeuristicAnalysisResponse> result = controller.analyze(10L, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().riskPercentage()).isEqualTo(42);
        assertThat(result.getBody().riskLevel()).isEqualTo(com.athenyx.backend.heuristics.ThreatLevel.YELLOW);
    }

    // --- Trial limit: 403 propagation ---

    @Test
    void analyze_returns403WhenTrialLimitExceeded() {
        when(authentication.getPrincipal()).thenReturn(1L);
        CompletableFuture<HeuristicAnalysisResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new TrialLimitExceededException("Límite de análisis alcanzado", 0));
        when(service.analyze(1L, 10L)).thenReturn(failed);

        ResponseEntity<HeuristicAnalysisResponse> result = controller.analyze(10L, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody()).isNull();
    }

    // --- getLatest: 200 / 404 ---

    @Test
    void getLatest_returns200WhenAnalysisExists() {
        when(authentication.getPrincipal()).thenReturn(1L);
        HeuristicAnalysisResponse response = buildResponse(15, com.athenyx.backend.heuristics.ThreatLevel.GREEN);
        when(service.getLatest(1L, 10L)).thenReturn(Optional.of(response));

        ResponseEntity<HeuristicAnalysisResponse> result = controller.getLatest(10L, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().riskPercentage()).isEqualTo(15);
    }

    @Test
    void getLatest_returns404WhenNoAnalysis() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(service.getLatest(1L, 10L)).thenReturn(Optional.empty());

        ResponseEntity<HeuristicAnalysisResponse> result = controller.getLatest(10L, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
    }

    // --- trialLimit: 200 ---

    @Test
    void trialLimit_returnsRemainingAndLimit() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(service.getTrialRemaining(1L)).thenReturn(7);

        ResponseEntity<Map<String, Object>> result = controller.trialLimit(authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("remaining", 7);
        assertThat(result.getBody()).containsEntry("limit", 20);
    }

    @Test
    void trialLimit_returnsMaxForNonTrialUser() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(service.getTrialRemaining(1L)).thenReturn(Integer.MAX_VALUE);

        ResponseEntity<Map<String, Object>> result = controller.trialLimit(authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("remaining", Integer.MAX_VALUE);
    }

    private HeuristicAnalysisResponse buildResponse(int pct, com.athenyx.backend.heuristics.ThreatLevel level) {
        return new HeuristicAnalysisResponse(
            1L, 10L, pct, level,
            List.of(), List.of(), List.of(),
            null, "", "", List.of(),
            LocalDateTime.now(), com.athenyx.backend.heuristics.AnalysisOrigin.HEURISTIC, null
        );
    }
}
