package com.athenyx.backend.ai;

import com.athenyx.backend.dto.AiExplanationResponse;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.heuristics.ThreatLevel;
import com.athenyx.backend.heuristics.TrialLimitExceededException;
import com.athenyx.backend.repository.AiExplanationRepository;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CompletionException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiExplanationService} covering the 5 scenarios
 * defined in US 3.2 plus the foreign-email access check.
 */
@ExtendWith(MockitoExtension.class)
class AiExplanationServiceTest {

    @Mock
    private org.springframework.ai.chat.client.ChatClient chatClient;

    @Mock
    private org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec userSpec;

    @Mock
    private org.springframework.ai.chat.client.ChatClient.CallResponseSpec callResponse;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private EmailRepository emailRepository;

    @Mock
    private EmailAnalysisRepository analysisRepository;

    @Mock
    private AiExplanationRepository aiExplanationRepository;

    @Mock
    private UserRepository userRepository;

    private AiExplanationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-07-07T10:00:00Z"), ZoneOffset.UTC);

    private User premiumUser;
    private User trialUserAtLimit;
    private Email email;

    @BeforeEach
    void setUp() {
        service = new AiExplanationService(
                chatClient, aiProperties, emailRepository,
                analysisRepository, aiExplanationRepository,
                userRepository, objectMapper, fixedClock);

        premiumUser = User.builder()
                .id(1L).googleId("gid").email("u@example.com").name("U")
                .role(Role.PREMIUM).analysisCount(0)
                .build();

        trialUserAtLimit = User.builder()
                .id(2L).googleId("gid2").email("trial@example.com").name("T")
                .role(Role.TRIAL).analysisCount(20)
                .build();

        email = Email.builder()
                .id(10L).gmailId("msg-1").sender("a@b.com").senderName("A")
                .subject("S").snippet("snip").contentForAnalysis("body")
                .receivedAt(LocalDateTime.now()).originalDateHeader("now")
                .isRead(false).isImportant(false).user(premiumUser)
                .build();
    }

    // --- Helpers ---

    private void stubLlmSuccess(String responseText) {
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenReturn(callResponse);
        when(callResponse.content()).thenReturn(responseText);
    }

    // --- Scenario 1a: No previous analysis → FALLBACK fixed, NOT called ChatClient ---

