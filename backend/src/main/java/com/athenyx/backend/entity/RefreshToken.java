package com.athenyx.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh-token record stored in the {@code refresh_tokens} table.
 *
 * <p>Each row stores only the SHA-256 hash of the raw token. Tokens are
 * organised in {@code familyId} groups: a normal refresh marks the
 * presented row as {@link RevokedReason#REPLACED} and inserts a
 * successor in the same family. Presenting a {@code REPLACED} or
 * already-revoked token triggers
 * {@link com.athenyx.backend.security.RefreshTokenException}
 * with kind {@code REUSE_DETECTED}, which revokes the whole family.</p>
 *
 * <p>Each family has a sliding {@link #expiresAt} (idle TTL) and a hard
 * {@link #absoluteExpiresAt} (max lifetime, regardless of activity).</p>
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_family_id", columnList = "family_id"),
                @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_tokens_user"))
    private User user;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Lob
    @Column(name = "token_hash", nullable = false, unique = true)
    private byte[] tokenHash;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private LocalDateTime absoluteExpiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private RevokedReason revokedReason;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip", length = 64)
    private String ip;

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now) && absoluteExpiresAt.isAfter(now);
    }

    public static String newFamilyId() {
        return UUID.randomUUID().toString();
    }
}
