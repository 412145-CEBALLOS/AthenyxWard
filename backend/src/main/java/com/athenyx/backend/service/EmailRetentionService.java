package com.athenyx.backend.service;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.repository.AiExplanationRepository;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import com.athenyx.backend.repository.EmailRepository;
import com.athenyx.backend.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailRetentionService {

    private final EmailRepository emailRepository;
    private final ReminderRepository reminderRepository;
    private final EmailAnalysisRepository emailAnalysisRepository;
    private final AiExplanationRepository aiExplanationRepository;
    private final ConfigService configService;
    private final Clock clock;

    public record PurgeResult(int purgedCount, long skippedDueToReminders) {}

    @Transactional
    public PurgeResult purgeNow() {
        int retentionDays = configService.getInt(ConfigKey.EMAIL_RETENTION_DAYS);
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        List<Long> idsToDelete = new ArrayList<>(emailRepository.findIdsOlderThan(cutoff));

        if (idsToDelete.isEmpty()) {
            log.info("email.purge-now deleted=0 cutoff={} retention-days={}", cutoff, retentionDays);
            return new PurgeResult(0, 0);
        }

        List<Long> activeReminderEmailIds = reminderRepository.findActiveEmailIds(idsToDelete);
        Set<Long> protectedIds = new HashSet<>(activeReminderEmailIds);

        List<Long> deletable = idsToDelete.stream()
                .filter(id -> !protectedIds.contains(id))
                .toList();

        int purgedCount = 0;
        if (!deletable.isEmpty()) {
            int reminders = reminderRepository.deleteByEmailIdIn(deletable);
            int aiExpls = aiExplanationRepository.deleteByEmailIdIn(deletable);
            int analyses = emailAnalysisRepository.deleteByEmailIdIn(deletable);
            purgedCount = emailRepository.deleteAllByIdIn(deletable);
            log.info("email.purge-now children reminders={} aiExplanations={} analyses={} emails={}",
                    reminders, aiExpls, analyses, purgedCount);
        }

        long skipped = protectedIds.size();
        log.info("email.purge-now deleted={} skipped={} cutoff={} retention-days={}",
                purgedCount, skipped, cutoff, retentionDays);
        return new PurgeResult(purgedCount, skipped);
    }
}
