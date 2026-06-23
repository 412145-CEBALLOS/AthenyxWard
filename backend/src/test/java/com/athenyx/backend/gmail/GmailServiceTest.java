package com.athenyx.backend.gmail;

import com.athenyx.backend.dto.EmailImportantToggleResponse;
import com.athenyx.backend.dto.EmailSummary;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private GmailService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new GmailService(userRepository, emailRepository,
                gmailPageTokenRepository, emailAnalysisRepository, tokenEncryptionService);
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
        when(emailRepository.findByUserIdAndIsImportantTrueOrderByReceivedAtDesc(1L))
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
        when(emailRepository.findByUserIdAndIsImportantTrueOrderByReceivedAtDesc(1L))
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
    void getImportantEmails_populatesRiskFromLatestAnalysis() {
        when(userRepository.existsById(1L)).thenReturn(true);
        Email email = Email.builder()
                .id(10L).gmailId("gid1").sender("a@b.com").senderName("A")
                .subject("Subj").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(true).user(user)
                .fetchedAt(LocalDateTime.now())
                .build();
        when(emailRepository.findByUserIdAndIsImportantTrueOrderByReceivedAtDesc(1L))
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
        when(emailRepository.findByUserIdAndIsImportantTrueOrderByReceivedAtDesc(1L))
                .thenReturn(List.of(email));
        when(emailAnalysisRepository.findLatestByEmailIds(List.of(10L)))
                .thenReturn(List.of());

        List<EmailSummary> result = service.getImportantEmails(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).riskPercentage()).isNull();
        assertThat(result.get(0).riskLevel()).isNull();
    }
}
