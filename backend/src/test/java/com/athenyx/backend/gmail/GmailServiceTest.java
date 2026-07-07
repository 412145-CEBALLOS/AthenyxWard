package com.athenyx.backend.gmail;

import com.athenyx.backend.dto.EmailHideResponse;
import com.athenyx.backend.dto.EmailImportantToggleResponse;
import com.athenyx.backend.dto.EmailPageResponse;
import com.athenyx.backend.dto.EmailSummary;
import com.athenyx.backend.dto.ReminderSummary;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.heuristics.AnalysisOrigin;
import com.athenyx.backend.heuristics.ThreatLevel;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.GmailPageTokenRepository;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.security.TokenEncryptionService;
import com.athenyx.backend.service.reminder.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailRepository emailRepository;
    @Mock
    private GmailPageTokenRepository gmailPageTokenRepository;
    @Mock
    private EmailAnalysisRepository emailAnalysisRepository;
    @Mock
    private TokenEncryptionService tokenEncryptionService;
    @Mock
    private ReminderService reminderService;

    private GmailService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new GmailService(userRepository, emailRepository,
                emailAnalysisRepository, gmailPageTokenRepository, tokenEncryptionService,
                reminderService);
        // The list endpoints always invoke the reminder enrichment as
        // a safety net; individual tests override it as needed.
        lenient().when(reminderService.findSummariesForEmails(any(), any()))
                .thenReturn(java.util.Map.of());
        user = User.builder()
                .id(1L)
                .googleId("gid")
                .email("u@example.com")
                .name("User")
                .build();
    }

    @Test
    void getImportantEmails_returnsOnlyFlaggedForUser() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Email email1 = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj1").snippet("snip1").contentForAnalysis("body1")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(true).isImportant(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        Email email2 = Email.builder()
                .id(20L).gmailId("gid2").sender("c@d.com").senderName("C")
                .subject("Subj2").snippet("snip2").contentForAnalysis("body2")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findByUserIdAndIsImportantTrueAndIsHiddenFalseOrderByReceivedAtDesc(1L))
                .thenReturn(List.of(email1, email2));

        List<EmailSummary> result = service.getImportantEmails(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).isImportant()).isTrue();
        assertThat(result.get(1).id()).isEqualTo(20L);
        assertThat(result.get(1).isImportant()).isTrue();
    }

    @Test
    void getImportantEmails_emptyListWhenNoneFlagged() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(emailRepository.findByUserIdAndIsImportantTrueAndIsHiddenFalseOrderByReceivedAtDesc(1L))
                .thenReturn(List.of());

        List<EmailSummary> result = service.getImportantEmails(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getImportantEmails_throwsWhenUserNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getImportantEmails(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void getImportantEmailCount_returnsCount() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(emailRepository.countByUserIdAndIsImportantTrue(1L)).thenReturn(5L);

        long count = service.getImportantEmailCount(1L);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void getImportantEmailCount_throwsWhenUserNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getImportantEmailCount(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void toggleImportant_flipsFlagFromFalseToTrue() {
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(false).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));

        EmailImportantToggleResponse result = service.toggleImportant(1L, 10L);

        assertThat(result.emailId()).isEqualTo(10L);
        assertThat(result.isImportant()).isTrue();
        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository).save(captor.capture());
        assertThat(captor.getValue().isImportant()).isTrue();
    }

    @Test
    void toggleImportant_flipsFlagFromTrueToFalse() {
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));

        EmailImportantToggleResponse result = service.toggleImportant(1L, 10L);

        assertThat(result.isImportant()).isFalse();
        assertThat(email.isImportant()).isFalse();
    }

    @Test
    void toggleImportant_throwsWhenEmailBelongsToAnotherUser() {
        User otherUser = User.builder().id(99L).build();
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(false).user(otherUser)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));

        assertThatThrownBy(() -> service.toggleImportant(1L, 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Acceso denegado");
    }

    @Test
    void toggleImportant_throwsWhenEmailNotFound() {
        when(emailRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleImportant(1L, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Correo no encontrado");
    }

    @Test
    void hide_setsIsHiddenTrue() {
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isHidden(false).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));

        var result = service.hide(1L, 10L);

        assertThat(result.emailId()).isEqualTo(10L);
        assertThat(result.isHidden()).isTrue();
        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository).save(captor.capture());
        assertThat(captor.getValue().isHidden()).isTrue();
    }

    @Test
    void hide_throwsWhenEmailBelongsToAnotherUser() {
        User otherUser = User.builder().id(99L).build();
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isHidden(false).user(otherUser)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));

        assertThatThrownBy(() -> service.hide(1L, 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Acceso denegado");
    }

    @Test
    void hide_throwsWhenEmailNotFound() {
        when(emailRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hide(1L, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Correo no encontrado");
    }

    @Test
    void unhide_setsIsHiddenFalse() {
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isHidden(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));

        var result = service.unhide(1L, 10L);

        assertThat(result.emailId()).isEqualTo(10L);
        assertThat(result.isHidden()).isFalse();
        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository).save(captor.capture());
        assertThat(captor.getValue().isHidden()).isFalse();
    }

    @Test
    void unhide_throwsWhenEmailBelongsToAnotherUser() {
        User otherUser = User.builder().id(99L).build();
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isHidden(true).user(otherUser)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));

        assertThatThrownBy(() -> service.unhide(1L, 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Acceso denegado");
    }

    @Test
    void unhide_throwsWhenEmailNotFound() {
        when(emailRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unhide(1L, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Correo no encontrado");
    }

    @Test
    void getHiddenEmails_returnsHiddenEmails() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Email email1 = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj1").snippet("snip1").contentForAnalysis("body1")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isHidden(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        Email email2 = Email.builder()
                .id(20L).gmailId("gid2").sender("c@d.com").senderName("C")
                .subject("Subj2").snippet("snip2").contentForAnalysis("body2")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(true).isHidden(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findByUserIdAndIsHiddenTrueOrderByReceivedAtDesc(1L))
                .thenReturn(List.of(email1, email2));

        var result = service.getHiddenEmails(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isHidden()).isTrue();
        assertThat(result.get(1).isHidden()).isTrue();
    }

    @Test
    void getHiddenEmails_returnsEmptyListWhenNoneHidden() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(emailRepository.findByUserIdAndIsHiddenTrueOrderByReceivedAtDesc(1L))
                .thenReturn(List.of());

        var result = service.getHiddenEmails(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getHiddenEmails_throwsWhenUserNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getHiddenEmails(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void getImportantEmails_populatesRiskFromLatestAnalysis() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findByUserIdAndIsImportantTrueAndIsHiddenFalseOrderByReceivedAtDesc(1L))
                .thenReturn(List.of(email));
        EmailAnalysis analysis = EmailAnalysis.builder()
                .id(99L)
                .email(email)
                .user(user)
                .origin(AnalysisOrigin.HEURISTIC)
                .riskLevel(ThreatLevel.RED)
                .riskPercentage(85)
                .build();
        when(emailAnalysisRepository.findLatestByEmailIds(List.of(10L)))
                .thenReturn(List.of(analysis));

        List<EmailSummary> result = service.getImportantEmails(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).riskPercentage()).isEqualTo(85);
        assertThat(result.get(0).riskLevel()).isEqualTo(ThreatLevel.RED);
    }

    @Test
    void getImportantEmails_leavesRiskNullWhenNoAnalysisExists() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findByUserIdAndIsImportantTrueAndIsHiddenFalseOrderByReceivedAtDesc(1L))
                .thenReturn(List.of(email));
        when(emailAnalysisRepository.findLatestByEmailIds(List.of(10L)))
                .thenReturn(List.of());

        List<EmailSummary> result = service.getImportantEmails(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).riskPercentage()).isNull();
        assertThat(result.get(0).riskLevel()).isNull();
    }

    // --- US 3.7 — searchEmails -------------------------------------

    private Email makeEmail(long id, String subject, String sender, String senderName, String snippet) {
        return Email.builder()
                .id(id).gmailId("g" + id).sender(sender).senderName(senderName)
                .subject(subject).snippet(snippet).contentForAnalysis("body" + id)
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(false).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void searchEmails_withQuery_callsRepositoryWithTrimmedTermAndSize20() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Page<Email> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(emailRepository.searchByUserAndTerm(eq(1L), eq("paypal"), any(Pageable.class)))
                .thenReturn(empty);

        EmailPageResponse response = service.searchEmails(1L, 0, "  paypal  ");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(emailRepository).searchByUserAndTerm(eq(1L), eq("paypal"), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(response.emails()).isEmpty();
        assertThat(response.hasNextPage()).isFalse();
        assertThat(response.currentPage()).isEqualTo(0);
        assertThat(response.pageSize()).isEqualTo(20);
    }

    @Test
    void searchEmails_blankQuery_fallsBackToGmailPath() {
        // With a blank q, the repository is never touched and the
        // service must attempt the Gmail live path. We can't drive
        // the live path here without a real Gmail client, so the
        // simplest contract we can verify is "no DB query" — the
        // Gmail call itself will throw, which is fine.
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.searchEmails(1L, 0, "   "))
                .isInstanceOf(RuntimeException.class);
        verify(emailRepository, never())
                .searchByUserAndTerm(anyLong(), anyString(), any(Pageable.class));
    }

    @Test
    void searchEmails_nullQuery_fallsBackToGmailPath() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.searchEmails(1L, 0, (String) null))
                .isInstanceOf(RuntimeException.class);
        verify(emailRepository, never())
                .searchByUserAndTerm(anyLong(), anyString(), any(Pageable.class));
    }

    @Test
    void searchEmails_returnsPersistedResultsEnrichedWithRiskAndReminder() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Email e1 = makeEmail(10L, "PayPal receipt", "noreply@paypal.com", "PayPal", "you got money");
        Email e2 = makeEmail(20L, "Your invoice", "billing@acme.com", "Acme", "thanks");
        Page<Email> page = new PageImpl<>(List.of(e1, e2),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "receivedAt")), 2);
        when(emailRepository.searchByUserAndTerm(eq(1L), eq("pay"), any(Pageable.class)))
                .thenReturn(page);

        EmailAnalysis analysis = EmailAnalysis.builder()
                .id(99L).email(e1).user(user)
                .origin(AnalysisOrigin.HEURISTIC)
                .riskLevel(ThreatLevel.RED).riskPercentage(85)
                .build();
        when(emailAnalysisRepository.findLatestByEmailIds(List.of(10L, 20L)))
                .thenReturn(List.of(analysis));
        ReminderSummary reminder = new ReminderSummary(7L,
                LocalDateTime.now().plusDays(1), false);
        when(reminderService.findSummariesForEmails(eq(1L), eq(List.of(10L, 20L))))
                .thenReturn(Map.of(10L, reminder));

        EmailPageResponse response = service.searchEmails(1L, 0, "pay");

        assertThat(response.emails()).hasSize(2);
        assertThat(response.hasNextPage()).isFalse();
        assertThat(response.currentPage()).isEqualTo(0);
        assertThat(response.pageSize()).isEqualTo(20);
        // e1 picked up risk + reminder from the enrichment pipeline
        assertThat(response.emails().get(0).riskPercentage()).isEqualTo(85);
        assertThat(response.emails().get(0).riskLevel()).isEqualTo(ThreatLevel.RED);
        assertThat(response.emails().get(0).reminder()).isEqualTo(reminder);
        // e2 has no analysis and no reminder — slots remain null
        assertThat(response.emails().get(1).riskPercentage()).isNull();
        assertThat(response.emails().get(1).riskLevel()).isNull();
        assertThat(response.emails().get(1).reminder()).isNull();
    }

    @Test
    void searchEmails_emptyResultReturnsEmptyListAndNoNextPage() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(emailRepository.searchByUserAndTerm(eq(1L), eq("nothing"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(),
                        PageRequest.of(0, 20,
                                Sort.by(Sort.Direction.DESC, "receivedAt")), 0));

        EmailPageResponse response = service.searchEmails(1L, 0, "nothing");

        assertThat(response.emails()).isEmpty();
        assertThat(response.hasNextPage()).isFalse();
    }

    @Test
    void searchEmails_hasNextTrueWhenPageHasMore() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Email e1 = makeEmail(10L, "Foo", "a@b.com", "A", "x");
        // PageImpl with totalElements > pageSize → hasNext() == true
        Page<Email> page = new PageImpl<>(List.of(e1),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "receivedAt")), 21);
        when(emailRepository.searchByUserAndTerm(eq(1L), eq("foo"), any(Pageable.class)))
                .thenReturn(page);

        EmailPageResponse response = service.searchEmails(1L, 0, "foo");

        assertThat(response.hasNextPage()).isTrue();
    }

    @Test
    void searchEmails_throwsWhenUserNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.searchEmails(99L, 0, "foo"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void searchEmails_respectsRequestedSize() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(emailRepository.searchByUserAndTerm(eq(1L), eq("foo"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(),
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "receivedAt")), 0));

        EmailPageResponse response = service.searchEmails(1L, 0, "foo", 8);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(emailRepository).searchByUserAndTerm(eq(1L), eq("foo"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(8);
        assertThat(response.pageSize()).isEqualTo(8);
    }

    @Test
    void searchEmails_clampsSizeAboveMaxTo50() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(emailRepository.searchByUserAndTerm(eq(1L), eq("foo"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(),
                        PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "receivedAt")), 0));

        EmailPageResponse response = service.searchEmails(1L, 0, "foo", 999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(emailRepository).searchByUserAndTerm(eq(1L), eq("foo"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(response.pageSize()).isEqualTo(50);
    }

    @Test
    void searchEmails_clampsSizeBelowOneToOne() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(emailRepository.searchByUserAndTerm(eq(1L), eq("foo"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(),
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "receivedAt")), 0));

        EmailPageResponse response = service.searchEmails(1L, 0, "foo", 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(emailRepository).searchByUserAndTerm(eq(1L), eq("foo"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(1);
    }

    @Test
    void searchEmails_nullSizeDefaultsTo20() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(emailRepository.searchByUserAndTerm(eq(1L), eq("foo"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(),
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "receivedAt")), 0));

        EmailPageResponse response = service.searchEmails(1L, 0, "foo", null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(emailRepository).searchByUserAndTerm(eq(1L), eq("foo"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(response.pageSize()).isEqualTo(20);
    }
}
