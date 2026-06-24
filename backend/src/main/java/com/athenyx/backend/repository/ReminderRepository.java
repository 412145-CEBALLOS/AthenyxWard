package com.athenyx.backend.repository;

import com.athenyx.backend.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Reminder}. All queries are
 * scoped by user — no method accepts a raw {@code emailId} alone.
 */
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    /**
     * Cheap existence check used by the service to enforce the
     * "one reminder per email" rule before persisting.
     */
    boolean existsByEmailIdAndUserId(Long emailId, Long userId);

    /**
     * Fetches the (unique) reminder for an email owned by a user, or
     * empty when none exists.
     */
    Optional<Reminder> findByEmailIdAndUserId(Long emailId, Long userId);

    /**
     * Lists every reminder for the user, ordered by date ascending
     * (soonest first — matches the "próximos primero" spec for the
     * /reminders page).
     */
    List<Reminder> findByUserIdOrderByReminderDateAsc(Long userId);

    /**
     * Filtered variant of {@link #findByUserIdOrderByReminderDateAsc}
     * used to drive the "Pendientes" / "Completados" tabs.
     */
    List<Reminder> findByUserIdAndDoneOrderByReminderDateAsc(Long userId, boolean done);

    /**
     * Batch lookup for the list-endpoint enrichment — returns every
     * reminder the user owns whose email is in the given collection.
     * Used to fill {@code EmailSummary.reminder} without an N+1.
     */
    List<Reminder> findByUserIdAndEmailIdIn(Long userId, Collection<Long> emailIds);

    /**
     * Returns every pending reminder for the user whose date falls
     * inside {@code [from, to]}. The notification service uses
     * this with a symmetric 24 h window around {@code now} so the
     * frontend can render both "due soon" and "just overdue"
     * entries in one request.
     */
    @Query("""
        SELECT r FROM Reminder r
        WHERE r.user.id = :userId
        AND r.done = false
        AND r.reminderDate BETWEEN :from AND :to
        ORDER BY r.reminderDate ASC
        """)
    List<Reminder> findUpcomingForUser(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    /**
     * Bulk-deletes every completed reminder for the user. Returns
     * the number of rows affected. Pending reminders are untouched.
     */
    long deleteByUserIdAndDone(Long userId, boolean done);
}
