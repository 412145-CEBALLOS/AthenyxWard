package com.athenyx.backend.repository;

import com.athenyx.backend.entity.AuditActionType;
import com.athenyx.backend.entity.AuditLog;
import com.athenyx.backend.entity.AuditSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Modifying
    void deleteByCreatedAtBefore(LocalDateTime cutoff);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (CAST(:from AS timestamp) IS NULL OR a.createdAt >= :from)
        AND (CAST(:to AS timestamp) IS NULL OR a.createdAt <= :to)
        AND (:actor IS NULL OR CAST(:actor AS string) IS NULL OR a.actorEmail LIKE CONCAT('%', :actor, '%'))
        AND (:action IS NULL OR a.actionType = :action)
        AND (:severity IS NULL OR a.severity = :severity)
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findEntries(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("actor") String actor,
        @Param("action") AuditActionType action,
        @Param("severity") AuditSeverity severity,
        Pageable pageable);

    @Query(value = """
        SELECT a.* FROM audit_log a
        WHERE (:from IS NULL OR a.created_at >= :from)
        AND (:to IS NULL OR a.created_at <= :to)
        AND (:actor IS NULL OR a.actor_email LIKE CONCAT('%', :actor, '%'))
        AND (:action IS NULL OR a.action_type = :action)
        AND (:severity IS NULL OR a.severity = :severity)
        ORDER BY (
            (CASE WHEN a.actor_email LIKE CONCAT('%', :query, '%') THEN 1 ELSE 0 END) +
            (CASE WHEN a.target_id LIKE CONCAT('%', :query, '%') THEN 1 ELSE 0 END) +
            (CASE WHEN a.payload LIKE CONCAT('%', :query, '%') THEN 1 ELSE 0 END)
        ) DESC,
        a.created_at DESC
        LIMIT :top
        """, nativeQuery = true)
    List<AuditLog> searchByRelevance(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("actor") String actor,
        @Param("action") String action,
        @Param("severity") String severity,
        @Param("query") String query,
        @Param("top") int top);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (CAST(:from AS timestamp) IS NULL OR a.createdAt >= :from)
        AND (CAST(:to AS timestamp) IS NULL OR a.createdAt <= :to)
        AND (:actor IS NULL OR a.actorEmail LIKE CONCAT('%', :actor, '%'))
        AND (:action IS NULL OR a.actionType = :action)
        AND (:severity IS NULL OR a.severity = :severity)
        ORDER BY a.createdAt DESC
        """)
    Stream<AuditLog> streamEntries(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("actor") String actor,
        @Param("action") AuditActionType action,
        @Param("severity") AuditSeverity severity);

    long countByCreatedAtBefore(LocalDateTime cutoff);
}
