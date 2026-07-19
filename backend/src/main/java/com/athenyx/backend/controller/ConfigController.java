package com.athenyx.backend.controller;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigNotFoundException;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.config.dto.ConfigCategoryResponse;
import com.athenyx.backend.config.dto.ConfigEntryResponse;
import com.athenyx.backend.config.dto.PurgeResultResponse;
import com.athenyx.backend.config.dto.UpdateConfigRequest;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.service.EmailRetentionService;
import com.athenyx.backend.service.audit.AuditRetentionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ConfigController {

    private final ConfigService configService;
    private final AuditRetentionService auditRetentionService;
    private final EmailRetentionService emailRetentionService;
    private final AuditEventPublisher auditEventPublisher;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<ConfigCategoryResponse>> list() {
        return ResponseEntity.ok(configService.getAllGrouped());
    }

    @GetMapping("/{key}")
    public ResponseEntity<ConfigEntryResponse> get(@PathVariable String key) {
        return ResponseEntity.ok(configService.getByKey(key));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ConfigEntryResponse> update(
            @PathVariable String key,
            @Valid @RequestBody UpdateConfigRequest req,
            Authentication authentication) {
        ConfigKey configKey = ConfigKey.findByName(key)
                .orElseThrow(() -> new ConfigNotFoundException(key));
        if (!(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ConfigNotFoundException("user"));
        String oldValue = configService.getRaw(configKey);
        ConfigEntryResponse updated = configService.set(configKey, req.getValue(), actor);
        auditEventPublisher.publishConfigUpdate(
                actor.getId(), actor.getEmail(), key, oldValue, req.getValue());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/AUDIT_RETENTION_DAYS/purge-now")
    public ResponseEntity<PurgeResultResponse> purgeAudit(Authentication authentication) {
        long start = System.currentTimeMillis();
        int deleted = auditRetentionService.purgeNow();
        long duration = System.currentTimeMillis() - start;
        if (authentication.getPrincipal() instanceof Long actorId) {
            userRepository.findById(actorId).ifPresent(actor ->
                auditEventPublisher.publishConfigPurge(actorId, actor.getEmail(), "AUDIT_LOG",
                        "AUDIT_RETENTION_DAYS", deleted, null));
        }
        return ResponseEntity.ok(PurgeResultResponse.builder()
                .purgedCount(deleted)
                .executedAt(LocalDateTime.now())
                .durationMs(duration)
                .build());
    }

    @PostMapping("/EMAIL_RETENTION_DAYS/purge-now")
    public ResponseEntity<PurgeResultResponse> purgeEmails(Authentication authentication) {
        long start = System.currentTimeMillis();
        EmailRetentionService.PurgeResult result = emailRetentionService.purgeNow();
        long duration = System.currentTimeMillis() - start;
        if (authentication.getPrincipal() instanceof Long actorId) {
            userRepository.findById(actorId).ifPresent(actor ->
                auditEventPublisher.publishConfigPurge(actorId, actor.getEmail(), "EMAIL",
                        "EMAIL_RETENTION_DAYS", result.purgedCount(), result.skippedDueToReminders()));
        }
        return ResponseEntity.ok(PurgeResultResponse.builder()
                .purgedCount(result.purgedCount())
                .skippedDueToReminders(result.skippedDueToReminders())
                .executedAt(LocalDateTime.now())
                .durationMs(duration)
                .build());
    }
}
