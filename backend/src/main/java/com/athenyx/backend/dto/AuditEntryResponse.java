package com.athenyx.backend.dto;

import com.athenyx.backend.entity.AuditActionType;
import com.athenyx.backend.entity.AuditResult;
import com.athenyx.backend.entity.AuditSeverity;

import java.time.LocalDateTime;

public record AuditEntryResponse(
    Long id,
    LocalDateTime createdAt,
    Long actorId,
    String actorEmail,
    String actorRole,
    AuditActionType actionType,
    String targetType,
    String targetId,
    AuditSeverity severity,
    AuditResult result,
    String payload,
    String ipAddress,
    String userAgent,
    String correlationId
) {}
