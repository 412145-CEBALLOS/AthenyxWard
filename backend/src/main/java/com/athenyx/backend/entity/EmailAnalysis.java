package com.athenyx.backend.entity;

import com.athenyx.backend.heuristics.AnalysisOrigin;
import com.athenyx.backend.heuristics.ThreatLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThreatLevel threatLevel;

    @Column(nullable = false)
    private Integer riskPercentage;

    @Column(columnDefinition = "TEXT")
    private String findings;

    @Column(columnDefinition = "TEXT")
    private String suspiciousUrls;

    @Column(columnDefinition = "TEXT")
    private String senderTrust;

    @Column(columnDefinition = "TEXT")
    private String recommendedActions;

    @Column(columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(columnDefinition = "TEXT")
    private String contentSummary;

    private String modelName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime analyzedAt;
}
