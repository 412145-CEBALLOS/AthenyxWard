package com.athenyx.backend.controller;

import com.athenyx.backend.dto.AiExplanationResponse;
import com.athenyx.backend.heuristics.TrialLimitExceededException;
import com.athenyx.backend.ai.AiExplanationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiController {

    private final AiExplanationService service;

    @PostMapping("/emails/{id}/explain")
    @PreAuthorize("hasAnyRole('PREMIUM', 'ADMIN')")
    public ResponseEntity<AiExplanationResponse> explain(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            AiExplanationResponse response = service.explain(userId, id).join();
            return ResponseEntity.ok(response);
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TrialLimitExceededException) {
                throw (TrialLimitExceededException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw e;
        }
    }
}
