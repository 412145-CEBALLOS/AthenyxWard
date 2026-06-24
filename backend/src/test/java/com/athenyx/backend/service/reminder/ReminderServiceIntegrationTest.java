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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration test for the reminder CRUD path. Uses H2
 * (via the {@code test} profile) to exercise JPA mappings, FK
 * constraints, the {@code uk_reminder_user_email} unique constraint
 * and every {@link ReminderService} method against a real database.
 *
 * <p>HTTP-layer security is deliberately bypassed by going through
 * the service directly — the controller + PreAuthorize annotations
 * are covered by {@link com.athenyx.backend.controller.ReminderControllerSecurityTest}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReminderServiceIntegrationTest {

    @Autowired private ReminderService service;
    @Autowired private ReminderRepository repository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private UserRepository userRepository;

    private User premium;
    private User trial;
    private Email email;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        emailRepository.deleteAll();
        userRepository.deleteAll();

        premium = userRepository.save(User.builder()
            .googleId("g-premium").email("p@example.com").name("P").role(Role.PREMIUM).build());
        trial = userRepository.save(User.builder()
            .googleId("g-trial").email("t@example.com").name("T").role(Role.TRIAL).build());

        email = emailRepository.save(Email.builder()
            .gmailId("gid-1").sender("a@b.com").senderName("A").subject("Subj")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(LocalDateTime.now())
            .originalDateHeader("now")
            .user(premium)
            .build());
    }

    // --- create ---

    @Test
    void create_persistsAndReturnsFullResponse() {
        CreateReminderRequest req = new CreateReminderRequest(
            email.getId(),
            LocalDateTime.of(2026, 6, 24, 15, 0),
            "  Llamar al banco  ");

        ReminderResponse response = service.create(premium.getId(), req);

        assertThat(response.id()).isNotNull();
        assertThat(response.emailId()).isEqualTo(email.getId());
        assertThat(response.reminderDate()).isEqualTo(LocalDateTime.of(2026, 6, 24, 15, 0));
        assertThat(response.message()).isEqualTo("Llamar al banco");
        assertThat(response.done()).isFalse();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void create_rejectsDuplicateWithConflict() {
        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "primero"));

        assertThatThrownBy(() -> service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 25, 9, 0), "segundo")))
            .isInstanceOf(ReminderConflictException.class)
            .hasMessageContaining("Ya tienes");
    }

    @Test
    void create_rejectsTrialUser() {
        assertThatThrownBy(() -> service.create(trial.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "x")))
            .isInstanceOf(ReminderPremiumRequiredException.class);
    }

    @Test
    void create_allowsSameEmailAcrossDifferentUsers() {
        User other = userRepository.save(User.builder()
            .googleId("g-other").email("o@example.com").name("O").role(Role.PREMIUM).build());
        Email otherEmail = emailRepository.save(Email.builder()
            .gmailId("gid-2").sender("a@b.com").subject("Subj2")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(LocalDateTime.now())
            .originalDateHeader("now")
            .user(other).build());

        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "u1"));
        service.create(other.getId(), new CreateReminderRequest(
            otherEmail.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "u2"));

        assertThat(repository.findAll()).hasSize(2);
    }

    // --- read ---

    @Test
    void findByUser_returnsItemsSortedByDateAsc() {
        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 26, 10, 0), "later"));

        Email email2 = emailRepository.save(Email.builder()
            .gmailId("gid-3").sender("a@b.com").subject("Subj3")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(LocalDateTime.now())
            .originalDateHeader("now")
            .user(premium).build());
        service.create(premium.getId(), new CreateReminderRequest(
            email2.getId(), LocalDateTime.of(2026, 6, 25, 9, 0), "sooner"));

        List<ReminderResponse> items = service.findByUser(premium.getId(), ReminderService.Filter.ALL);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).reminderDate()).isEqualTo(LocalDateTime.of(2026, 6, 25, 9, 0));
        assertThat(items.get(1).reminderDate()).isEqualTo(LocalDateTime.of(2026, 6, 26, 10, 0));
    }

    @Test
    void findByUser_pendingFilterExcludesDone() {
        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "pending"));

        Email email2 = emailRepository.save(Email.builder()
            .gmailId("gid-4").sender("a@b.com").subject("Subj4")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(LocalDateTime.now())
            .originalDateHeader("now")
            .user(premium).build());
        ReminderResponse done = service.create(premium.getId(), new CreateReminderRequest(
            email2.getId(), LocalDateTime.of(2026, 6, 25, 10, 0), "to-complete"));
        service.update(premium.getId(), done.id(), new UpdateReminderRequest(null, null, true));

        assertThat(service.findByUser(premium.getId(), ReminderService.Filter.PENDING))
            .hasSize(1)
            .allSatisfy(r -> assertThat(r.done()).isFalse());
        assertThat(service.findByUser(premium.getId(), ReminderService.Filter.DONE))
            .hasSize(1)
            .allSatisfy(r -> assertThat(r.done()).isTrue());
    }

    @Test
    void findByUser_trialReturnsEmpty() {
        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "x"));

        assertThat(service.findByUser(trial.getId(), ReminderService.Filter.ALL)).isEmpty();
    }

    @Test
    void findSummaryByEmail_returnsNullWhenNoneExists() {
        ReminderSummary summary = service.findSummaryByEmail(premium.getId(), email.getId());
        assertThat(summary).isNull();
    }

    @Test
    void findSummaryByEmail_returnsSummaryWhenExists() {
        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "x"));

        ReminderSummary summary = service.findSummaryByEmail(premium.getId(), email.getId());

        assertThat(summary).isNotNull();
        assertThat(summary.id()).isNotNull();
        assertThat(summary.reminderDate()).isEqualTo(LocalDateTime.of(2026, 6, 24, 15, 0));
        assertThat(summary.done()).isFalse();
    }

    @Test
    void findSummariesForEmails_groupsByEmailId() {
        Email email2 = emailRepository.save(Email.builder()
            .gmailId("gid-5").sender("a@b.com").subject("Subj5")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(LocalDateTime.now())
            .originalDateHeader("now")
            .user(premium).build());
        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "for-1"));

        Map<Long, ReminderSummary> map = service.findSummariesForEmails(
            premium.getId(), List.of(email.getId(), email2.getId()));

        assertThat(map).containsOnlyKeys(email.getId());
        assertThat(map.get(email.getId()).reminderDate()).isEqualTo(LocalDateTime.of(2026, 6, 24, 15, 0));
    }

    // --- update ---

    @Test
    void update_appliesPartialPatch() {
        ReminderResponse created = service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "original"));

        ReminderResponse updated = service.update(premium.getId(), created.id(),
            new UpdateReminderRequest(LocalDateTime.of(2026, 6, 25, 9, 0), "  new  ", null));

        assertThat(updated.reminderDate()).isEqualTo(LocalDateTime.of(2026, 6, 25, 9, 0));
        assertThat(updated.message()).isEqualTo("new");
        assertThat(updated.done()).isFalse();
    }

    @Test
    void update_doneOnlyLeavesOtherFieldsUntouched() {
        ReminderResponse created = service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "msg"));

        ReminderResponse updated = service.update(premium.getId(), created.id(),
            new UpdateReminderRequest(null, null, true));

        assertThat(updated.done()).isTrue();
        assertThat(updated.message()).isEqualTo("msg");
        assertThat(updated.reminderDate()).isEqualTo(LocalDateTime.of(2026, 6, 24, 15, 0));
    }

    @Test
    void update_throwsNotFoundForOtherUsersReminder() {
        ReminderResponse created = service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "x"));

        assertThatThrownBy(() -> service.update(trial.getId(), created.id(),
            new UpdateReminderRequest(null, null, true)))
            .isInstanceOf(ReminderNotFoundException.class);
    }

    // --- delete ---

    @Test
    void delete_removesReminder() {
        ReminderResponse created = service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "x"));

        service.delete(premium.getId(), created.id());

        assertThat(repository.findById(created.id())).isEmpty();
    }

    @Test
    void delete_throwsNotFoundForOtherUsersReminder() {
        ReminderResponse created = service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "x"));

        assertThatThrownBy(() -> service.delete(trial.getId(), created.id()))
            .isInstanceOf(ReminderNotFoundException.class);
        assertThat(repository.findById(created.id())).isPresent();
    }

    // --- unique constraint ---

    @Test
    void databaseEnforcesUniqueConstraintPerUserEmail() {
        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "x"));

        // Bypass the service's pre-check and try to write a raw row —
        // the DB unique constraint must reject it.
        Reminder duplicate = Reminder.builder()
            .user(premium).email(email)
            .reminderDate(LocalDateTime.of(2026, 6, 25, 9, 0))
            .message("dup").done(false)
            .build();

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
            .hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException.class);
    }

    // --- past date validation ---

    @Test
    void create_rejectsPastDate() {
        // The integration test uses the system clock (UTC) so a date
        // set 2 hours in the past from the test runner's wall clock
        // is reliably rejected.
        LocalDateTime past = LocalDateTime.now().minusHours(2);
        assertThatThrownBy(() -> service.create(premium.getId(),
            new CreateReminderRequest(email.getId(), past, "x")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("futuro");
    }

    @Test
    void update_rejectsPastDate() {
        ReminderResponse created = service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "x"));

        assertThatThrownBy(() -> service.update(premium.getId(), created.id(),
            new UpdateReminderRequest(LocalDateTime.now().minusHours(1), null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- clearCompleted ---

    @Test
    void clearCompleted_removesOnlyDoneReminders() {
        Email e1 = freshEmail(premium, "g-c1");
        Email e2 = freshEmail(premium, "g-c2");
        Email e3 = freshEmail(premium, "g-c3");
        ReminderResponse r1 = service.create(premium.getId(), new CreateReminderRequest(e1.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "p1"));
        ReminderResponse r2 = service.create(premium.getId(), new CreateReminderRequest(e2.getId(), LocalDateTime.of(2026, 6, 24, 16, 0), "p2"));
        ReminderResponse r3 = service.create(premium.getId(), new CreateReminderRequest(e3.getId(), LocalDateTime.of(2026, 6, 24, 17, 0), "p3"));
        // Mark r1 and r2 as done.
        service.update(premium.getId(), r1.id(), new UpdateReminderRequest(null, null, true));
        service.update(premium.getId(), r2.id(), new UpdateReminderRequest(null, null, true));
        // r3 stays pending.

        int deleted = service.clearCompleted(premium.getId());

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findById(r1.id())).isEmpty();
        assertThat(repository.findById(r2.id())).isEmpty();
        assertThat(repository.findById(r3.id())).isPresent();
    }

    @Test
    void clearCompleted_doesNotAffectOtherUsers() {
        User other = userRepository.save(User.builder()
            .googleId("g-other2").email("o2@example.com").name("O2").role(Role.PREMIUM).build());
        Email own = freshEmail(premium, "g-own");
        Email otherMail = freshEmail(other, "g-other");
        ReminderResponse r1 = service.create(premium.getId(), new CreateReminderRequest(own.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "own"));
        ReminderResponse r2 = service.create(other.getId(), new CreateReminderRequest(otherMail.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "other"));
        service.update(premium.getId(), r1.id(), new UpdateReminderRequest(null, null, true));
        service.update(other.getId(), r2.id(), new UpdateReminderRequest(null, null, true));

        int deleted = service.clearCompleted(premium.getId());

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(r2.id())).isPresent();
    }

    @Test
    void clearCompleted_returnsZeroWhenNothingDone() {
        service.create(premium.getId(), new CreateReminderRequest(
            email.getId(), LocalDateTime.of(2026, 6, 24, 15, 0), "p"));

        int deleted = service.clearCompleted(premium.getId());

        assertThat(deleted).isZero();
    }

    private Email freshEmail(User owner, String gmailId) {
        return emailRepository.save(Email.builder()
            .gmailId(gmailId).sender("a@b.com").subject("Subj")
            .snippet("snip").contentForAnalysis("body")
            .receivedAt(LocalDateTime.now())
            .originalDateHeader("now")
            .user(owner).build());
    }
}
