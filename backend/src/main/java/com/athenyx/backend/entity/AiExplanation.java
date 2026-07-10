package com.athenyx.backend.entity;

import com.athenyx.backend.ai.AiOrigin;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_explanations", indexes = {
    @Index(name = "idx_ai_explanations_user_email_time",
           columnList = "user_id, email_id, generated_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "heuristic_explanation", columnDefinition = "TEXT")
    private String heuristicExplanation;

    @Column(name = "second_opinion", columnDefinition = "TEXT")
    private String secondOpinion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiOrigin origin;

    @Column(name = "model_name", length = 64)
    private String modelName;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;
}
