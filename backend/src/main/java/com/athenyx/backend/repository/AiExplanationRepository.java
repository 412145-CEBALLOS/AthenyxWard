package com.athenyx.backend.repository;

import com.athenyx.backend.entity.AiExplanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface AiExplanationRepository extends JpaRepository<AiExplanation, Long> {

    Optional<AiExplanation> findFirstByEmailIdAndUserIdOrderByGeneratedAtDesc(Long emailId, Long userId);

    @Modifying
    @Query("DELETE FROM AiExplanation a WHERE a.email.id IN :ids")
    int deleteByEmailIdIn(@Param("ids") Collection<Long> ids);

    long countByUserId(Long userId);

    @Query("SELECT MIN(a.generatedAt) FROM AiExplanation a WHERE a.user.id = :userId")
    LocalDateTime findOldestByUserId(@Param("userId") Long userId);
}
