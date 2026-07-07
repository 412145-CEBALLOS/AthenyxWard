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

    @Column(columnDefinition = "TEXT")
    private String senderName;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String contentForAnalysis;

    @Column(columnDefinition = "LONGTEXT")
    private String htmlContent;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedUrls;

    private LocalDateTime receivedAt;

    @Column(columnDefinition = "TEXT")
    private String originalDateHeader;

    private boolean isRead;

    @Builder.Default
    private boolean isImportant = false;

    @Builder.Default
    private boolean isHidden = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime fetchedAt;

    @Column(columnDefinition = "TEXT")
    private String returnPath;

    @Column(columnDefinition = "TEXT")
    private String replyTo;

    @Column(columnDefinition = "TEXT")
    private String receivedHeaders;

    @Column(columnDefinition = "TEXT")
    private String spfStatus;

    @Column(columnDefinition = "TEXT")
    private String dkimStatus;

    @Column(columnDefinition = "TEXT")
    private String dmarcStatus;

    @Column(columnDefinition = "TEXT")
    private String listUnsubscribe;

    @Column(columnDefinition = "TEXT")
    private String xMailer;

    @Column(columnDefinition = "TEXT")
    private String originalTimezone;

    @Column(columnDefinition = "TEXT")
    private String attachmentsMeta;
}
