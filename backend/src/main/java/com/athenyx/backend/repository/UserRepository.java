package com.athenyx.backend.repository;

import com.athenyx.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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
}
