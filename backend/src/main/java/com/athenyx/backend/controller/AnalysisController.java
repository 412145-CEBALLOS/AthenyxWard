package com.athenyx.backend.controller;

import com.athenyx.backend.dto.AnalysisHistoryResponse;
import com.athenyx.backend.dto.HeuristicAnalysisResponse;
import com.athenyx.backend.heuristics.HeuristicAnalysisService;
import com.athenyx.backend.heuristics.TrialLimitExceededException;
import com.athenyx.backend.service.AnalysisHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final HeuristicAnalysisService service;
    private final AnalysisHistoryService historyService;

    @PostMapping("/emails/{id}/analyze")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HeuristicAnalysisResponse> analyze(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            HeuristicAnalysisResponse response = service.analyze(userId, id).join();
            return ResponseEntity.ok(response);
        } catch (TrialLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(null);
        } catch (Exception e) {
            if (e.getCause() instanceof TrialLimitExceededException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            throw e;
        }
    }

    @GetMapping("/emails/{id}/analysis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HeuristicAnalysisResponse> getLatest(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return service.getLatest(userId, id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/analysis/trial-limit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> trialLimit(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int remaining = service.getTrialRemaining(userId);
        return ResponseEntity.ok(Map.of(
            "remaining", remaining,
            "limit", 20
        ));
    }

    @GetMapping("/analysis/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AnalysisHistoryResponse> getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LocalDateTime fromDt = (from == null) ? null : from.atStartOfDay();
        LocalDateTime toDt   = (to   == null) ? null : to.atTime(LocalTime.MAX);
        return ResponseEntity.ok(historyService.getHistory(userId, fromDt, toDt, page, size));
    }
}
