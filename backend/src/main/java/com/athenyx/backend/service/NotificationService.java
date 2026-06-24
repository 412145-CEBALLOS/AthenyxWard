package com.athenyx.backend.service;

import com.athenyx.backend.dto.UpcomingReminderNotification;
import com.athenyx.backend.entity.Reminder;
import com.athenyx.backend.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Powers the bell-icon notification panel and the "tu recordatorio
 * acaba de vencer" toasts. Returns every non-done reminder for the
 * user that falls inside a symmetric 24 h window around the current
 * time — i.e. anything due in the next 24 h plus anything overdue
 * by up to 24 h.
 *
 * <p>The window is computed against a {@link Clock} so unit tests
 * can pin "now" deterministically. The default Spring container
 * supplies {@code Clock.systemUTC()}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /** How far back and forward a reminder is considered "upcoming". */
    static final Duration UPCOMING_WINDOW = Duration.ofHours(24);

    private final ReminderRepository reminderRepository;
    private final Clock clock;

    /**
     * Returns the list of upcoming / just-overdue reminders for
     * the user, sorted ascending by date (soonest first). The
     * {@code isOverdue} flag is computed per-row against the
     * current {@link Clock} value.
     */
    public List<UpcomingReminderNotification> getUpcomingReminders(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = now.minus(UPCOMING_WINDOW);
        LocalDateTime to = now.plus(UPCOMING_WINDOW);

        List<Reminder> rows = reminderRepository.findUpcomingForUser(userId, from, to);
        return rows.stream()
            .map(r -> toNotification(r, now))
            .toList();
    }

    private UpcomingReminderNotification toNotification(Reminder r, LocalDateTime now) {
        return new UpcomingReminderNotification(
            r.getId(),
            r.getEmail().getId(),
            r.getEmail().getSubject(),
            r.getEmail().getSender(),
            r.getMessage(),
            r.getReminderDate(),
            r.getReminderDate().isBefore(now)
        );
    }
}
