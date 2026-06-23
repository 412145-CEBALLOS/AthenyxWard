package com.athenyx.backend.heuristics;

import com.athenyx.backend.dto.HeuristicAnalysisResponse;
import com.athenyx.backend.dto.HeuristicAnalysisResponse.*;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.metadata.EmailHeaderCache;
import com.athenyx.backend.metadata.MetadataAnalysisResult;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HeuristicAnalysisService {

    private static final int TRIAL_LIMIT = 20;

    private final EmailRepository emailRepository;
    private final EmailAnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final HeuristicEngine engine;
    private final EmailHeaderCache emailHeaderCache;
    private final ObjectMapper objectMapper;

    @Async("heuristicsExecutor")
    @Transactional
    public CompletableFuture<HeuristicAnalysisResponse> analyze(Long userId, Long emailId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getRole() == Role.TRIAL && user.getAnalysisCount() >= TRIAL_LIMIT) {
            throw new TrialLimitExceededException("Límite de análisis alcanzado", 0);
        }

        Optional<EmailAnalysis> cached = analysisRepository
            .findFirstByEmailIdOrderByAnalyzedAtDesc(emailId);
        if (cached.isPresent() && cached.get().getAnalyzedAt()
                .isAfter(LocalDateTime.now().minusHours(24))) {
            return CompletableFuture.completedFuture(toResponse(cached.get()));
        }

        Email email = emailRepository.findById(emailId)
            .orElseThrow(() -> new RuntimeException("Correo no encontrado"));

        MetadataAnalysisResult metadata = emailHeaderCache.getOrAnalyze(email);
        EmailHeuristicsInput input = toInput(email);
        HeuristicResult result = engine.run(input);

        List<RecommendedActionDto> actions = buildRecommendedActions(result);
        String aiExplanation = buildAiExplanation(result);
        String contentSummary = email.getSnippet() != null ? email.getSnippet() : "";

        SenderTrustDto senderTrust = new SenderTrustDto(
            email.getSender(),
            email.getSenderName(),
            extractDomain(email.getSender()),
            false,
            email.getSpfStatus(),
            email.getDkimStatus(),
            email.getDmarcStatus(),
            email.getReturnPath(),
            email.getReplyTo(),
            metadata.headers().massMailingProvider().name(),
            metadata.timestampAnalysis().timezoneAnomaly(),
            metadata.trustLevel().name(),
            metadata.trustScore()
        );

        EmailAnalysis analysis = EmailAnalysis.builder()
            .email(email)
            .user(user)
            .origin(AnalysisOrigin.HEURISTIC)
            .threatLevel(result.threatLevel())
            .riskPercentage(result.riskPercentage())
            .findings(toJson(result.findings()))
            .suspiciousUrls(toJson(Collections.emptyList()))
            .senderTrust(toJson(senderTrust))
            .recommendedActions(toJson(actions))
            .aiExplanation(aiExplanation)
            .contentSummary(contentSummary)
            .build();

        EmailAnalysis saved = analysisRepository.save(analysis);

        if (user.getRole() == Role.TRIAL) {
            user.setAnalysisCount(user.getAnalysisCount() + 1);
            userRepository.save(user);
        }

        return CompletableFuture.completedFuture(toResponse(saved));
    }

    @Transactional(readOnly = true)
    public Optional<HeuristicAnalysisResponse> getLatest(Long userId, Long emailId) {
        return analysisRepository.findFirstByEmailIdOrderByAnalyzedAtDesc(emailId)
            .filter(a -> a.getUser().getId().equals(userId))
            .map(this::toResponse);
    }

    public int getTrialRemaining(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (user.getRole() != Role.TRIAL) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, TRIAL_LIMIT - user.getAnalysisCount());
    }

    private EmailHeuristicsInput toInput(Email email) {
        List<String> urls = new ArrayList<>();
        if (email.getExtractedUrls() != null && !email.getExtractedUrls().isBlank()) {
            urls = Arrays.asList(email.getExtractedUrls().split(","));
        }
        return new EmailHeuristicsInput(
            email.getSubject(),
            email.getSender(),
            email.getSenderName(),
            email.getContentForAnalysis(),
            email.getHtmlContent(),
            urls,
            email.getReceivedAt(),
            email.getOriginalDateHeader(),
            email.getReplyTo(),
            email.getReturnPath(),
            email.getSpfStatus(),
            email.getDkimStatus(),
            email.getDmarcStatus(),
            email.getOriginalTimezone(),
            email.getListUnsubscribe(),
            email.getXMailer()
        );
    }

    private HeuristicAnalysisResponse toResponse(EmailAnalysis a) {
        List<HeuristicFindingDto> findings = fromJsonArray(a.getFindings(), HeuristicFindingDto.class);
        List<SuspiciousUrlDto> suspiciousUrls = fromJsonArray(a.getSuspiciousUrls(), SuspiciousUrlDto.class);
        SenderTrustDto senderTrust = fromJson(a.getSenderTrust(), SenderTrustDto.class);
        List<RecommendedActionDto> recommendedActions = fromJsonArray(a.getRecommendedActions(), RecommendedActionDto.class);
        return new HeuristicAnalysisResponse(
            a.getId(),
            a.getEmail().getId(),
            a.getRiskPercentage(),
            a.getThreatLevel(),
            categorizeThreats(findings),
            findings,
            suspiciousUrls,
            senderTrust,
            a.getAiExplanation(),
            a.getContentSummary(),
            recommendedActions,
            a.getAnalyzedAt(),
            a.getOrigin(),
            a.getModelName()
        );
    }

    private List<ThreatCategoryDto> categorizeThreats(List<HeuristicFindingDto> findings) {
        List<ThreatCategoryDto> categories = new ArrayList<>();
        for (HeuristicFindingDto f : findings) {
            String rule = f.rule().toLowerCase();
            if (rule.contains("scam") || rule.contains("language")) {
                categories.add(new ThreatCategoryDto("FRAUD", "Fraude"));
            }
            if (rule.contains("domain") || rule.contains("impersonation") || rule.contains("spoof")) {
                categories.add(new ThreatCategoryDto("SPOOFING", "Suplantación"));
            }
            if (rule.contains("malicious") || rule.contains("attachment") || rule.contains("htmlform")) {
                categories.add(new ThreatCategoryDto("MALWARE", "Malware"));
            }
            if (rule.contains("urgent") || rule.contains("fake") || rule.contains("login")) {
                categories.add(new ThreatCategoryDto("PHISHING", "Phishing"));
            }
            if (rule.contains("social") || rule.contains("scam")) {
                categories.add(new ThreatCategoryDto("SOCIAL_ENGINEERING", "Ingeniería social"));
            }
        }
        return categories.stream().distinct().toList();
    }

    private List<RecommendedActionDto> buildRecommendedActions(HeuristicResult result) {
        List<RecommendedActionDto> actions = new ArrayList<>();
        if (result.threatLevel() == ThreatLevel.RED || result.threatLevel() == ThreatLevel.YELLOW) {
            actions.add(new RecommendedActionDto("No hacer clic en los enlaces del correo", true, false));
        }
        if (result.threatLevel() == ThreatLevel.RED) {
            actions.add(new RecommendedActionDto("Contactar al remitente por canales oficiales", false, false));
            actions.add(new RecommendedActionDto("Marcar como phishing y eliminar", true, false));
        }
        if (actions.isEmpty()) {
            actions.add(new RecommendedActionDto("No se requieren acciones especiales", false, false));
        }
        return actions;
    }

    private String buildAiExplanation(HeuristicResult result) {
        if (result.findings().isEmpty()) {
            return "El correo no presenta indicadores de riesgo relevantes según el análisis heurístico.";
        }
        int count = result.findings().size();
        return "El análisis heurístico ha detectado " + count + " indicador(es) de riesgo. " +
               "Nivel de riesgo: " + result.threatLevel() + " (" + result.riskPercentage() + "%).";
    }

    private String extractDomain(String sender) {
        if (sender == null) return "";
        int at = sender.indexOf('@');
        if (at < 0) return "";
        return sender.substring(at + 1).replace(">", "").trim();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed", e);
            return "[]";
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON deserialization failed", e);
            return null;
        }
    }

    private <T> List<T> fromJsonArray(String json, Class<T> elementClass) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
        } catch (JsonProcessingException e) {
            log.error("JSON array deserialization failed", e);
            return Collections.emptyList();
        }
    }
}
