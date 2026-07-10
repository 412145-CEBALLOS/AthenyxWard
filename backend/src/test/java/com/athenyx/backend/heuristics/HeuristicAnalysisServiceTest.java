package com.athenyx.backend.heuristics;

import com.athenyx.backend.dto.HeuristicAnalysisResponse;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.metadata.AuthStatus;
import com.athenyx.backend.metadata.EmailHeaderCache;
import com.athenyx.backend.metadata.ExtractedHeaders;
import com.athenyx.backend.metadata.MassMailingProvider;
import com.athenyx.backend.metadata.MetadataAnalysisResult;
import com.athenyx.backend.metadata.SenderTrustLevel;
import com.athenyx.backend.metadata.SenderValidationResult;
import com.athenyx.backend.metadata.TimestampAnalysisResult;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Integration-flavoured unit tests for {@link HeuristicAnalysisService}.
 *
 * <p>Drives the cache / trial-limit / counter / persistence branches
 * against a mocked engine and repositories. These tests cover the
 * behaviour introduced in US 2.8 (real-time analysis panel) and
 * re-validate the cache contract from US 2.1.</p>
 */
@ExtendWith(MockitoExtension.class)
class HeuristicAnalysisServiceTest {

    @Mock
    private EmailRepository emailRepository;
    @Mock
    private EmailAnalysisRepository analysisRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HeuristicEngine engine;
    @Mock
    private EmailHeaderCache emailHeaderCache;

