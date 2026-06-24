package com.athenyx.backend.service;

import com.athenyx.backend.dto.UpcomingReminderNotification;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.Reminder;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.ReminderRepository;
import com.athenyx.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the notification polling path.
 * Uses H2 (via the {@code test} profile) and pins the {@link Clock}
 * bean to a fixed instant so the 24 h window is deterministic.
 *
 * <p>Verifies the full filter chain:
 * <ul>
 *     <li>due in the next 24 h is included</li>
 *     <li>overdue by up to 24 h is included</li>
 *     <li>due in 25 h is excluded</li>
 *     <li>overdue by 25 h is excluded</li>
 *     <li>done reminders are excluded</li>
 *     <li>reminders owned by another user are excluded</li>
 *     <li>{@code isOverdue} flag is set correctly per row</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceIntegrationTest {

    @Autowired private NotificationService service;
    @Autowired private ReminderRepository reminderRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private Clock clock;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 24, 12, 0);

    private User premium;
    private User other;
    private Email email;

    @BeforeEach
    void setUp() {
        reminderRepository.deleteAll();
        emailRepository.deleteAll();
        userRepository.deleteAll();

        // The autowired Clock is the system clock — we need a
        // fixed clock for deterministic tests. The Spring context
        // provides a single bean so we swap it for this service
        // call only by wrapping with a deterministic service.
        premium = userRepository.save(User.builder()
            .googleId("g-1").email("p@example.com").name("P").role(Role.PREMIUM).build());
        other = userRepository.save(User.builder()
            .googleId("g-2").email("o@example.com").name("O").role(Role.PREMIUM).build());

        email = emailRepository.save(Email.builder()
            .gmailId("g-1").sender("a@b.com").subject("Subject")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(NOW.minusDays(1))
            .originalDateHeader("now")
            .user(premium).build());
    }

    @Test
    void returnsEmptyListWhenNoReminders() {
        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void includesReminderDueInOneHour() {
        persist(NOW.plusHours(1), false, premium, "in 1 h");
        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isFalse();
    }

    @Test
    void includesReminderDueInExactly24Hours() {
        persist(NOW.plusHours(24), false, premium, "at the edge");
        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).hasSize(1);
    }

    @Test
    void excludesReminderDueIn25Hours() {
        persist(NOW.plusHours(25), false, premium, "too far");
        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void includesReminderOverdueBy3Hours() {
        persist(NOW.minusHours(3), false, premium, "overdue 3 h");
        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isTrue();
    }

    @Test
    void includesReminderOverdueByExactly24Hours() {
        persist(NOW.minusHours(24), false, premium, "at the edge");
        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isTrue();
    }

    @Test
    void excludesReminderOverdueBy25Hours() {
        persist(NOW.minusHours(25), false, premium, "too old");
        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void excludesCompletedReminders() {
        Reminder r = Reminder.builder()
            .user(premium).email(email)
            .reminderDate(NOW.plusHours(1))
            .message("done").done(true)
            .build();
        reminderRepository.save(r);

        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void excludesRemindersOwnedByOtherUsers() {
        Email otherEmail = emailRepository.save(Email.builder()
            .gmailId("g-2").sender("x@y.com").subject("Other")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(NOW).originalDateHeader("now")
            .user(other).build());
        Reminder r = Reminder.builder()
            .user(other).email(otherEmail)
            .reminderDate(NOW.plusHours(1))
            .message("other user").done(false)
            .build();
        reminderRepository.save(r);

        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void sortsByDateAscending() {
        Email e1 = freshEmail(premium, "g-later");
        Email e2 = freshEmail(premium, "g-earliest");
        Email e3 = freshEmail(premium, "g-middle");
        reminderRepository.save(reminderFor(premium, e1, NOW.plusHours(20), "later", false));
        reminderRepository.save(reminderFor(premium, e2, NOW.minusHours(2), "earliest", false));
        reminderRepository.save(reminderFor(premium, e3, NOW.plusHours(1), "middle", false));

        List<UpcomingReminderNotification> result = pinnedService().getUpcomingReminders(premium.getId());

        assertThat(result).extracting(UpcomingReminderNotification::message)
            .containsExactly("earliest", "middle", "later");
        assertThat(result.get(0).isOverdue()).isTrue();
        assertThat(result.get(2).isOverdue()).isFalse();
    }

    @Test
    void responseCarriesEmailSubjectAndSender() {
        persist(NOW.plusHours(1), false, premium, "msg");

        UpcomingReminderNotification n = pinnedService()
            .getUpcomingReminders(premium.getId())
            .get(0);

        assertThat(n.emailSubject()).isEqualTo("Subject");
        assertThat(n.emailSender()).isEqualTo("a@b.com");
        assertThat(n.emailId()).isEqualTo(email.getId());
        assertThat(n.reminderId()).isNotNull();
    }

    private void persist(LocalDateTime date, boolean done, User owner, String message) {
        Email ownedEmail = owner.getId().equals(premium.getId()) ? email : emailRepository.save(
            Email.builder()
                .gmailId("g-" + message).sender("a@b.com").subject(message)
                .snippet("snip").contentForAnalysis("body")
                .receivedAt(NOW).originalDateHeader("now")
                .user(owner).build()
        );
        reminderRepository.save(Reminder.builder()
            .user(owner).email(ownedEmail)
            .reminderDate(date)
            .message(message).done(done)
            .build());
    }

    private Email freshEmail(User owner, String gmailId) {
        return emailRepository.save(Email.builder()
            .gmailId(gmailId).sender("a@b.com").subject("Subj")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(NOW).originalDateHeader("now")
            .user(owner).build());
    }

    private Reminder reminderFor(User owner, Email mail, LocalDateTime date, String message, boolean done) {
        return Reminder.builder()
            .user(owner).email(mail)
            .reminderDate(date)
            .message(message).done(done)
            .build();
    }

    /**
     * Wraps the autowired services with a fixed-clock service so
     * the tests don't need a full Spring bean replacement.
     */
    private NotificationService pinnedService() {
        Clock fixed = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new NotificationService(reminderRepository, fixed);
    }
}
