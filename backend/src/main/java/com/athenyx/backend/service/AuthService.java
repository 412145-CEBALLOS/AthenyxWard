package com.athenyx.backend.service;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.dto.ActiveSessionResponse;
import com.athenyx.backend.dto.UserInfo;
import com.athenyx.backend.dto.UserUsageResponse;
import com.athenyx.backend.entity.RefreshToken;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Read-side service for the current user's profile. Persists the
 * accessibility-mode toggle in a single transactional update.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final EmailAnalysisRepository emailAnalysisRepository;
    private final AiExplanationRepository aiExplanationRepository;
    private final ReminderRepository reminderRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ConfigService configService;
    private final RefreshTokenService refreshTokenService;
    private final AuditEventPublisher auditEventPublisher;

    public UserInfo getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return new UserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPictureUrl(),
                user.getRole(),
                user.getTrialEndDate(),
                user.isTrialExpired(),
                user.isAccessibilityMode(),
                user.getTermsAcceptedAt(),
                user.getTermsVersion(),
                user.getLastLoginAt(),
                user.getEmailVerified()
        );
    }

    public UserUsageResponse getUserUsage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        UserInfo userInfo = getUserInfo(userId);

        UserUsageResponse.AnalysisUsage analysisUsage;
        if (user.getRole() == com.athenyx.backend.entity.Role.TRIAL) {
            int limit = configService.getInt(ConfigKey.TRIAL_ANALYSIS_LIMIT);
            analysisUsage = new UserUsageResponse.AnalysisUsage(
                    user.getAnalysisCount(), limit, user.getTrialEndDate(), user.isTrialExpired());
        } else {
            analysisUsage = new UserUsageResponse.AnalysisUsage(
                    user.getAnalysisCount(), null, null, false);
        }

        long reminderActive = reminderRepository.countByUserIdAndDoneFalse(userId);
        long reminderDone = reminderRepository.countByUserId(userId) - reminderActive;
        UserUsageResponse.ReminderUsage reminderUsage =
                new UserUsageResponse.ReminderUsage(reminderActive, reminderDone);

        UserUsageResponse.EmailUsage emailUsage = new UserUsageResponse.EmailUsage(
                emailRepository.countByUserId(userId),
                emailRepository.countByUserIdAndIsImportantTrue(userId),
                emailRepository.countByUserIdAndIsHiddenTrue(userId),
                emailRepository.countByUserIdAndIsDeletedTrue(userId)
        );

        int activeSessions = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId).size();
        UserUsageResponse.SessionUsage sessionUsage = new UserUsageResponse.SessionUsage(activeSessions);

        LocalDateTime oldest = findOldestRecordAt(userId);
        long aiExplanations = aiExplanationRepository.countByUserId(userId);
        long auditEvents = auditLogRepository.countByActorId(userId);

        UserUsageResponse.DataInventory dataInventory = new UserUsageResponse.DataInventory(
                emailRepository.countByUserId(userId),
                emailAnalysisRepository.countByUserId(userId),
                aiExplanations,
                reminderRepository.countByUserId(userId),
                auditEvents,
                oldest
        );

        return new UserUsageResponse(userInfo, analysisUsage, reminderUsage, emailUsage, sessionUsage, dataInventory);
    }

    private LocalDateTime findOldestRecordAt(Long userId) {
        LocalDateTime oldest = null;
        Optional<LocalDateTime> oldestEmail = Optional.ofNullable(emailRepository.findOldestByUserId(userId));
        Optional<LocalDateTime> oldestAnalysis = Optional.ofNullable(emailAnalysisRepository.findOldestByUserId(userId));
        Optional<LocalDateTime> oldestAi = Optional.ofNullable(aiExplanationRepository.findOldestByUserId(userId));
        Optional<LocalDateTime> oldestReminder = Optional.ofNullable(reminderRepository.findOldestByUserId(userId));

        for (Optional<LocalDateTime> candidate : java.util.List.of(oldestEmail, oldestAnalysis, oldestAi, oldestReminder)) {
            if (candidate.isPresent()) {
                if (oldest == null || candidate.get().isBefore(oldest)) {
                    oldest = candidate.get();
                }
            }
        }
        return oldest;
    }

    public List<ActiveSessionResponse> listActiveSessions(Long userId, String currentTokenRaw) {
        List<RefreshToken> sessions = refreshTokenService.listActiveSessions(userId);
        String currentFamilyId = null;
        if (currentTokenRaw != null && !currentTokenRaw.isBlank()) {
            try {
                var current = refreshTokenService.findByTokenHash(
                        refreshTokenService.hashToken(currentTokenRaw));
                if (current.isPresent()) {
                    currentFamilyId = current.get().getFamilyId();
                }
            } catch (Exception ignored) {
            }
        }

        final String familyId = currentFamilyId;
        return sessions.stream()
                .map(rt -> new ActiveSessionResponse(
                        rt.getId(),
                        rt.getFamilyId(),
                        rt.getUserAgent(),
                        rt.getIp(),
                        rt.getIssuedAt(),
                        rt.getLastUsedAt(),
                        rt.getFamilyId().equals(familyId)
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(Long userId, Long sessionId, String currentTokenRaw) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<RefreshToken> sessions = refreshTokenService.listActiveSessions(userId);
        RefreshToken target = sessions.stream()
                .filter(s -> s.getId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Sesión no encontrada"));

        String currentFamilyId = null;
        if (currentTokenRaw != null && !currentTokenRaw.isBlank()) {
            try {
                var current = refreshTokenService.findByTokenHash(
                        refreshTokenService.hashToken(currentTokenRaw));
                if (current.isPresent()) {
                    currentFamilyId = current.get().getFamilyId();
                }
            } catch (Exception ignored) {
            }
        }

        final String familyId = currentFamilyId;
        if (target.getFamilyId().equals(familyId)) {
            throw new RuntimeException("No puedes revocar tu sesión actual");
        }

        refreshTokenService.revokeFamily(target.getFamilyId(), userId);
        auditEventPublisher.publishSessionRevoked(
                userId, user.getEmail(), target.getFamilyId(), target.getUserAgent());
    }

    @Transactional
    public UserInfo updateAccessibilityMode(Long userId, boolean accessibilityMode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setAccessibilityMode(accessibilityMode);
        return getUserInfo(userId);
    }

    @Transactional
    public UserInfo acceptTerms(Long userId, String version) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (user.getTermsAcceptedAt() != null) {
            log.info("auth.accept-terms userId={} alreadyAccepted=true keptTimestamp={}",
                    userId, user.getTermsAcceptedAt());
            return getUserInfo(userId);
        }
        user.setTermsAcceptedAt(LocalDateTime.now());
        user.setTermsVersion(version);
        log.info("auth.accept-terms userId={} alreadyAccepted=false version={} timestamp={}",
                userId, version, user.getTermsAcceptedAt());
        return getUserInfo(userId);
    }
}
