package com.athenyx.backend.repository;

import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User}.
 *
 * <p>Includes two custom JPQL operations used by the security layer:
 * {@link #findTokenVersionById(Long)} (cheap read for JWT validation)
 * and {@link #incrementTokenVersion(Long)} (atomic bump on login or
 * refresh rotation).</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Returns just the {@code tokenVersion} of a user, avoiding loading the
     * full entity during JWT validation.
     *
     * @param id primary key
     * @return current token version, or empty if the user does not exist
     */
    @Query("SELECT u.tokenVersion FROM User u WHERE u.id = :id")
    Optional<Long> findTokenVersionById(@Param("id") Long id);

    /**
     * Atomically increments {@code tokenVersion} and clears the
     * persistence context. Used to invalidate previously-issued access
     * JWTs when a user logs in or rotates their refresh token.
     *
     * @param id user id
     * @return number of rows updated (0 if the user does not exist)
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);

    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
        AND (:query IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:role IS NULL OR u.role = :role)
        AND (:active IS NULL OR u.isActive = :active)
        """)
    Page<User> findAllFiltered(
        @Param("query") String query,
        @Param("role") Role role,
        @Param("active") Boolean active,
        Pageable pageable
    );

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.deletedAt = :deletedAt, u.isActive = false WHERE u.id = :id")
    int softDelete(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.analysisCount = 0, u.trialEndDate = :trialEndDate WHERE u.id = :id")
    int resetTrial(@Param("id") Long id, @Param("trialEndDate") LocalDateTime trialEndDate);

    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
        AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY CASE WHEN LOWER(u.email) LIKE LOWER(CONCAT(:query, '%')) THEN 0 ELSE 1 END
        """)
    List<User> searchByEmail(@Param("query") String query, Pageable pageable);

    long countByDeletedAtIsNull();

    long countByRoleAndDeletedAtIsNull(Role role);

    @Query("""
        SELECT u.role AS role, COUNT(u) AS count FROM User u
        WHERE u.deletedAt IS NULL
        GROUP BY u.role
        """)
    List<RoleCount> countByRoleGrouped();

    interface RoleCount {
        Role getRole();
        long getCount();
    }

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query(value = """
        SELECT DATE(u.created_at), COUNT(*)
        FROM users u
        WHERE u.created_at BETWEEN :from AND :to
        GROUP BY DATE(u.created_at)
        """, nativeQuery = true)
    List<Object[]> countDailySignups(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    long countByLastLoginAtAfterAndDeletedAtIsNull(LocalDateTime cutoff);
}
