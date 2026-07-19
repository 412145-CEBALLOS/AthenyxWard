package com.athenyx.backend.repository;

import com.athenyx.backend.entity.AiExplanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface AiExplanationRepository extends JpaRepository<AiExplanation, Long> {

    Optional<AiExplanation> findFirstByEmailIdAndUserIdOrderByGeneratedAtDesc(Long emailId, Long userId);

    @Modifying
    @Query("DELETE FROM AiExplanation a WHERE a.email.id IN :ids")
    int deleteByEmailIdIn(@Param("ids") Collection<Long> ids);
}
