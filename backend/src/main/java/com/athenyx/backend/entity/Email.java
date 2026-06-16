package com.athenyx.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persisted Gmail message — analysis-ready subset only.
 *
 * <p>The raw MIME body is <strong>not</strong> kept: only the
 * sender/subject, plain-text content capped at 10 000 characters
 * ({@code contentForAnalysis}), HTML preview, extracted URLs, snippet
 * and the read flag. See {@code SPEC.md} § Email Storage.</p>
 */
@Entity
@Table(name = "emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String gmailId;

    @Column(nullable = false)
    private String sender;

    private String senderName;

    @Column(nullable = false)
    private String subject;

    private String snippet;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String contentForAnalysis;

    @Column(columnDefinition = "LONGTEXT")
    private String htmlContent;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedUrls;

    private LocalDateTime receivedAt;

    private String originalDateHeader;

    private boolean isRead;

    @Builder.Default
    private boolean isImportant = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime fetchedAt;
}
