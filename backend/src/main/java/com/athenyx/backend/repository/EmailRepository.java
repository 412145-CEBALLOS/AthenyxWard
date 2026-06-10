package com.athenyx.backend.repository;

import com.athenyx.backend.entity.Email;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Email}. All lookups are
 * scoped by user — never expose rows belonging to a different account.
 */
public interface EmailRepository extends JpaRepository<Email, Long> {
    /**
     * Lists every persisted email for the user, newest first. Used as
     * the in-database source of truth when the Gmail API is unreachable.
     */
    List<Email> findByUserIdOrderByReceivedAtDesc(Long userId);

    /**
     * Cheap check used by {@code GmailService} to skip persisting a
     * message we have already seen.
     */
    boolean existsByGmailIdAndUserId(String gmailId, Long userId);

    /**
     * Fetches an email by Gmail id, scoped to a user.
     */
    Optional<Email> findByGmailIdAndUserId(String id, Long id1);
}
