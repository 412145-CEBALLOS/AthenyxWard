package com.athenyx.backend.controller;

import com.athenyx.backend.dto.HeuristicAnalysisResponse;
import com.athenyx.backend.heuristics.HeuristicAnalysisService;
import com.athenyx.backend.heuristics.TrialLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final HeuristicAnalysisService service;

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
}
