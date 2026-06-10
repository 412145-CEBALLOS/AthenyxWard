package com.athenyx.backend.controller;

import com.athenyx.backend.dto.EmailPageResponse;
import com.athenyx.backend.dto.EmailDetail;
import com.athenyx.backend.gmail.GmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/fetch")
    public ResponseEntity<EmailPageResponse> fetchEmails(
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(gmailService.fetchEmails(userId, page));
    }

    @GetMapping("/{emailId}")
    public ResponseEntity<EmailDetail> getEmailDetail(
            @PathVariable Long emailId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(gmailService.getEmailDetail(userId, emailId));
    }
}
