package com.athenyx.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit log entry.
 *
 * <p>Each row records one discrete action performed on the platform by a user
 * or by the system.  Rows are written by {@code AuditEventListener} (via
 * {@code AFTER_COMMIT}) and are never updated or deleted by application code
 * other than {@code AuditRetentionService}.</p>
 *
 * <p>Timestamps are set via {@code Clock} (not {@code @CreationTimestamp})
 * to keep them deterministic in tests.</p>
 *
 * @see AuditActionType
 * @see AuditSeverity
 * @see AuditResult
 */
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_created_at", columnList = "created_at DESC"),
    @Index(name = "idx_audit_actor_created", columnList = "actor_id, created_at DESC"),
    @Index(name = "idx_audit_action_created", columnList = "action_type, created_at DESC"),
    @Index(name = "idx_audit_correlation", columnList = "correlation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "actor_id")
    private Long actorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", insertable = false, updatable = false)
    private User actor;

    @Column(name = "actor_email", nullable = false)
    private String actorEmail;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private AuditActionType actionType;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditResult result;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;
}
