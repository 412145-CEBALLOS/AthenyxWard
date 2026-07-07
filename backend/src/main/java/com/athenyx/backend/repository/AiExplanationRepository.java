package com.athenyx.backend.repository;

import com.athenyx.backend.entity.AiExplanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiExplanationRepository extends JpaRepository<AiExplanation, Long> {

    Optional<AiExplanation> findFirstByEmailIdAndUserIdOrderByGeneratedAtDesc(Long emailId, Long userId);
}