    private HeuristicAnalysisService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(LocalDateTime.of(2025, 1, 1, 12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private User user;
    private Email email;
    private MetadataAnalysisResult metadata;

    @BeforeEach
    void setUp() {
        service = new HeuristicAnalysisService(
            emailRepository, analysisRepository, userRepository,
            engine, emailHeaderCache, objectMapper, clock
        );

        user = User.builder()
            .id(1L).googleId("gid").email("u@example.com").name("U")
            .role(Role.PREMIUM).analysisCount(0)
            .build();
        email = Email.builder()
            .id(10L).gmailId("msg-1").sender("a@b.com").senderName("A")
            .subject("S").snippet("snip").contentForAnalysis("body")
            .receivedAt(LocalDateTime.now()).originalDateHeader("now")
            .isRead(false).isImportant(false).user(user)
            .build();
        ExtractedHeaders headers = new ExtractedHeaders(
            "a@b.com", "b.com", "A", null, null, null, null,
            java.util.List.of(), "now", null,
            AuthStatus.NONE, AuthStatus.NONE, AuthStatus.NONE,
            null, null, null, false, MassMailingProvider.NONE
        );
        metadata = new MetadataAnalysisResult(
            headers,
            new SenderValidationResult(false, false, false),
            new TimestampAnalysisResult(false, false, false, 0L),
            50,
            SenderTrustLevel.UNKNOWN,
            java.util.List.of(), java.util.List.of()
        );
    }

    @Test
    void reAnalysis_createsNewRecordWhenCacheStale() throws Exception {
        EmailAnalysis stale = EmailAnalysis.builder()
            .id(100L)
            .email(email).user(user)
            .origin(AnalysisOrigin.HEURISTIC)
            .riskLevel(ThreatLevel.GREEN)
            .riskPercentage(15)
            .analyzedAt(LocalDateTime.now(clock).minusHours(25))
            .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(emailHeaderCache.getOrAnalyze(email)).thenReturn(metadata);
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
            .thenReturn(Optional.of(stale));
        when(engine.run(any())).thenReturn(new HeuristicResult(
            List.of(), 88, ThreatLevel.RED
        ));
        when(analysisRepository.save(any(EmailAnalysis.class)))
            .thenAnswer(inv -> {
                EmailAnalysis a = inv.getArgument(0);
                a.setId(200L);
                a.setAnalyzedAt(LocalDateTime.now());
                return a;
            });

        HeuristicAnalysisResponse result = service.analyze(1L, 10L).get();

        ArgumentCaptor<EmailAnalysis> captor = ArgumentCaptor.forClass(EmailAnalysis.class);
        verify(analysisRepository, times(1)).save(captor.capture());
        EmailAnalysis saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(200L);
        assertThat(saved.getId()).isNotEqualTo(stale.getId());
        assertThat(saved.getAnalyzedAt()).isAfter(stale.getAnalyzedAt());
        assertThat(result.riskPercentage()).isEqualTo(88);
        assertThat(result.riskLevel()).isEqualTo(ThreatLevel.RED);
        assertThat(result.analysisId()).isEqualTo(200L);
    }

    @Test
    void cacheHit_returnsExistingAnalysisWithoutRunningEngine() throws Exception {
        EmailAnalysis cached = EmailAnalysis.builder()
            .id(99L)
            .email(email).user(user)
            .origin(AnalysisOrigin.HEURISTIC)
            .riskLevel(ThreatLevel.GREEN)
            .riskPercentage(15)
            .analyzedAt(LocalDateTime.now().minusMinutes(30))
            .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
            .thenReturn(Optional.of(cached));

        CompletableFuture<HeuristicAnalysisResponse> future = service.analyze(1L, 10L);
        HeuristicAnalysisResponse result = future.get();

        assertThat(result.riskPercentage()).isEqualTo(15);
        assertThat(result.riskLevel()).isEqualTo(ThreatLevel.GREEN);
        assertThat(result.analysisId()).isEqualTo(99L);
        verify(engine, never()).run(any());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void cacheExpired_runsEngineAndSaves() throws Exception {
        EmailAnalysis stale = EmailAnalysis.builder()
            .id(99L)
            .email(email).user(user)
            .origin(AnalysisOrigin.HEURISTIC)
            .riskLevel(ThreatLevel.GREEN)
            .riskPercentage(15)
            .analyzedAt(LocalDateTime.now(clock).minusHours(25))
            .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
            .thenReturn(Optional.of(stale));
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(emailHeaderCache.getOrAnalyze(email)).thenReturn(metadata);
        when(engine.run(any())).thenReturn(new HeuristicResult(
            List.of(), 22, ThreatLevel.GREEN
        ));
        when(analysisRepository.save(any(EmailAnalysis.class)))
            .thenAnswer(inv -> {
                EmailAnalysis a = inv.getArgument(0);
                a.setId(123L);
                return a;
            });

        CompletableFuture<HeuristicAnalysisResponse> future = service.analyze(1L, 10L);
        HeuristicAnalysisResponse result = future.get();

        assertThat(result.riskPercentage()).isEqualTo(22);
        verify(engine, times(1)).run(any());
        verify(analysisRepository, times(1)).save(any(EmailAnalysis.class));
    }

    @Test
    void noCache_runsEngineAndSaves() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
            .thenReturn(Optional.empty());
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(emailHeaderCache.getOrAnalyze(email)).thenReturn(metadata);
        when(engine.run(any())).thenReturn(new HeuristicResult(
            List.of(), 80, ThreatLevel.RED
        ));
        when(analysisRepository.save(any(EmailAnalysis.class)))
            .thenAnswer(inv -> {
                EmailAnalysis a = inv.getArgument(0);
                a.setId(456L);
                return a;
            });

        HeuristicAnalysisResponse result = service.analyze(1L, 10L).get();

        assertThat(result.riskPercentage()).isEqualTo(80);
        assertThat(result.riskLevel()).isEqualTo(ThreatLevel.RED);
        verify(engine, times(1)).run(any());
        verify(analysisRepository, times(1)).save(any());
    }

    @Test
    void trialUserUnderLimit_incrementsCounter() throws Exception {
        user.setRole(Role.TRIAL);
        user.setAnalysisCount(5);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
            .thenReturn(Optional.empty());
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(emailHeaderCache.getOrAnalyze(email)).thenReturn(metadata);
        when(engine.run(any())).thenReturn(new HeuristicResult(
            List.of(), 10, ThreatLevel.GREEN
        ));
        when(analysisRepository.save(any(EmailAnalysis.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.analyze(1L, 10L).get();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getAnalysisCount()).isEqualTo(6);
    }

    @Test
    void trialUserAtLimit_throwsTrialLimitExceeded() {
        user.setRole(Role.TRIAL);
        user.setAnalysisCount(20);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.analyze(1L, 10L))
            .isInstanceOf(TrialLimitExceededException.class);
        verify(engine, never()).run(any());
        verify(analysisRepository, never()).save(any());
    }

    @Test
    void nonTrialUser_doesNotIncrementCounter() throws Exception {
        user.setRole(Role.PREMIUM);
        user.setAnalysisCount(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
            .thenReturn(Optional.empty());
        when(emailRepository.findById(10L)).thenReturn(Optional.of(email));
        when(emailHeaderCache.getOrAnalyze(email)).thenReturn(metadata);
        when(engine.run(any())).thenReturn(new HeuristicResult(
            List.of(), 5, ThreatLevel.GREEN
        ));
        when(analysisRepository.save(any(EmailAnalysis.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.analyze(1L, 10L).get();

        verify(userRepository, never()).save(any());
    }

    @Test
    void getLatest_returnsEmptyWhenNoAnalysis() {
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
            .thenReturn(Optional.empty());

        Optional<HeuristicAnalysisResponse> result = service.getLatest(1L, 10L);

        assertThat(result).isEmpty();
    }

    @Test
    void getLatest_filtersByOwner() {
        User other = User.builder().id(99L).build();
        EmailAnalysis ea = EmailAnalysis.builder()
            .id(50L).email(email).user(other)
            .origin(AnalysisOrigin.HEURISTIC)
            .riskLevel(ThreatLevel.GREEN).riskPercentage(0)
            .build();
        when(analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(10L))
            .thenReturn(Optional.of(ea));

        Optional<HeuristicAnalysisResponse> result = service.getLatest(1L, 10L);

        assertThat(result).isEmpty();
    }

    @Test
    void getTrialRemaining_returnsLimitMinusCount() {
        user.setRole(Role.TRIAL);
        user.setAnalysisCount(7);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        int remaining = service.getTrialRemaining(1L);

        assertThat(remaining).isEqualTo(13);
    }

    @Test
    void getTrialRemaining_returnsMaxForNonTrial() {
        user.setRole(Role.PREMIUM);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        int remaining = service.getTrialRemaining(1L);

        assertThat(remaining).isEqualTo(Integer.MAX_VALUE);
    }
}