    @Test
    void noPreviousAnalysis_returnsFixedFallback_doesNotCallChatClient() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.empty());

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.text()).isEqualTo("Analiza primero el correo");
        assertThat(result.modelName()).isNull();
        verify(chatClient, never()).prompt();
    }

    @Test
    void noPreviousAnalysis_persistsRowWithFallbackOrigin() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.empty());
        ArgumentCaptor<com.athenyx.backend.entity.AiExplanation> captor =
                ArgumentCaptor.forClass(com.athenyx.backend.entity.AiExplanation.class);

        service.explain(1L, 10L).get();

        verify(aiExplanationRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(captor.getValue().getText()).isEqualTo("Analiza primero el correo");
        assertThat(captor.getValue().getModelName()).isNull();
    }

    // --- Scenario 1b: AI disabled → FALLBACK heuristic, NOT called ChatClient ---

    @Test
    void aiDisabled_returnsHeuristicFallback_doesNotCallChatClient() throws Exception {
        EmailAnalysis latest = EmailAnalysis.builder()
                .id(100L).email(email).user(premiumUser)
                .riskLevel(ThreatLevel.YELLOW).riskPercentage(65)
                .findings("[{\"rule\":\"ScamLanguage\",\"description\":\"Urgency\",\"score\":30}]")
                .recommendedActions("[{\"label\":\"No hacer clic en los enlaces\"}]")
                .analyzedAt(LocalDateTime.now(fixedClock))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(aiProperties.enabled()).thenReturn(false);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.text()).contains("heurístico");
        assertThat(result.modelName()).isNull();
        verify(chatClient, never()).prompt();
    }

    // --- Scenario 1c: Ollama throws → FALLBACK heuristic ---

    @Test
    void ollamaThrows_returnsHeuristicFallback() throws Exception {
        EmailAnalysis latest = EmailAnalysis.builder()
                .id(100L).email(email).user(premiumUser)
                .riskLevel(ThreatLevel.RED).riskPercentage(85)
                .findings("[{\"rule\":\"FakeLogin\",\"description\":\"Fake bank link\",\"score\":50}]")
                .recommendedActions("[{\"label\":\"Marcar como phishing y eliminar\"}]")
                .analyzedAt(LocalDateTime.now(fixedClock))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(aiProperties.enabled()).thenReturn(true);
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenThrow(new RuntimeException("Connection refused"));

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.text()).contains("heurístico");
        assertThat(result.modelName()).isNull();
    }

    // --- Scenario 1d: Ollama empty response → FALLBACK heuristic ---

    @Test
    void ollamaReturnsEmptyString_returnsHeuristicFallback() throws Exception {
        EmailAnalysis latest = EmailAnalysis.builder()
                .id(100L).email(email).user(premiumUser)
                .riskLevel(ThreatLevel.YELLOW).riskPercentage(50)
                .findings("[]")
                .recommendedActions("[{\"label\":\"No se requieren acciones especiales\"}]")
                .analyzedAt(LocalDateTime.now(fixedClock))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(aiProperties.enabled()).thenReturn(true);
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenReturn(callResponse);
        when(callResponse.content()).thenReturn("   ");

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.modelName()).isNull();
    }

    // --- Scenario 2: TRIAL at limit → throws TrialLimitExceeded ---

    @Test
    void trialUserAtLimit_throwsTrialLimitExceeded() {
        Email trialEmail = Email.builder()
                .id(20L).gmailId("msg-2").sender("c@d.com").senderName("C")
                .subject("S2").snippet("snip2").contentForAnalysis("body2")
                .isRead(false).isImportant(false).user(trialUserAtLimit)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(trialUserAtLimit));
        when(emailRepository.findById(20L)).thenReturn(Optional.of(trialEmail));

        assertThatThrownBy(() -> service.explain(2L, 20L).join())
                .isInstanceOf(TrialLimitExceededException.class);
    }

    // --- Scenario 3: LLM success → LLM response, persisted ---

    @Test
    void llmEnabled_returnsLlmExplanation_persistsRowWithLlmOrigin() throws Exception {
        EmailAnalysis latest = EmailAnalysis.builder()
                .id(100L).email(email).user(premiumUser)
                .riskLevel(ThreatLevel.YELLOW).riskPercentage(65)
                .findings("[{\"rule\":\"Urgency\",\"description\":\" ASAP\",\"score\":20}]")
                .recommendedActions("[{\"label\":\"Contactar al remitente\"}]")
                .analyzedAt(LocalDateTime.now(fixedClock))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(aiProperties.enabled()).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenReturn(callResponse);
        when(callResponse.content())
                .thenReturn("Este correo es sospechoso porque contiene lenguaje de urgencia.");

        ArgumentCaptor<com.athenyx.backend.entity.AiExplanation> captor =
                ArgumentCaptor.forClass(com.athenyx.backend.entity.AiExplanation.class);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        verify(aiExplanationRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(AiOrigin.LLM);
        assertThat(captor.getValue().getModelName()).isEqualTo("llama3");
        assertThat(result.origin()).isEqualTo(AiOrigin.LLM);
        assertThat(result.modelName()).isEqualTo("llama3");
    }

    @Test
    void trialUser_llmSuccess_incrementsAnalysisCount() throws Exception {
        User trialUser = User.builder()
                .id(3L).googleId("gid3").email("trial2@example.com").name("T2")
                .role(Role.TRIAL).analysisCount(5)
                .build();
        Email trialEmail = Email.builder()
                .id(30L).gmailId("msg-3").sender("e@f.com").senderName("E")
                .subject("S3").snippet("snip3").contentForAnalysis("body3")
                .isRead(false).isImportant(false).user(trialUser)
                .build();
        EmailAnalysis latest = EmailAnalysis.builder()
                .id(100L).email(trialEmail).user(trialUser)
                .riskLevel(ThreatLevel.YELLOW).riskPercentage(65)
                .findings("[]")
                .recommendedActions("[]")
                .analyzedAt(LocalDateTime.now(fixedClock))
                .build();

        when(userRepository.findById(3L)).thenReturn(Optional.of(trialUser));
        when(emailRepository.findById(30L)).thenReturn(Optional.of(trialEmail));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(30L))
                .thenReturn(Optional.of(latest));
        when(aiProperties.enabled()).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenReturn(callResponse);
        when(callResponse.content()).thenReturn("Respuesta IA");

        service.explain(3L, 30L).get();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAnalysisCount()).isEqualTo(6);
    }

    // --- Security: foreign email → 403 RuntimeException ---

    @Test
    void foreignEmail_throwsAccessDenied() {
        User anotherUser = User.builder()
                .id(99L).googleId("other").email("other@example.com").name("Other")
                .role(Role.PREMIUM).analysisCount(0)
                .build();
        Email foreignEmail = Email.builder()
                .id(20L).gmailId("msg-2").sender("c@d.com").senderName("C")
                .subject("S2").snippet("snip2").contentForAnalysis("body2")
                .isRead(false).isImportant(false).user(anotherUser)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(20L)).thenReturn(Optional.of(foreignEmail));

        assertThatThrownBy(() -> service.explain(1L, 20L).join())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Acceso denegado");
    }
}
