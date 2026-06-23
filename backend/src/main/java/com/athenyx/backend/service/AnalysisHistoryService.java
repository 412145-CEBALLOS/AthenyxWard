package com.athenyx.backend.service;

import com.athenyx.backend.dto.AnalysisHistoryResponse;
import com.athenyx.backend.dto.AnalysisHistoryResponse.AnalysisHistoryItem;
import com.athenyx.backend.entity.Email;
import com.athenyx.backend.entity.EmailAnalysis;
import com.athenyx.backend.repository.EmailAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisHistoryService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    static final int SUMMARY_MAX_LENGTH = 200;

    private final EmailAnalysisRepository repository;

    public AnalysisHistoryResponse getHistory(
            Long userId, LocalDateTime from, LocalDateTime to, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(
            safePage, safeSize, Sort.by(Sort.Direction.DESC, "analyzedAt"));

        Page<EmailAnalysis> result = repository.findHistoryByUser(userId, from, to, pageable);

        return new AnalysisHistoryResponse(
            result.getContent().stream().map(this::toItem).toList(),
            result.getNumber(),
            result.getTotalPages(),
            result.getTotalElements()
        );
    }

    private AnalysisHistoryItem toItem(EmailAnalysis a) {
        Email e = a.getEmail();
        return new AnalysisHistoryItem(
            a.getId(),
            e.getId(),
            e.getSender(),
            e.getSubject(),
            a.getRiskPercentage(),
            a.getRiskLevel().name(),
            a.getAnalyzedAt(),
            truncate(a.getAiExplanation(), SUMMARY_MAX_LENGTH)
        );
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        if (value.length() <= max) return value;
        return value.substring(0, max - 1).trim() + "…";
    }
}
