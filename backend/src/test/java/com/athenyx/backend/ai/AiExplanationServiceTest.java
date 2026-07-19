package com.athenyx.backend.ai;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.security.FeatureToggleService;
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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Mock
    private ConfigService configService;

    @Mock
    private FeatureToggleService featureToggleService;

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
                userRepository, objectMapper, fixedClock, configService, featureToggleService);

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

    private void stubLlmSuccess(String jsonResponse) {
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenReturn(callResponse);
        when(callResponse.content()).thenReturn(jsonResponse);
    }

    private ListAppender<ILoggingEvent> installLogCaptor() {
        Logger logger = (Logger) LoggerFactory.getLogger(AiExplanationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void assertStructuredLog(ListAppender<ILoggingEvent> appender,
                                     Long expectedUserId, Long expectedEmailId,
                                     AiOrigin expectedOrigin, boolean expectError) {
        var events = appender.list;
        assertThat(events).hasSizeGreaterThan(0);
        var event = events.get(events.size() - 1);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String msg = event.getFormattedMessage();
        assertThat(msg).contains("ai.explain");
        assertThat(msg).contains("userId=" + expectedUserId);
        assertThat(msg).contains("emailId=" + expectedEmailId);
        assertThat(msg).contains("origin=" + expectedOrigin);
        if (expectError) {
            assertThat(msg).contains("error=");
        }
    }

    private EmailAnalysis makeAnalysis() {
        return EmailAnalysis.builder()
                .id(100L).email(email).user(premiumUser)
                .riskLevel(ThreatLevel.YELLOW).riskPercentage(65)
                .findings("[{\"rule\":\"Urgency\",\"description\":\" Urgency\",\"score\":20}]")
                .recommendedActions("[{\"label\":\"Contactar al remitente\"}]")
                .analyzedAt(LocalDateTime.now(fixedClock))
                .build();
    }

    // --- Scenario 1: No previous analysis → FALLBACK with 3 null fields ---

    @Test
    void noPreviousAnalysis_returnsFallbackWithAllNulls() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.empty());

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.summary()).isNull();
        assertThat(result.heuristicExplanation()).isNull();
        assertThat(result.secondOpinion()).isNull();
        assertThat(result.modelName()).isNull();
        verify(chatClient, never()).prompt();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, false);
    }

    @Test
    void noPreviousAnalysis_persistsRowWithFallbackOriginAndNullFields() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.empty());
        ArgumentCaptor<com.athenyx.backend.entity.AiExplanation> captor =
                ArgumentCaptor.forClass(com.athenyx.backend.entity.AiExplanation.class);

        service.explain(1L, 10L).get();

        verify(aiExplanationRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(captor.getValue().getSummary()).isNull();
        assertThat(captor.getValue().getHeuristicExplanation()).isNull();
        assertThat(captor.getValue().getSecondOpinion()).isNull();
        assertThat(captor.getValue().getModelName()).isNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, false);
    }

    // --- Scenario 2: AI disabled → FALLBACK with heuristic values ---

    @Test
    void aiDisabled_returnsHeuristicFallback() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(false);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.summary()).isNotNull();
        assertThat(result.heuristicExplanation()).isNotNull();
        assertThat(result.heuristicExplanation()).contains("heurístico");
        assertThat(result.secondOpinion()).isNotNull();
        assertThat(result.modelName()).isNull();
        verify(chatClient, never()).prompt();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, false);
    }

    // --- Scenario 3: Ollama throws → FALLBACK with heuristic values ---

    @Test
    void ollamaThrows_returnsHeuristicFallback() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenThrow(new RuntimeException("Connection refused"));

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.heuristicExplanation()).isNotNull();
        assertThat(result.heuristicExplanation()).contains("heurístico");
        assertThat(result.modelName()).isNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, true);
    }

    // --- Scenario 4: Ollama empty response → FALLBACK ---

    @Test
    void ollamaReturnsEmptyString_returnsHeuristicFallback() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenReturn(callResponse);
        when(callResponse.content()).thenReturn("   ");

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.modelName()).isNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, true);
    }

    // --- Scenario 5: Ollama timeout → FALLBACK ---

    @Test
    void ollamaTimeout_returnsHeuristicFallback() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(chatClient.prompt()).thenReturn(userSpec);
        when(userSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenThrow(new AiUnavailableException("timeout"));

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.heuristicExplanation()).isNotNull();
        assertThat(result.heuristicExplanation()).contains("heurístico");
        assertThat(result.modelName()).isNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, true);
    }

    // --- Scenario 6: LLM returns valid JSON with all 3 fields → LLM origin ---

    @Test
    void llmReturnsValidJson_persistsAllThreeFields() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String jsonResponse = """
            {
              "summary": "Correo疑似 phishing.",
              "heuristicExplanation": "Contiene lenguaje de urgencia.",
              "secondOpinion": "De acuerdo con el riesgo."
            }""";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        stubLlmSuccess(jsonResponse);

        ArgumentCaptor<com.athenyx.backend.entity.AiExplanation> captor =
                ArgumentCaptor.forClass(com.athenyx.backend.entity.AiExplanation.class);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        verify(aiExplanationRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(AiOrigin.LLM);
        assertThat(captor.getValue().getSummary()).isEqualTo("Correo疑似 phishing.");
        assertThat(captor.getValue().getHeuristicExplanation()).isEqualTo("Contiene lenguaje de urgencia.");
        assertThat(captor.getValue().getSecondOpinion()).isEqualTo("De acuerdo con el riesgo.");
        assertThat(captor.getValue().getModelName()).isEqualTo("llama3");
        assertThat(result.origin()).isEqualTo(AiOrigin.LLM);
        assertThat(result.summary()).isEqualTo("Correo疑似 phishing.");
        assertThat(result.heuristicExplanation()).isEqualTo("Contiene lenguaje de urgencia.");
        assertThat(result.secondOpinion()).isEqualTo("De acuerdo con el riesgo.");
        assertThat(result.modelName()).isEqualTo("llama3");
        assertStructuredLog(appender, 1L, 10L, AiOrigin.LLM, false);
    }

    // --- Scenario 7: LLM returns partial JSON (only 2 fields) → LLM origin, 1 field null ---

    @Test
    void llmReturnsPartialJson_persistsOnlyNonEmptyFields() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String jsonResponse = """
            {
              "summary": "Correo sospechoso.",
              "heuristicExplanation": "Lenguaje de urgencia."
            }""";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        stubLlmSuccess(jsonResponse);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.LLM);
        assertThat(result.summary()).isEqualTo("Correo sospechoso.");
        assertThat(result.heuristicExplanation()).isEqualTo("Lenguaje de urgencia.");
        assertThat(result.secondOpinion()).isNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.LLM, false);
    }

    // --- Scenario 8: LLM returns invalid JSON → FALLBACK with heuristic ---

    @Test
    void llmReturnsInvalidJson_returnsHeuristicFallback() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.numPredict()).thenReturn(1500);
        stubLlmSuccess("Esto no es JSON {");

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.heuristicExplanation()).isNotNull();
        assertThat(result.heuristicExplanation()).contains("heurístico");
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, true);
    }

    // --- Scenario 9: LLM returns JSON with all empty fields → FALLBACK with 3 nulls ---

    @Test
    void llmReturnsEmptySections_returnsFallbackWithAllNulls() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String jsonResponse = """
            {
              "summary": "   ",
              "heuristicExplanation": "",
              "secondOpinion": null
            }""";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        stubLlmSuccess(jsonResponse);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.summary()).isNull();
        assertThat(result.heuristicExplanation()).isNull();
        assertThat(result.secondOpinion()).isNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, true);
    }

    // --- Scenario 10: LLM returns JSON wrapped in markdown fences → parses via stripMarkdownFences ---

    @Test
    void llmReturnsMarkdownJson_parsesSuccessfully() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String markdownJson = """
            ```json
            {
              "summary": "Correo sospechoso.",
              "heuristicExplanation": "Lenguaje de urgencia.",
              "secondOpinion": "De acuerdo con el veredicto."
            }
            ```""";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        stubLlmSuccess(markdownJson);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.LLM);
        assertThat(result.summary()).isEqualTo("Correo sospechoso.");
        assertThat(result.heuristicExplanation()).isEqualTo("Lenguaje de urgencia.");
        assertThat(result.secondOpinion()).isEqualTo("De acuerdo con el veredicto.");
        assertStructuredLog(appender, 1L, 10L, AiOrigin.LLM, false);
    }

    // --- Scenario 11: LLM returns text with preamble before JSON → parses via extractFirstJsonObject ---

    @Test
    void llmReturnsJsonWithPreamble_parsesSuccessfully() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String jsonWithPreamble = """
            Aquí está el análisis en formato JSON:
            {
              "summary": "Resumen del correo.",
              "heuristicExplanation": "Indicadores detectados.",
              "secondOpinion": "Veredicto independiente."
            }
            Espero que sea útil.""";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        stubLlmSuccess(jsonWithPreamble);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.LLM);
        assertThat(result.summary()).isEqualTo("Resumen del correo.");
        assertThat(result.heuristicExplanation()).isEqualTo("Indicadores detectados.");
        assertThat(result.secondOpinion()).isEqualTo("Veredicto independiente.");
        assertStructuredLog(appender, 1L, 10L, AiOrigin.LLM, false);
    }

    // --- Scenario 12: Very long non-JSON response near num-predict threshold → logged as truncated_json ---

    @Test
    void llmReturnsVeryLongResponse_nearNumPredictThreshold_loggedAsTruncated() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String longNonJson = "A".repeat(4500);

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.numPredict()).thenReturn(1500);
        stubLlmSuccess(longNonJson);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.heuristicExplanation()).isNotNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, true);
    }

    // --- TRIAL success increments count (PREMIUM user — TRIAL is blocked by role check) ---

    @Test
    void premiumUser_llmSuccess_returnsLlmResponse() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        User premiumUser2 = User.builder()
                .id(3L).googleId("gid3").email("prem2@example.com").name("P2")
                .role(Role.PREMIUM).analysisCount(5)
                .build();
        Email premiumEmail = Email.builder()
                .id(30L).gmailId("msg-3").sender("e@f.com").senderName("E")
                .subject("S3").snippet("snip3").contentForAnalysis("body3")
                .isRead(false).isImportant(false).user(premiumUser2)
                .build();
        EmailAnalysis latest = EmailAnalysis.builder()
                .id(100L).email(premiumEmail).user(premiumUser2)
                .riskLevel(ThreatLevel.YELLOW).riskPercentage(65)
                .findings("[]")
                .recommendedActions("[]")
                .analyzedAt(LocalDateTime.now(fixedClock))
                .build();
        String jsonResponse = """
            {
              "summary": "Res.",
              "heuristicExplanation": "Exp.",
              "secondOpinion": "Op."
            }""";

        when(userRepository.findById(3L)).thenReturn(Optional.of(premiumUser2));
        when(emailRepository.findById(30L)).thenReturn(Optional.of(premiumEmail));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(30L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        stubLlmSuccess(jsonResponse);

        AiExplanationResponse result = service.explain(3L, 30L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.LLM);
        assertStructuredLog(appender, 3L, 30L, AiOrigin.LLM, false);
    }

    // --- TRIAL role check ---

    @Test
    void trialUser_throwsAiPremiumRequired() {
        User trialUser = User.builder()
                .id(4L).googleId("gid4").email("trial4@example.com").name("T4")
                .role(Role.TRIAL).analysisCount(5)
                .build();
        Email trialEmail = Email.builder()
                .id(40L).gmailId("msg-4").sender("g@h.com").senderName("G")
                .subject("S4").snippet("snip4").contentForAnalysis("body4")
                .isRead(false).isImportant(false).user(trialUser)
                .build();

        when(userRepository.findById(4L)).thenReturn(Optional.of(trialUser));
        when(emailRepository.findById(40L)).thenReturn(Optional.of(trialEmail));

        assertThatThrownBy(() -> service.explain(4L, 40L))
                .isInstanceOf(AiPremiumRequiredException.class);
    }

    // --- Safety net (unreachable for TRIAL — role check guards first) ---
    // PREMIUM user with any analysisCount proceeds to AI path.

    @Test
    void premiumUser_highAnalysisCount_proceedsToAI() throws Exception {
        User premiumAtLimit = User.builder()
                .id(5L).googleId("gid5").email("prem5@example.com").name("P5")
                .role(Role.PREMIUM).analysisCount(1000)
                .build();
        Email premiumEmail = Email.builder()
                .id(50L).gmailId("msg-5").sender("i@j.com").senderName("I")
                .subject("S5").snippet("snip5").contentForAnalysis("body5")
                .isRead(false).isImportant(false).user(premiumAtLimit)
                .build();
        EmailAnalysis latest = EmailAnalysis.builder()
                .id(200L).email(premiumEmail).user(premiumAtLimit)
                .riskLevel(ThreatLevel.YELLOW).riskPercentage(65)
                .findings("[]")
                .recommendedActions("[]")
                .analyzedAt(LocalDateTime.now(fixedClock))
                .build();
        String jsonResponse = """
            {"summary": "R.", "heuristicExplanation": "E.", "secondOpinion": "O."}""";

        when(userRepository.findById(5L)).thenReturn(Optional.of(premiumAtLimit));
        when(emailRepository.findById(50L)).thenReturn(Optional.of(premiumEmail));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(50L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        stubLlmSuccess(jsonResponse);

        service.explain(5L, 50L).get();

        verify(userRepository, never()).save(any());
    }

    // --- Security: foreign email → RuntimeException ---

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

        assertThatThrownBy(() -> service.explain(1L, 20L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Acceso denegado");
    }

    // --- Scenario 13: JSON truncated mid-string → logged as truncated_json (pattern-based) ---

    @Test
    void llmReturnsJsonTruncatedMidString_loggedAsTruncated() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String truncatedJson = """
            {
              "summary": "El correo electrónico procede de Betsson, una plataforma de apuestas, y tiene un asunto que promete una gran cantidad de dinero para jugar. El cuerpo del correo es un bloque de caracte
            }""";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.numPredict()).thenReturn(1500);
        stubLlmSuccess(truncatedJson);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.heuristicExplanation()).isNotNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, true);
        var events = appender.list;
        var warnEvent = events.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .findFirst()
                .orElseThrow();
        assertThat(warnEvent.getFormattedMessage()).contains("reason=truncated_json");
    }

    // --- Scenario 14: JSON with unbalanced braces → logged as truncated_json (pattern-based) ---

    @Test
    void llmReturnsJsonWithUnbalancedBraces_loggedAsTruncated() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String unbalancedJson = """
            {
              "summary": "Correo sospechoso.",
              "heuristicExplanation": "Lenguaje de urgencia.
            }""";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.numPredict()).thenReturn(1500);
        stubLlmSuccess(unbalancedJson);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        assertThat(result.heuristicExplanation()).isNotNull();
        assertStructuredLog(appender, 1L, 10L, AiOrigin.FALLBACK, true);
        var events = appender.list;
        var warnEvent = events.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .findFirst()
                .orElseThrow();
        assertThat(warnEvent.getFormattedMessage()).contains("reason=truncated_json");
    }

    // --- Scenario 15: Complete valid JSON at low length → LLM origin, no warning ---

    @Test
    void llmReturnsCompleteJsonAtLowLength_returnsLlmOrigin() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String completeJson = """
            {
              "summary": "Resumen corto.",
              "heuristicExplanation": "Indicadores.",
              "secondOpinion": "Veredicto."
            }""";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.modelName()).thenReturn("llama3");
        stubLlmSuccess(completeJson);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.LLM);
        assertThat(result.summary()).isEqualTo("Resumen corto.");
        assertStructuredLog(appender, 1L, 10L, AiOrigin.LLM, false);
        long warnCount = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .count();
        assertThat(warnCount).isZero();
    }

    // --- Scenario 16: Invalid JSON log includes length field ---

    @Test
    void parseJsonFailedLog_includesLengthField() throws Exception {
        ListAppender<ILoggingEvent> appender = installLogCaptor();
        EmailAnalysis latest = makeAnalysis();
        String shortInvalidJson = "Esto no es JSON valido";

        when(userRepository.findById(1L)).thenReturn(Optional.of(premiumUser));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
                .thenReturn(Optional.of(latest));
        when(featureToggleService.isEnabled(ConfigKey.AI_ENABLED)).thenReturn(true);
        when(aiProperties.numPredict()).thenReturn(1500);
        stubLlmSuccess(shortInvalidJson);

        AiExplanationResponse result = service.explain(1L, 10L).get();

        assertThat(result.origin()).isEqualTo(AiOrigin.FALLBACK);
        var events = appender.list;
        var warnEvent = events.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .findFirst()
                .orElseThrow();
        assertThat(warnEvent.getFormattedMessage()).contains("length=");
        assertThat(warnEvent.getFormattedMessage()).contains("reason=invalid_json");
    }
}
