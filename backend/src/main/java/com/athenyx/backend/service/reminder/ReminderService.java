package com.athenyx.backend.service.reminder;

import com.athenyx.backend.dto.CreateReminderRequest;
import com.athenyx.backend.dto.ReminderResponse;
import com.athenyx.backend.dto.ReminderSummary;
import com.athenyx.backend.dto.UpdateReminderRequest;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.Reminder;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.ReminderRepository;
import com.athenyx.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for {@link Reminder} CRUD plus the
 * list-endpoint enrichment helpers consumed by {@code GmailService}.
 *
 * <p>Role gating: the controller's {@code @PreAuthorize} is the
 * primary gate for create, but the service repeats the check as a
 * safety net (defence in depth, in case the controller is ever
 * wired with a relaxed annotation). Read/update/delete require
 * ownership and throw {@link ReminderNotFoundException} when the
 * row is missing <strong>or</strong> belongs to a different user —
 * deliberately indistinguishable to avoid leaking ids.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReminderService {

    /** Tolerance window so a reminder set "right now" is allowed. */
    private static final Duration PAST_TOLERANCE = Duration.ofMinutes(1);

    private final ReminderRepository repository;
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Throws {@link IllegalArgumentException} when {@code target} is
     * more than {@link #PAST_TOLERANCE} in the past. Returns silently
     * for "right now" and for every future instant.
     */
    private void requireFuture(LocalDateTime target) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (target.isBefore(now.minus(PAST_TOLERANCE))) {
            throw new IllegalArgumentException(
                "La fecha del recordatorio debe ser en el futuro.");
        }
    }

    /**
     * Persists a new reminder. Throws
     * {@link ReminderPremiumRequiredException} if the user is on the
     * TRIAL plan, {@link ReminderConflictException} when a reminder
     * already exists for the same email, and a plain
     * {@link RuntimeException} (mapped to 400) when the email
     * id is unknown or belongs to another user.
     */
    public ReminderResponse create(Long userId, CreateReminderRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getRole() == Role.TRIAL) {
            throw new ReminderPremiumRequiredException(
                "Los recordatorios están disponibles en el plan Premium.");
        }

        Email email = emailRepository.findById(request.emailId())
            .orElseThrow(() -> new RuntimeException("Correo no encontrado"));
        if (!email.getUser().getId().equals(userId)) {
            throw new RuntimeException("Acceso denegado");
        }

        if (repository.existsByEmailIdAndUserId(email.getId(), userId)) {
            throw new ReminderConflictException(
                "Ya tienes un recordatorio para este correo.");
        }

        requireFuture(request.reminderDate());

        Reminder reminder = Reminder.builder()
            .user(user)
            .email(email)
            .reminderDate(request.reminderDate())
            .message(normalizeMessage(request.message()))
            .done(false)
            .build();

        return toResponse(repository.save(reminder));
    }

    /**
     * Applies a partial update — only the non-null members of
     * {@code request} are written. Ownership is verified up-front.
     */
    public ReminderResponse update(Long userId, Long id, UpdateReminderRequest request) {
        Reminder reminder = repository.findById(id)
            .filter(r -> r.getUser().getId().equals(userId))
            .orElseThrow(() -> new ReminderNotFoundException("Recordatorio no encontrado"));

        if (request.reminderDate() != null) {
            requireFuture(request.reminderDate());
            reminder.setReminderDate(request.reminderDate());
        }
        if (request.message() != null) {
            reminder.setMessage(normalizeMessage(request.message()));
        }
        if (request.done() != null) {
            reminder.setDone(request.done());
        }

        return toResponse(repository.save(reminder));
    }

    /**
     * Removes a reminder by id. Verifies ownership before deleting;
     * a missing row raises {@link ReminderNotFoundException} (404).
     */
    public void delete(Long userId, Long id) {
        Reminder reminder = repository.findById(id)
            .filter(r -> r.getUser().getId().equals(userId))
            .orElseThrow(() -> new ReminderNotFoundException("Recordatorio no encontrado"));
        repository.delete(reminder);
    }

    /**
     * Bulk-deletes every completed reminder for the user. Returns
     * the number of rows removed so the SPA can show "Se eliminaron
     * N recordatorios". Pending reminders are untouched.
     */
    public int clearCompleted(Long userId) {
        return (int) repository.deleteByUserIdAndDone(userId, true);
    }

    /**
     * Lists the user's reminders. TRIAL users get an empty list —
     * matching the "200 with empty list" decision so the SPA can
     * render the upsell state without inspecting HTTP status codes.
     */
    @Transactional(readOnly = true)
    public List<ReminderResponse> findByUser(Long userId, Filter filter) {
        List<Reminder> rows = switch (filter) {
            case PENDING -> repository.findByUserIdAndDoneOrderByReminderDateAsc(userId, false);
            case DONE -> repository.findByUserIdAndDoneOrderByReminderDateAsc(userId, true);
            case ALL -> repository.findByUserIdOrderByReminderDateAsc(userId);
        };
        return rows.stream().map(this::toResponse).toList();
    }

    /**
     * Returns the reminder for a given email owned by the user, or
     * empty when none exists. {@code null} is mapped to
     * {@link ReminderSummary} {@code null} at the call site — the
     * goal is to power {@code GET /api/reminders/by-email/{id}}.
     */
    @Transactional(readOnly = true)
    public ReminderSummary findSummaryByEmail(Long userId, Long emailId) {
        return repository.findByEmailIdAndUserId(emailId, userId)
            .map(r -> new ReminderSummary(r.getId(), r.getReminderDate(), r.isDone()))
            .orElse(null);
    }

    /**
     * Batch lookup for list-endpoint enrichment. Returns a map keyed
     * by email id (only emails with a reminder are present).
     */
    @Transactional(readOnly = true)
    public Map<Long, ReminderSummary> findSummariesForEmails(Long userId, Collection<Long> emailIds) {
        if (emailIds == null || emailIds.isEmpty()) {
            return Map.of();
        }
        List<Reminder> rows = repository.findByUserIdAndEmailIdIn(userId, emailIds);
        Map<Long, ReminderSummary> byEmail = new HashMap<>(rows.size());
        for (Reminder r : rows) {
            byEmail.put(r.getEmail().getId(),
                new ReminderSummary(r.getId(), r.getReminderDate(), r.isDone()));
        }
        return byEmail;
    }

    private ReminderResponse toResponse(Reminder r) {
        return new ReminderResponse(
            r.getId(),
            r.getEmail().getId(),
            r.getReminderDate(),
            r.getMessage(),
            r.isDone(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }

    private static String normalizeMessage(String message) {
        if (message == null) return null;
        String trimmed = message.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Filter selector for {@link #findByUser}. */
    public enum Filter {
        ALL, PENDING, DONE;

        public static Filter parse(String raw) {
            if (raw == null) return ALL;
            return switch (raw.trim().toLowerCase()) {
                case "pending", "pendientes" -> PENDING;
                case "done", "completados", "completed" -> DONE;
                default -> ALL;
            };
        }
    }
}
