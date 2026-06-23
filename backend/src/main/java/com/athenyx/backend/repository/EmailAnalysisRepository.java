package com.athenyx.backend.repository;

import com.athenyx.backend.entity.EmailAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailAnalysisRepository extends JpaRepository<EmailAnalysis, Long> {

    Optional<EmailAnalysis> findFirstByEmailIdOrderByAnalyzedAtDesc(Long emailId);

    Optional<EmailAnalysis> findFirstByEmailIdAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
        Long emailId, LocalDateTime cutoff);

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
}
