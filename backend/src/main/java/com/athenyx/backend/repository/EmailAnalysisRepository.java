package com.athenyx.backend.repository;

import com.athenyx.backend.entity.EmailAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmailAnalysisRepository extends JpaRepository<EmailAnalysis, Long> {

    Optional<EmailAnalysis> findFirstByEmailIdOrderByAnalyzedAtDesc(Long emailId);

    Optional<EmailAnalysis> findFirstByEmailIdAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
        Long emailId, LocalDateTime cutoff);

    /**
     * Returns the latest analysis (highest id = most recent {@code analyzedAt})
     * for each email id in the given collection. Used to enrich email-list
     * endpoints with risk data without triggering N+1 queries.
     */
    @Query("""
        SELECT ea FROM EmailAnalysis ea
        WHERE ea.id IN (
            SELECT MAX(ea2.id) FROM EmailAnalysis ea2
            WHERE ea2.email.id IN :emailIds
            GROUP BY ea2.email.id
        )
        """)
    List<EmailAnalysis> findLatestByEmailIds(@Param("emailIds") Collection<Long> emailIds);

    @Query("""
        SELECT ea FROM EmailAnalysis ea
        WHERE ea.user.id = :userId
        AND ea.analyzedAt BETWEEN :from AND :to
        ORDER BY ea.analyzedAt DESC
        """)
    Page<EmailAnalysis> findByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    Page<EmailAnalysis> findByUserIdOrderByAnalyzedAtDesc(Long userId, Pageable pageable);

    @Query(value = """
        SELECT ea FROM EmailAnalysis ea
        JOIN FETCH ea.email
        WHERE ea.user.id = :userId
        AND (CAST(:from AS timestamp) IS NULL OR ea.analyzedAt >= :from)
        AND (CAST(:to   AS timestamp) IS NULL OR ea.analyzedAt <= :to)
        ORDER BY ea.analyzedAt DESC
        """,
        countQuery = """
        SELECT COUNT(ea) FROM EmailAnalysis ea
        WHERE ea.user.id = :userId
        AND (CAST(:from AS timestamp) IS NULL OR ea.analyzedAt >= :from)
        AND (CAST(:to   AS timestamp) IS NULL OR ea.analyzedAt <= :to)
        """)
    Page<EmailAnalysis> findHistoryByUser(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    @Modifying
    @Query("DELETE FROM EmailAnalysis ea WHERE ea.email.id IN :ids")
    int deleteByEmailIdIn(@Param("ids") Collection<Long> ids);

    long countByUserId(Long userId);

    @Query("SELECT MIN(ea.analyzedAt) FROM EmailAnalysis ea WHERE ea.user.id = :userId")
    LocalDateTime findOldestByUserId(@Param("userId") Long userId);
}
