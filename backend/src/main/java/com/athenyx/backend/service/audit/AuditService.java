package com.athenyx.backend.service.audit;

import com.athenyx.backend.dto.AuditEntryResponse;
import com.athenyx.backend.dto.AuditPageResponse;
import com.athenyx.backend.entity.AuditActionType;
import com.athenyx.backend.entity.AuditLog;
import com.athenyx.backend.entity.AuditSeverity;
import com.athenyx.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;

    public AuditPageResponse findEntries(
            LocalDateTime from,
            LocalDateTime to,
            String actor,
            AuditActionType action,
            AuditSeverity severity,
            int page,
            int size) {

        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLog> result = repository.findEntries(from, to, actor, action, severity, pageable);

        return new AuditPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    public List<AuditLog> searchByRelevance(
            LocalDateTime from,
            LocalDateTime to,
            String actor,
            AuditActionType action,
            AuditSeverity severity,
            String query,
            int top) {

        return repository.searchByRelevance(
                from, to, actor,
                action != null ? action.name() : null,
                severity != null ? severity.name() : null,
                query,
                top);
    }

    public List<AuditLog> streamEntries(
            LocalDateTime from,
            LocalDateTime to,
            String actor,
            AuditActionType action,
            AuditSeverity severity) {
        return repository.streamEntries(from, to, actor, action, severity).toList();
    }

    private AuditEntryResponse toResponse(AuditLog a) {
        return new AuditEntryResponse(
                a.getId(),
                a.getCreatedAt(),
                a.getActorId(),
                a.getActorEmail(),
                a.getActorRole(),
                a.getActionType(),
                a.getTargetType(),
                a.getTargetId(),
                a.getSeverity(),
                a.getResult(),
                a.getPayload(),
                a.getIpAddress(),
                a.getUserAgent(),
                a.getCorrelationId()
        );
    }
}
