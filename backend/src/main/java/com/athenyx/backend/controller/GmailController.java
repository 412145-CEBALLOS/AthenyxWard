package com.athenyx.backend.controller;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.dto.EmailPageResponse;
import com.athenyx.backend.dto.EmailDetail;
import com.athenyx.backend.dto.EmailSummary;
import com.athenyx.backend.dto.EmailImportantToggleResponse;
import com.athenyx.backend.dto.EmailDeleteResponse;
import com.athenyx.backend.dto.EmailHideResponse;
import com.athenyx.backend.gmail.GmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints exposing Gmail data to the authenticated user.
 *
 * <p>Base path: {@code /api/emails}. Endpoints require a valid JWT (the
 * principal is treated as the user id).</p>
 */
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class GmailController {

    private final GmailService gmailService;
    private final AuditEventPublisher auditEventPublisher;

    @GetMapping("/fetch")
    public ResponseEntity<EmailPageResponse> fetchEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(gmailService.searchEmails(userId, page, q, size));
    }

    @GetMapping("/{emailId}")
    public ResponseEntity<EmailDetail> getEmailDetail(
            @PathVariable Long emailId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(gmailService.getEmailDetail(userId, emailId));
    }

    @GetMapping("/important")
    public ResponseEntity<List<EmailSummary>> getImportantEmails(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(gmailService.getImportantEmails(userId));
    }

    @GetMapping("/important/count")
    public ResponseEntity<Map<String, Long>> getImportantEmailCount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("count", gmailService.getImportantEmailCount(userId)));
    }

    @GetMapping("/hidden")
    public ResponseEntity<List<EmailSummary>> getHiddenEmails(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(gmailService.getHiddenEmails(userId));
    }

    @GetMapping("/hidden/count")
    public ResponseEntity<Map<String, Long>> getHiddenEmailCount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("count", gmailService.getHiddenEmailCount(userId)));
    }

    @PostMapping("/{id}/important")
    @PreAuthorize("hasAnyRole('PREMIUM', 'ADMIN')")
    @Transactional
    public ResponseEntity<EmailImportantToggleResponse> toggleImportant(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String actorEmail = (String) authentication.getDetails();
        EmailImportantToggleResponse result = gmailService.toggleImportant(userId, id);
        auditEventPublisher.publishEmailMarkedImportant(userId, actorEmail, id, result.isImportant());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/hide")
    @PreAuthorize("hasAnyRole('PREMIUM', 'ADMIN')")
    @Transactional
    public ResponseEntity<EmailHideResponse> hideEmail(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String actorEmail = (String) authentication.getDetails();
        EmailHideResponse result = gmailService.hide(userId, id);
        auditEventPublisher.publishEmailHidden(userId, actorEmail, id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/unhide")
    @PreAuthorize("hasAnyRole('PREMIUM', 'ADMIN')")
    @Transactional
    public ResponseEntity<EmailHideResponse> unhideEmail(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String actorEmail = (String) authentication.getDetails();
        EmailHideResponse result = gmailService.unhide(userId, id);
        auditEventPublisher.publishEmailUnhidden(userId, actorEmail, id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public ResponseEntity<EmailDeleteResponse> deleteEmail(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String actorEmail = (String) authentication.getDetails();
        EmailDeleteResponse result = gmailService.softDelete(userId, id);
        auditEventPublisher.publishEmailDeleted(userId, actorEmail, id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/deleted")
    public ResponseEntity<List<EmailSummary>> getDeletedEmails(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(gmailService.getDeletedEmails(userId));
    }

    @GetMapping("/deleted/count")
    public ResponseEntity<Map<String, Long>> getDeletedEmailCount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("count", gmailService.getDeletedEmailCount(userId)));
    }
}
