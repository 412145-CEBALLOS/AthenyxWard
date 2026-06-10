package com.athenyx.backend.repository;

import com.athenyx.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RefreshToken}.
 *
 * <p>The two bulk-update queries are intentionally written as JPQL
 * {@code UPDATE} statements so they hit the database with a single
 * statement rather than loading rows into memory.</p>
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Looks up a refresh token by its SHA-256 hash, eagerly loading the
     * associated user. Always returns the same row, even after the token
     * has been revoked — the service layer interprets that as reuse.
     */
    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    /**
     * Lists every still-active refresh token for a user. Not currently
     * used by the API but exposed for future admin tooling.
     */
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(Long userId);

    /**
     * Revokes every active row in the given family. Returns the number
     * of rows updated. Used both on reuse detection and on absolute
     * lifetime expiry.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revokedAt = :now,
                rt.revokedReason = :reason
            WHERE rt.familyId = :familyId
              AND rt.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") String familyId,
                     @Param("reason") com.athenyx.backend.entity.RevokedReason reason,
                     @Param("now") LocalDateTime now);

    /**
     * Revokes every active row for the given user. Used by
     * {@code /api/auth/logout-all}.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revokedAt = :now,
                rt.revokedReason = :reason
            WHERE rt.user.id = :userId
              AND rt.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("reason") com.athenyx.backend.entity.RevokedReason reason,
                         @Param("now") LocalDateTime now);
}
