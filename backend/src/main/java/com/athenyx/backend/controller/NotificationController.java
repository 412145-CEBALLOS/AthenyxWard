package com.athenyx.backend.controller;

import com.athenyx.backend.dto.UpcomingReminderNotification;
import com.athenyx.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints powering the bell-icon notification panel and the
 * "your reminder just fired" toasts (US 2.7). Base path:
 * {@code /api/notifications}.
 *
 * <p>All endpoints are gated to PREMIUM/ADMIN — the SPA hides the
 * bell for TRIAL users.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping("/upcoming")
    @PreAuthorize("hasAnyRole('PREMIUM', 'ADMIN')")
    public ResponseEntity<List<UpcomingReminderNotification>> getUpcoming(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(service.getUpcomingReminders(userId));
    }
}
