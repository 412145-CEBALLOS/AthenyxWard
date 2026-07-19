package com.athenyx.backend.controller;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.entity.AuditActionType;
import com.athenyx.backend.entity.AuditLog;
import com.athenyx.backend.entity.AuditSeverity;
import com.athenyx.backend.service.audit.AuditService;
import com.athenyx.backend.util.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * ADMIN-only REST endpoint for the audit log.
 *
 * <p>Base path: {@code /api/admin/audit}. All endpoints require
 * {@code ROLE_ADMIN}.</p>
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;
    private final AuditEventPublisher auditEventPublisher;

    @GetMapping
    public ResponseEntity<?> getAuditEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditActionType action,
            @RequestParam(required = false) AuditSeverity severity,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.atTime(LocalTime.MAX);

        if (query != null && !query.isBlank()) {
            List<AuditLog> entries = auditService.searchByRelevance(
                    fromDt, toDt, actor, action, severity, query, 10);
            return ResponseEntity.ok(Map.of(
                    "items", entries.stream().map(this::toMap).toList(),
                    "currentPage", 0,
                    "totalPages", 1,
                    "totalItems", entries.size()
            ));
        }

        return ResponseEntity.ok(auditService.findEntries(
                fromDt, toDt, actor, action, severity, page, size));
    }

    @GetMapping("/export")
    @Transactional
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditActionType action,
            @RequestParam(required = false) AuditSeverity severity,
            Authentication auth) {

        Long actorId = (Long) auth.getPrincipal();
        String actorEmail = (String) auth.getDetails();

        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.atTime(LocalTime.MAX);

        String filters = buildFilterSummary(from, to, actor, action, severity);
        auditEventPublisher.publishExportCsv(actorId, actorEmail, filters);

        List<AuditLog> entries = auditService.streamEntries(fromDt, toDt, actor, action, severity);

        String filename = "audit-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";

        StreamingResponseBody stream = out -> {
            byte[] header = CsvWriter.writeRow(
                    List.of("id", "created_at", "actor_email", "actor_role", "action_type",
                            "target_type", "target_id", "severity", "result", "payload",
                            "ip_address", "user_agent", "correlation_id"));
            out.write(header);
            out.write('\n');

            for (AuditLog entry : entries) {
                byte[] row = CsvWriter.writeRow(List.of(
                        String.valueOf(entry.getId()),
                        entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : "",
                        entry.getActorEmail(),
                        entry.getActorRole() != null ? entry.getActorRole() : "",
                        entry.getActionType() != null ? entry.getActionType().name() : "",
                        entry.getTargetType() != null ? entry.getTargetType() : "",
                        entry.getTargetId() != null ? entry.getTargetId() : "",
                        entry.getSeverity() != null ? entry.getSeverity().name() : "",
                        entry.getResult() != null ? entry.getResult().name() : "",
                        entry.getPayload() != null ? entry.getPayload() : "",
                        entry.getIpAddress() != null ? entry.getIpAddress() : "",
                        entry.getUserAgent() != null ? entry.getUserAgent() : "",
                        entry.getCorrelationId() != null ? entry.getCorrelationId() : ""
                ));
                out.write(row);
                out.write('\n');
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(stream);
    }

    private String buildFilterSummary(LocalDate from, LocalDate to, String actor,
                                     AuditActionType action, AuditSeverity severity) {
        StringBuilder sb = new StringBuilder();
        if (from != null) sb.append("from=").append(from).append(" ");
        if (to != null) sb.append("to=").append(to).append(" ");
        if (actor != null && !actor.isBlank()) sb.append("actor=").append(actor).append(" ");
        if (action != null) sb.append("action=").append(action).append(" ");
        if (severity != null) sb.append("severity=").append(severity);
        return sb.toString().trim();
    }

    private Map<String, Object> toMap(AuditLog a) {
        return Map.ofEntries(
                Map.entry("id", a.getId()),
                Map.entry("createdAt", a.getCreatedAt()),
                Map.entry("actorId", a.getActorId() != null ? a.getActorId() : ""),
                Map.entry("actorEmail", a.getActorEmail()),
                Map.entry("actorRole", a.getActorRole() != null ? a.getActorRole() : ""),
                Map.entry("actionType", a.getActionType() != null ? a.getActionType().name() : ""),
                Map.entry("targetType", a.getTargetType() != null ? a.getTargetType() : ""),
                Map.entry("targetId", a.getTargetId() != null ? a.getTargetId() : ""),
                Map.entry("severity", a.getSeverity() != null ? a.getSeverity().name() : ""),
                Map.entry("result", a.getResult() != null ? a.getResult().name() : ""),
                Map.entry("payload", a.getPayload() != null ? a.getPayload() : ""),
                Map.entry("ipAddress", a.getIpAddress() != null ? a.getIpAddress() : ""),
                Map.entry("userAgent", a.getUserAgent() != null ? a.getUserAgent() : ""),
                Map.entry("correlationId", a.getCorrelationId() != null ? a.getCorrelationId() : "")
        );
    }
}
