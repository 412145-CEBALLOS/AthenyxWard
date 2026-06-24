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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReminderService}. All collaborators are
 * mocked; we only exercise the service's business rules.
 */
@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock private ReminderRepository repository;
    @Mock private EmailRepository emailRepository;
    @Mock private UserRepository userRepository;

    private ReminderService service;

    private User premium;
    private User trial;
    private User otherUser;
    private Email email;
    private Reminder persisted;

    @BeforeEach
    void setUp() {
        service = new ReminderService(repository, emailRepository, userRepository);

        premium = User.builder().id(1L).email("p@example.com").role(Role.PREMIUM).build();
        trial = User.builder().id(2L).email("t@example.com").role(Role.TRIAL).build();
        otherUser = User.builder().id(3L).email("o@example.com").role(Role.PREMIUM).build();

        email = Email.builder()
            .id(10L).gmailId("g-1").sender("a@b.com").subject("Subj").user(premium)
            .contentForAnalysis("x").build();

        persisted = Reminder.builder()
            .id(100L).user(premium).email(email)
            .reminderDate(LocalDateTime.of(2026, 6, 24, 10, 0))
            .message("Llamar").done(false)
            .createdAt(LocalDateTime.of(2026, 6, 22, 9, 0))
            .updatedAt(LocalDateTime.of(2026, 6, 22, 9, 0))
            .build();
    }

    // --- create ---

    @Test
    void create_persistsAndReturnsResponse() {
        CreateReminderRequest req = new CreateReminderRequest(
            10L, LocalDateTime.of(2026, 6, 24, 10, 0), "  Llamar  ");
        when(userRepository.findById(1L)).thenReturn(Optional.of(premium));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(repository.existsByEmailIdAndUserId(10L, 1L)).thenReturn(false);
        when(repository.save(any(Reminder.class))).thenReturn(persisted);

        ReminderResponse response = service.create(1L, req);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(premium);
        assertThat(captor.getValue().getEmail()).isEqualTo(email);
        assertThat(captor.getValue().isDone()).isFalse();
        assertThat(captor.getValue().getMessage()).isEqualTo("Llamar");

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.emailId()).isEqualTo(10L);
        assertThat(response.message()).isEqualTo("Llamar");
    }

    @Test
    void create_rejectsTrialUserWithPremiumRequiredException() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(trial));

        CreateReminderRequest req = new CreateReminderRequest(
            10L, LocalDateTime.of(2026, 6, 24, 10, 0), null);

        assertThatThrownBy(() -> service.create(2L, req))
            .isInstanceOf(ReminderPremiumRequiredException.class)
            .hasMessageContaining("Premium");

        verify(repository, never()).save(any());
    }

    @Test
    void create_throwsWhenEmailDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(premium));
        when(emailRepository.findById(10L)).thenReturn(Optional.empty());

        CreateReminderRequest req = new CreateReminderRequest(
            10L, LocalDateTime.of(2026, 6, 24, 10, 0), null);

        assertThatThrownBy(() -> service.create(1L, req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Correo no encontrado");
    }

    @Test
    void create_throwsWhenEmailBelongsToAnotherUser() {
        email.setUser(otherUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(premium));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));

        CreateReminderRequest req = new CreateReminderRequest(
            10L, LocalDateTime.of(2026, 6, 24, 10, 0), null);

        assertThatThrownBy(() -> service.create(1L, req))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Acceso denegado");
    }

    @Test
    void create_throwsConflictWhenReminderAlreadyExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(premium));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(repository.existsByEmailIdAndUserId(10L, 1L)).thenReturn(true);

        CreateReminderRequest req = new CreateReminderRequest(
            10L, LocalDateTime.of(2026, 6, 24, 10, 0), null);

        assertThatThrownBy(() -> service.create(1L, req))
            .isInstanceOf(ReminderConflictException.class)
            .hasMessageContaining("Ya tienes");

        verify(repository, never()).save(any());
    }

    @Test
    void create_normalizesBlankMessageToNull() {
        CreateReminderRequest req = new CreateReminderRequest(
            10L, LocalDateTime.of(2026, 6, 24, 10, 0), "   ");
        when(userRepository.findById(1L)).thenReturn(Optional.of(premium));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(repository.existsByEmailIdAndUserId(10L, 1L)).thenReturn(false);
        when(repository.save(any(Reminder.class))).thenReturn(persisted);

        service.create(1L, req);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isNull();
    }

    // --- update ---

    @Test
    void update_appliesPartialPatch() {
        UpdateReminderRequest req = new UpdateReminderRequest(
            LocalDateTime.of(2026, 6, 25, 9, 0), null, null);
        when(repository.findById(100L)).thenReturn(Optional.of(persisted));
        when(repository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        ReminderResponse response = service.update(1L, 100L, req);

        assertThat(response.reminderDate()).isEqualTo(LocalDateTime.of(2026, 6, 25, 9, 0));
        assertThat(persisted.getMessage()).isEqualTo("Llamar");
        assertThat(persisted.isDone()).isFalse();
    }

    @Test
    void update_togglesDoneFlag() {
        UpdateReminderRequest req = new UpdateReminderRequest(null, null, true);
        when(repository.findById(100L)).thenReturn(Optional.of(persisted));
        when(repository.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));

        ReminderResponse response = service.update(1L, 100L, req);

        assertThat(response.done()).isTrue();
        assertThat(persisted.isDone()).isTrue();
    }

    @Test
    void update_throwsNotFoundWhenIdMissing() {
        when(repository.findById(100L)).thenReturn(Optional.empty());
        UpdateReminderRequest req = new UpdateReminderRequest(null, "x", null);

        assertThatThrownBy(() -> service.update(1L, 100L, req))
            .isInstanceOf(ReminderNotFoundException.class);
    }

    @Test
    void update_throwsNotFoundWhenOwnedByAnotherUser() {
        persisted.setUser(otherUser);
        when(repository.findById(100L)).thenReturn(Optional.of(persisted));
        UpdateReminderRequest req = new UpdateReminderRequest(null, "x", null);

        assertThatThrownBy(() -> service.update(1L, 100L, req))
            .isInstanceOf(ReminderNotFoundException.class);
        verify(repository, never()).save(any());
    }

    // --- delete ---

    @Test
    void delete_removesReminder() {
        when(repository.findById(100L)).thenReturn(Optional.of(persisted));

        service.delete(1L, 100L);

        verify(repository).delete(persisted);
    }

    @Test
    void delete_throwsNotFoundWhenOwnedByAnotherUser() {
        persisted.setUser(otherUser);
        when(repository.findById(100L)).thenReturn(Optional.of(persisted));

        assertThatThrownBy(() -> service.delete(1L, 100L))
            .isInstanceOf(ReminderNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    // --- findByUser ---

    @Test
    void findByUser_allReturnsAll() {
        when(repository.findByUserIdOrderByReminderDateAsc(1L)).thenReturn(List.of(persisted));

        List<ReminderResponse> items = service.findByUser(1L, ReminderService.Filter.ALL);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).id()).isEqualTo(100L);
    }

    @Test
    void findByUser_pendingFiltersByDoneFalse() {
        when(repository.findByUserIdAndDoneOrderByReminderDateAsc(1L, false))
            .thenReturn(List.of(persisted));

        List<ReminderResponse> items = service.findByUser(1L, ReminderService.Filter.PENDING);

        assertThat(items).hasSize(1);
        verify(repository).findByUserIdAndDoneOrderByReminderDateAsc(1L, false);
        verify(repository, never()).findByUserIdAndDoneOrderByReminderDateAsc(1L, true);
    }

    @Test
    void findByUser_doneFiltersByDoneTrue() {
        when(repository.findByUserIdAndDoneOrderByReminderDateAsc(1L, true))
            .thenReturn(List.of());

        List<ReminderResponse> items = service.findByUser(1L, ReminderService.Filter.DONE);

        assertThat(items).isEmpty();
        verify(repository).findByUserIdAndDoneOrderByReminderDateAsc(1L, true);
    }

    // --- findSummaryByEmail ---

    @Test
    void findSummaryByEmail_returnsNullWhenNoneExists() {
        when(repository.findByEmailIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        ReminderSummary summary = service.findSummaryByEmail(1L, 10L);

        assertThat(summary).isNull();
    }

    @Test
    void findSummaryByEmail_returnsSummaryWhenExists() {
        when(repository.findByEmailIdAndUserId(10L, 1L)).thenReturn(Optional.of(persisted));

        ReminderSummary summary = service.findSummaryByEmail(1L, 10L);

        assertThat(summary).isNotNull();
        assertThat(summary.id()).isEqualTo(100L);
        assertThat(summary.done()).isFalse();
    }

    // --- findSummariesForEmails ---

    @Test
    void findSummariesForEmails_returnsEmptyWhenInputEmpty() {
        Map<Long, ReminderSummary> map = service.findSummariesForEmails(1L, List.of());
        assertThat(map).isEmpty();
        verify(repository, never()).findByUserIdAndEmailIdIn(any(), any());
    }

    @Test
    void findSummariesForEmails_groupsByEmailId() {
        when(repository.findByUserIdAndEmailIdIn(eq(1L), any(Collection.class)))
            .thenReturn(List.of(persisted));

        Map<Long, ReminderSummary> map = service.findSummariesForEmails(1L, List.of(10L, 20L));

        assertThat(map).containsOnlyKeys(10L);
        assertThat(map.get(10L).id()).isEqualTo(100L);
    }

    // --- Filter.parse ---

    @Test
    void filterParse_handlesAllKnownVariants() {
        assertThat(ReminderService.Filter.parse(null)).isEqualTo(ReminderService.Filter.ALL);
        assertThat(ReminderService.Filter.parse("")).isEqualTo(ReminderService.Filter.ALL);
        assertThat(ReminderService.Filter.parse("ALL")).isEqualTo(ReminderService.Filter.ALL);
        assertThat(ReminderService.Filter.parse("pending")).isEqualTo(ReminderService.Filter.PENDING);
        assertThat(ReminderService.Filter.parse("PENDIENTES")).isEqualTo(ReminderService.Filter.PENDING);
        assertThat(ReminderService.Filter.parse("done")).isEqualTo(ReminderService.Filter.DONE);
        assertThat(ReminderService.Filter.parse("completados")).isEqualTo(ReminderService.Filter.DONE);
        assertThat(ReminderService.Filter.parse("garbage")).isEqualTo(ReminderService.Filter.ALL);
    }

    @SuppressWarnings("unused")
    private void unusedSuppressUnusedWarning() { anyBoolean(); }
}
