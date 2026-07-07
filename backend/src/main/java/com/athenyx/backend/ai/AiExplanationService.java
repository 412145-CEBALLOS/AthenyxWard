package com.athenyx.backend.ai;

import com.athenyx.backend.dto.AiExplanationResponse;
import com.athenyx.backend.entity.AiExplanation;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.heuristics.TrialLimitExceededException;
import com.athenyx.backend.repository.AiExplanationRepository;
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

import java.time.Clock;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio de explicación IA on-demand (US 3.2).
 *
 * <h3>Cinco caminos posibles desde {@link #explain(Long, Long)}:</h3>
 * <ol>
 *   <li><b>Sin análisis previo (riskLevel == null)</b> → FALLBACK con texto
 *       fijo "Analiza primero el correo", {@code origin = FALLBACK},
 *       {@code modelName = null}. No se invoca Ollama.</li>
 *   <li><b>AI deshabilitada ({@code enabled=false})</b> → FALLBACK regenerado
 *       del último {@link EmailAnalysis}: findings + riskLevel +
 *       recommendedActions.</li>
 *   <li><b>Ollama responde en 8 s</b> → {@code origin = LLM},
 *       {@code modelName} de {@link AiProperties}, {@code User.analysisCount++}
 *       si el usuario es TRIAL, se persiste {@link AiExplanation}.</li>
 *   <li><b>Ollama lanza excepción / timeout</b> → FALLBACK heurístico
 *       (mismo regenerador del camino 2). La excepción se captura
 *       internamente y se loggea.</li>
 *   <li><b>TRIAL con quota agotada ({@code analysisCount >= 20})</b> →
 *       {@link TrialLimitExceededException} → mapeada a 403 por el handler
 *       global. Defense-in-depth: el controlador ya bloquea TRIAL vía
 *       {@code @PreAuthorize}.</li>
 * </ol>
 *
 * <h3>Contrato de fallback</h3>
 * El servicio <strong>nunca propaga errores de IA al cliente</strong>.
 * Toda excepción de Ollama (timeout, parse error, 5xx de red) se captura
 * y se convierte en {@code origin = FALLBACK} con un texto regenerado.
 * El cliente siempre recibe HTTP 200 con {@link AiExplanationResponse}.
 *
 * <h3>Persistencia</h3>
 * Toda explicación (LLM o FALLBACK) se persiste como una nueva fila en
 * {@code ai_explanations}, nunca se actualiza una fila existente. La tabla
 * es append-only para preservar el historial.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiExplanationService {

    private static final int TRIAL_LIMIT = 20;

    private final org.springframework.ai.chat.client.ChatClient chatClient;
    private final AiProperties aiProperties;
    private final EmailRepository emailRepository;
    private final EmailAnalysisRepository analysisRepository;
    private final AiExplanationRepository aiExplanationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Async("aiExecutor")
    @Transactional
    public CompletableFuture<AiExplanationResponse> explain(Long userId, Long emailId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new RuntimeException("Correo no encontrado"));

        if (!email.getUser().getId().equals(userId)) {
            throw new RuntimeException("Acceso denegado");
        }

        if (user.getRole() == Role.TRIAL && user.getAnalysisCount() >= TRIAL_LIMIT) {
            throw new TrialLimitExceededException("Límite de análisis alcanzado", 0);
        }

        EmailAnalysis latest = analysisRepository
                .findFirstByEmailIdOrderByAnalyzedAtDesc(emailId)
                .orElse(null);

        if (latest == null) {
            return CompletableFuture.completedFuture(
                    persistAndReturn(user, email, "Analiza primero el correo",
                            AiOrigin.FALLBACK, null));
        }

        if (!aiProperties.enabled()) {
            return CompletableFuture.completedFuture(persistAndReturn(
                    user, email, buildHeuristicFallback(latest), AiOrigin.FALLBACK, null));
        }

        long start = System.currentTimeMillis();
        try {
            String prompt = buildPrompt(latest, email);
            String text = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (text == null || text.isBlank()) {
                throw new AiUnavailableException("Empty LLM response");
            }

            long durationMs = System.currentTimeMillis() - start;
            log.info("ai.explain userId={} emailId={} durationMs={} origin={}",
                    userId, emailId, durationMs, AiOrigin.LLM);

            if (user.getRole() == Role.TRIAL) {
                user.setAnalysisCount(user.getAnalysisCount() + 1);
                userRepository.save(user);
            }

            return CompletableFuture.completedFuture(persistAndReturn(
                    user, email, text, AiOrigin.LLM, aiProperties.modelName()));

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            log.info("ai.explain userId={} emailId={} durationMs={} origin={} error={}",
                    userId, emailId, durationMs, AiOrigin.FALLBACK, e.getMessage());

            return CompletableFuture.completedFuture(persistAndReturn(
                    user, email, buildHeuristicFallback(latest), AiOrigin.FALLBACK, null));
        }
    }

    private AiExplanationResponse persistAndReturn(
            User user, Email email, String text, AiOrigin origin, String modelName) {
        AiExplanation row = AiExplanation.builder()
                .user(user)
                .email(email)
                .text(text)
                .origin(origin)
                .modelName(modelName)
                .build();
        aiExplanationRepository.save(row);
        return new AiExplanationResponse(
                row.getId(),
                row.getText(),
                row.getOrigin(),
                row.getModelName(),
                row.getGeneratedAt());
    }

    private String buildHeuristicFallback(EmailAnalysis a) {
        int findingsCount = countFindings(a.getFindings());
        String recommendedAction = buildRecommendedActionSnippet(a);
        return String.format(
                "El análisis heurístico detectó %d indicador(es) de riesgo. " +
                "Nivel de riesgo: %s (%d%%). %s",
                findingsCount,
                a.getRiskLevel() != null ? a.getRiskLevel().name() : "DESCONOCIDO",
                a.getRiskPercentage() != null ? a.getRiskPercentage() : 0,
                recommendedAction);
    }

    private int countFindings(String findingsJson) {
        if (findingsJson == null || findingsJson.isBlank()) {
            return 0;
        }
        try {
            var list = objectMapper.readValue(findingsJson, java.util.List.class);
            return list.size();
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    private String buildRecommendedActionSnippet(EmailAnalysis a) {
        if (a.getRecommendedActions() == null || a.getRecommendedActions().isBlank()) {
            return "Revisa los indicadores antes de tomar acciones.";
        }
        try {
            var actions = objectMapper.readValue(a.getRecommendedActions(), java.util.List.class);
            if (actions.isEmpty()) {
                return "No se requieren acciones especiales.";
            }
            var first = (java.util.Map<String, Object>) actions.get(0);
            String label = (String) first.get("label");
            return label != null ? label + "." : "Revisa los indicadores antes de tomar acciones.";
        } catch (JsonProcessingException e) {
            return "Revisa los indicadores antes de tomar acciones.";
        }
    }

    private String buildPrompt(EmailAnalysis a, Email email) {
        return String.format("""
Eres un asistente de ciberseguridad para Athenyx Ward, una plataforma de protección de correo electrónico.
Tu tarea es explicar de forma clara, concisa y en español al usuario, en máximo 3 párrafos,
por qué este correo tiene un nivel de riesgo %s (%d%%).

=== INDICADORES DETECTADOS ===
%s

=== REMITENTE ===
%s <%s>

=== ASUNTO ===
%s

=== CUERPO DEL CORREO (primeros 1500 caracteres) ===
%s

Responde SOLO con la explicación. No inventes datos. Sé directo.""",
                a.getRiskLevel() != null ? a.getRiskLevel().name() : "DESCONOCIDO",
                a.getRiskPercentage() != null ? a.getRiskPercentage() : 0,
                a.getFindings() != null ? a.getFindings() : "(sin hallazgos)",
                email.getSenderName() != null ? email.getSenderName() : "",
                email.getSender(),
                email.getSubject(),
                email.getContentForAnalysis() != null
                        ? email.getContentForAnalysis().substring(
                                0, Math.min(1500, email.getContentForAnalysis().length()))
                        : "");
    }
}
