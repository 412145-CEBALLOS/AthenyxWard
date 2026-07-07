package com.athenyx.backend.repository;

import com.athenyx.backend.entity.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * Excludes soft-deleted emails.
     */
    List<Email> findByUserIdAndIsDeletedFalseOrderByReceivedAtDesc(Long userId);

    /**
     * Lists persisted emails for the user that are not hidden and not deleted, newest first.
     */
    List<Email> findByUserIdAndIsHiddenFalseAndIsDeletedFalseOrderByReceivedAtDesc(Long userId);

    /**
     * Cheap check used by {@code GmailService} to skip persisting a
     * message we have already seen.
     */
    boolean existsByGmailIdAndUserId(String gmailId, Long userId);

    /**
     * Fetches an email by Gmail id, scoped to a user.
     */
    Optional<Email> findByGmailIdAndUserId(String id, Long id1);

    /**
     * Lists all hidden emails for the user, newest first.
     * Excludes soft-deleted emails.
     */
    List<Email> findByUserIdAndIsHiddenTrueAndIsDeletedFalseOrderByReceivedAtDesc(Long userId);

    /**
     * Lists emails marked as important for the user, newest first.
     * Excludes hidden and deleted emails.
     */
    List<Email> findByUserIdAndIsImportantTrueAndIsHiddenFalseAndIsDeletedFalseOrderByReceivedAtDesc(Long userId);

    /**
     * Counts emails marked as important for the user.
     */
    long countByUserIdAndIsImportantTrue(Long userId);

    /**
     * Counts emails marked as hidden for the user.
     */
    long countByUserIdAndIsHiddenTrue(Long userId);

    /**
     * Case-insensitive substring search over {@code subject},
     * {@code sender}, {@code senderName} and {@code snippet} for the
     * given user. Newest first. Backs US 3.7 — the search bar in the
     * inbox view.
     *
     * <p>Both sides of the {@code LIKE} are wrapped in {@code LOWER(...)}
     * so the comparison is case-insensitive on MySQL (default
     * {@code utf8mb4_unicode_ci} would also work, but H2 in MySQL mode
     * is case-sensitive without the explicit cast — keeping the
     * {@code LOWER()} makes the behaviour identical across both
     * engines used in the project).</p>
     *
     * <p>The Gmail API is not consulted — the search is purely over
     * the {@code emails} table. Newly arrived messages that have not
     * been persisted yet will not appear in the results.</p>
     *
     * @param userId owner of the inbox
     * @param q non-blank search term (the caller is expected to trim)
     * @param pageable page request (page number, size, sort)
     */
    @Query("""
        SELECT e FROM Email e
        WHERE e.user.id = :userId
          AND e.isHidden = false
          AND e.isDeleted = false
          AND (LOWER(e.subject)     LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(e.sender)      LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(e.senderName)  LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(e.snippet)     LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY e.receivedAt DESC
        """)
    Page<Email> searchByUserAndTerm(@Param("userId") Long userId,
                                    @Param("q") String q,
                                    Pageable pageable);

    /**
     * Lists soft-deleted emails for the user, newest first.
     */
    List<Email> findByUserIdAndIsDeletedTrueOrderByReceivedAtDesc(Long userId);

    /**
     * Counts soft-deleted emails for the user.
     */
    long countByUserIdAndIsDeletedTrue(Long userId);
}
