package com.athenyx.backend.controller;

import com.athenyx.backend.dto.AdminStatsResponse;
import com.athenyx.backend.dto.StatsPeriod;
import com.athenyx.backend.dto.UserStatsResponse;
import com.athenyx.backend.service.stats.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserStatsResponse> getUserStats(
            @RequestParam(defaultValue = "week") String period,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        StatsPeriod p = StatsPeriod.from(period);
        return ResponseEntity.ok(statsService.getUserStats(userId, p));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminStatsResponse> getAdminStats(
            @RequestParam(defaultValue = "week") String period) {
        StatsPeriod p = StatsPeriod.from(period);
        return ResponseEntity.ok(statsService.getAdminStats(p));
    }
}
