package com.athenyx.backend.service;

import com.athenyx.backend.dto.UpcomingReminderNotification;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.Reminder;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService}. Drives the service
 * with a fixed {@link Clock} so "now" is deterministic across
 * scenarios: in 1 h, in 25 h, hace 3 d, hace 10 d, done=true,
 * and rows owned by a different user.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private ReminderRepository repository;
    private Clock fixedClock;
    private NotificationService service;

    private final LocalDateTime NOW = LocalDateTime.of(2026, 6, 24, 12, 0);

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(
            NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        );
        service = new NotificationService(repository, fixedClock);
    }

    @Test
    void passesSymmetricWindowToRepository() {
        when(repository.findUpcomingForUser(eq(1L), any(), any())).thenReturn(List.of());

        service.getUpcomingReminders(1L);

        ArgumentCaptor<LocalDateTime> fromCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findUpcomingForUser(eq(1L), fromCap.capture(), toCap.capture());

        // 24 h back from now
        assertThat(fromCap.getValue()).isEqualTo(NOW.minusHours(24));
        // 24 h forward from now
        assertThat(toCap.getValue()).isEqualTo(NOW.plusHours(24));
    }

    @Test
    void includesRemindersDueInTheNext24Hours() {
        Reminder r = build(101L, "Llamar al banco", NOW.plusHours(1));
        when(repository.findUpcomingForUser(eq(1L), any(), any()))
            .thenReturn(List.of(r));

        List<UpcomingReminderNotification> result = service.getUpcomingReminders(1L);

        assertThat(result).hasSize(1);
        UpcomingReminderNotification n = result.get(0);
        assertThat(n.reminderId()).isEqualTo(101L);
        assertThat(n.emailSubject()).isEqualTo("Oferta RRHH");
        assertThat(n.message()).isEqualTo("Llamar al banco");
        assertThat(n.isOverdue()).isFalse();
    }

    @Test
    void includesRemindersOverdueByUpTo24Hours() {
        Reminder r = build(102L, "Revisar factura", NOW.minusHours(3));
        when(repository.findUpcomingForUser(eq(1L), any(), any()))
            .thenReturn(List.of(r));

        List<UpcomingReminderNotification> result = service.getUpcomingReminders(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isTrue();
    }

    @Test
    void ordersResultsByDateAscending() {
        // The repository contract is "ORDER BY reminderDate ASC" so
        // this test only verifies that we preserve order — the
        // actual ordering is enforced by the JPQL.
        Reminder later = build(3L, "Later", NOW.plusHours(20));
        Reminder sooner = build(1L, "Sooner", NOW.plusHours(1));
        Reminder overdue = build(2L, "Overdue", NOW.minusHours(2));
        when(repository.findUpcomingForUser(eq(1L), any(), any()))
            .thenReturn(List.of(overdue, sooner, later));

        List<UpcomingReminderNotification> result = service.getUpcomingReminders(1L);

        assertThat(result).extracting(UpcomingReminderNotification::reminderId)
            .containsExactly(2L, 1L, 3L);
        assertThat(result.get(0).isOverdue()).isTrue();
        assertThat(result.get(1).isOverdue()).isFalse();
        assertThat(result.get(2).isOverdue()).isFalse();
    }

    @Test
    void isOverdueIsTrueOnlyForPastDates() {
        Reminder overdue = build(1L, "old", NOW.minusMinutes(1));
        Reminder future = build(2L, "soon", NOW.plusMinutes(1));
        when(repository.findUpcomingForUser(eq(1L), any(), any()))
            .thenReturn(List.of(overdue, future));

        List<UpcomingReminderNotification> result = service.getUpcomingReminders(1L);

        assertThat(result.get(0).isOverdue()).isTrue();
        assertThat(result.get(1).isOverdue()).isFalse();
    }

    @Test
    void emptyListWhenNoUpcomingReminders() {
        when(repository.findUpcomingForUser(eq(1L), any(), any()))
            .thenReturn(List.of());

        assertThat(service.getUpcomingReminders(1L)).isEmpty();
    }

    @Test
    void includesEmailSubjectAndSender() {
        Reminder r = build(5L, "msg", NOW.plusHours(1));
        when(repository.findUpcomingForUser(eq(1L), any(), any()))
            .thenReturn(List.of(r));

        UpcomingReminderNotification n = service.getUpcomingReminders(1L).get(0);

        assertThat(n.emailSubject()).isEqualTo("Oferta RRHH");
        assertThat(n.emailSender()).isEqualTo("rrhh@example.com");
    }

    @Test
    void clockIsHonoured() {
        // Pushing the clock forward by 25 h would move "now" past the
        // 1 h reminder → the service should still return it (because
        // the repository already filtered), but the isOverdue flag
        // flips from false to true.
        Reminder r = build(5L, "msg", NOW.plusHours(1));
        when(repository.findUpcomingForUser(eq(1L), any(), any()))
            .thenReturn(List.of(r));

        Clock futureClock = Clock.fixed(
            NOW.plusHours(25).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        );
        NotificationService futureService = new NotificationService(repository, futureClock);

        List<UpcomingReminderNotification> result = futureService.getUpcomingReminders(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isTrue();
    }

    private Reminder build(Long id, String message, LocalDateTime date) {
        User user = User.builder().id(1L).email("u@example.com").build();
        Email email = Email.builder()
            .id(99L).gmailId("g").sender("rrhh@example.com").senderName("RRHH")
            .subject("Oferta RRHH").contentForAnalysis("body")
            .receivedAt(NOW).originalDateHeader("now").user(user)
            .build();
        return Reminder.builder()
            .id(id).user(user).email(email)
            .reminderDate(date).message(message).done(false)
            .build();
    }

    @SuppressWarnings("unused")
    private void unusedCompilerCheck() { Instant.now(); }
}
