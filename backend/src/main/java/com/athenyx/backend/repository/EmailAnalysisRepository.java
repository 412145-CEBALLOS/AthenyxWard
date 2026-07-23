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

    long countByUserIdAndAnalyzedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

    long countByAnalyzedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
        SELECT COUNT(ea) FROM EmailAnalysis ea
        WHERE ea.user.id = :userId
        AND ea.riskLevel = 'RED'
        AND ea.analyzedAt BETWEEN :from AND :to
        """)
    long countThreatsByUserIdAndAnalyzedAtBetween(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(ea) FROM EmailAnalysis ea
        WHERE ea.riskLevel = 'RED'
        AND ea.analyzedAt BETWEEN :from AND :to
        """)
    long countThreatsByAnalyzedAtBetween(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("""
        SELECT AVG(ea.riskPercentage) FROM EmailAnalysis ea
        WHERE ea.user.id = :userId
        AND ea.analyzedAt BETWEEN :from AND :to
        """)
    Double avgRiskPercentageByUserIdAndAnalyzedAtBetween(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("""
        SELECT AVG(ea.riskPercentage) FROM EmailAnalysis ea
        WHERE ea.analyzedAt BETWEEN :from AND :to
        """)
    Double avgRiskPercentageByAnalyzedAtBetween(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("""
        SELECT ea.riskLevel AS level, COUNT(ea) AS count FROM EmailAnalysis ea
        WHERE ea.user.id = :userId
        AND ea.analyzedAt BETWEEN :from AND :to
        GROUP BY ea.riskLevel
        """)
    List<RiskLevelCount> countRiskLevelsByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("""
        SELECT ea.riskLevel AS level, COUNT(ea) AS count FROM EmailAnalysis ea
        WHERE ea.analyzedAt BETWEEN :from AND :to
        GROUP BY ea.riskLevel
        """)
    List<RiskLevelCount> countRiskLevelsByDateRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    interface RiskLevelCount {
        com.athenyx.backend.heuristics.ThreatLevel getLevel();
        long getCount();
    }

    @Query("""
        SELECT ea.origin AS source, COUNT(ea) AS count FROM EmailAnalysis ea
        WHERE ea.analyzedAt BETWEEN :from AND :to
        GROUP BY ea.origin
        """)
    List<OriginCount> countOriginsByDateRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    interface OriginCount {
        com.athenyx.backend.heuristics.AnalysisOrigin getSource();
        long getCount();
    }

    @Query(value = """
        SELECT DATE(ea.analyzed_at), COUNT(*)
        FROM email_analysis ea
        WHERE ea.risk_level = 'RED'
        AND ea.analyzed_at BETWEEN :from AND :to
        GROUP BY DATE(ea.analyzed_at)
        """, nativeQuery = true)
    List<Object[]> countDailyThreats(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query(value = """
        SELECT HOUR(ea.analyzed_at), COUNT(*)
        FROM email_analysis ea
        WHERE ea.risk_level = 'RED'
        AND ea.analyzed_at BETWEEN :from AND :to
        GROUP BY HOUR(ea.analyzed_at)
        """, nativeQuery = true)
    List<Object[]> countThreatsByHour(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("""
        SELECT ea.findings FROM EmailAnalysis ea
        WHERE ea.user.id = :userId
        AND ea.analyzedAt BETWEEN :from AND :to
        ORDER BY ea.analyzedAt DESC
        """)
    List<String> findFindingsByUserIdAndDateRange(
        @Param("userId") Long userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    @Query("""
        SELECT ea.findings FROM EmailAnalysis ea
        WHERE ea.analyzedAt BETWEEN :from AND :to
        ORDER BY ea.analyzedAt DESC
        """)
    List<String> findFindingsByDateRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    @Query("""
        SELECT MAX(ea.analyzedAt) FROM EmailAnalysis ea
        WHERE ea.user.id = :userId
        AND ea.riskLevel = 'RED'
        """)
    java.util.Optional<LocalDateTime> findLastThreatAtByUserId(@Param("userId") Long userId);
}
