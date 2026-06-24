package com.athenyx.backend.controller;

import com.athenyx.backend.dto.CreateReminderRequest;
import com.athenyx.backend.dto.ReminderResponse;
import com.athenyx.backend.dto.ReminderSummary;
import com.athenyx.backend.dto.RemindersListResponse;
import com.athenyx.backend.dto.UpdateReminderRequest;
import com.athenyx.backend.service.reminder.ReminderService;
import com.athenyx.backend.service.reminder.ReminderService.Filter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints exposing the reminder CRUD to the authenticated
 * user. Base path: {@code /api/reminders}.
 *
 * <ul>
 *     <li>{@code POST /} — PREMIUM/ADMIN only (enforced both by
 *         {@code @PreAuthorize} and the service layer).</li>
 *     <li>{@code PATCH /{id}}, {@code DELETE /{id}} — any
 *         authenticated user; ownership is verified in the
 *         service.</li>
 *     <li>{@code DELETE /completed} — bulk-removes every done
 *         reminder for the caller. Any authenticated user.</li>
 *     <li>{@code GET /} — any authenticated user; TRIAL users get
 *         an empty list (no 403) so the SPA can render the upsell
 *         state uniformly.</li>
 *     <li>{@code GET /by-email/{emailId}} — used by the email
 *         viewer to check whether a reminder is configured.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('PREMIUM', 'ADMIN')")
    public ResponseEntity<ReminderResponse> create(
            @Valid @RequestBody CreateReminderRequest body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.create(userId, body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReminderRequest body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(service.update(userId, id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/completed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Integer>> clearCompleted(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int deleted = service.clearCompleted(userId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RemindersListResponse> list(
            @RequestParam(name = "filter", required = false, defaultValue = "all") String filter,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<ReminderResponse> items = service.findByUser(userId, Filter.parse(filter));
        return ResponseEntity.ok(new RemindersListResponse(items));
    }

    @GetMapping("/by-email/{emailId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReminderSummary> getByEmail(
            @PathVariable Long emailId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ReminderSummary summary = service.findSummaryByEmail(userId, emailId);
        return ResponseEntity.ok(summary);
    }
}
