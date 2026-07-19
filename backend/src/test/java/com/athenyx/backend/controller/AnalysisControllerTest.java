package com.athenyx.backend.controller;

import com.athenyx.backend.dto.AnalysisHistoryResponse;
import com.athenyx.backend.dto.AnalysisHistoryResponse.AnalysisHistoryItem;
import com.athenyx.backend.dto.HeuristicAnalysisResponse;
import com.athenyx.backend.heuristics.HeuristicAnalysisService;
import com.athenyx.backend.heuristics.TrialLimitExceededException;
import com.athenyx.backend.service.AnalysisHistoryService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private AnalysisHistoryService historyService;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AnalysisController controller;

    // --- Security annotations ---

    @Test
    void analyze_endpoint_isAuthenticatedOnly() throws Exception {
        Method m = AnalysisController.class.getMethod("analyze", Long.class, Authentication.class, HttpServletRequest.class);
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

    @Test
    void getHistory_endpoint_isAuthenticatedOnly() throws Exception {
        Method m = AnalysisController.class.getMethod("getHistory",
            LocalDate.class, LocalDate.class, int.class, int.class, Authentication.class);
        PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }

    // --- Happy path: analyze ---

    @Test
    void analyze_returns200WithResponseBody() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(authentication.getDetails()).thenReturn("u@test.com");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestBrowser/1.0");
        when(request.getAttribute("correlationId")).thenReturn("corr-123");
        HeuristicAnalysisResponse response = buildResponse(42, com.athenyx.backend.heuristics.ThreatLevel.YELLOW);
        when(service.analyze(eq(1L), eq(10L), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        ResponseEntity<HeuristicAnalysisResponse> result = controller.analyze(10L, authentication, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().riskPercentage()).isEqualTo(42);
        assertThat(result.getBody().riskLevel()).isEqualTo(com.athenyx.backend.heuristics.ThreatLevel.YELLOW);
    }

    // --- Trial limit: 403 propagation ---

    @Test
    void analyze_returns403WhenTrialLimitExceeded() {
        when(authentication.getPrincipal()).thenReturn(1L);
        when(authentication.getDetails()).thenReturn("u@test.com");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("TestBrowser/1.0");
        when(request.getAttribute("correlationId")).thenReturn("corr-123");
        CompletableFuture<HeuristicAnalysisResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new TrialLimitExceededException("Límite de análisis alcanzado", 0));
        when(service.analyze(eq(1L), eq(10L), any(), any(), any())).thenReturn(failed);

        ResponseEntity<HeuristicAnalysisResponse> result = controller.analyze(10L, authentication, request);

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

    // --- getHistory: 200 / empty list ---

    @Test
    void getHistory_returns200WithResponseBody() {
        when(authentication.getPrincipal()).thenReturn(1L);
        AnalysisHistoryItem item = new AnalysisHistoryItem(
            99L, 10L, "a@b.com", "S", 87, "RED",
            LocalDateTime.of(2026, 6, 8, 9, 14), "Resumen");
        AnalysisHistoryResponse response = new AnalysisHistoryResponse(
            List.of(item), 0, 1, 1);
        when(historyService.getHistory(eq(1L), isNull(), isNull(), eq(0), eq(20)))
            .thenReturn(response);

        ResponseEntity<AnalysisHistoryResponse> result = controller.getHistory(
            null, null, 0, 20, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().items()).hasSize(1);
        assertThat(result.getBody().items().get(0).analysisId()).isEqualTo(99L);
        assertThat(result.getBody().items().get(0).summary()).isEqualTo("Resumen");
        assertThat(result.getBody().totalItems()).isEqualTo(1);
    }

    @Test
    void getHistory_returnsEmptyListWhenNoAnalyses() {
        when(authentication.getPrincipal()).thenReturn(1L);
        AnalysisHistoryResponse response = new AnalysisHistoryResponse(
            List.of(), 0, 0, 0);
        when(historyService.getHistory(eq(1L), isNull(), isNull(), eq(0), eq(20)))
            .thenReturn(response);

        ResponseEntity<AnalysisHistoryResponse> result = controller.getHistory(
            null, null, 0, 20, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().items()).isEmpty();
        assertThat(result.getBody().totalItems()).isZero();
    }

    @Test
    void getHistory_convertsFromDateToStartOfDay() {
        when(authentication.getPrincipal()).thenReturn(1L);
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        when(historyService.getHistory(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(20)))
            .thenReturn(new AnalysisHistoryResponse(List.of(), 0, 0, 0));

        controller.getHistory(from, to, 0, 20, authentication);

        org.mockito.Mockito.verify(historyService).getHistory(
            eq(1L),
            eq(from.atStartOfDay()),
            eq(to.atTime(LocalTime.MAX)),
            eq(0),
            eq(20));
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
