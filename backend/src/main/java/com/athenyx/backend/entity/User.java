package com.athenyx.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Application user, one row per Google account.
 *
 * <p>Identity is anchored on {@link #googleId} (the OIDC {@code sub}
 * claim). The {@link #googleAccessToken} and {@link #googleRefreshToken}
 * are AES-GCM encrypted at rest via
 * {@link com.athenyx.backend.security.TokenEncryptionService}.</p>
 *
 * <p>{@link #tokenVersion} is bumped on every login and every refresh
 * rotation; access JWTs encode the version they were issued at and are
 * rejected as 401 if the user-side counter no longer matches.</p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String googleId;

    @Column(nullable = false)
    private String email;

    private String name;

    private String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.TRIAL;

    private LocalDateTime trialEndDate;

    @Column(length = 1024)
    private String googleAccessToken;

    @Column(length = 1024)
    private String googleRefreshToken;

    private LocalDateTime googleAccessTokenExpiresAt;

    @Builder.Default
    private int analysisCount = 0;

    @Builder.Default
    private boolean accessibilityMode = true;

    private LocalDateTime termsAcceptedAt;

    private String termsVersion;

    @Column(nullable = false)
    @Builder.Default
    private long tokenVersion = 0L;

    @Builder.Default
    private boolean isActive = true;

    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;

    private Boolean emailVerified;

    private String gmailHistoryId;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Email> emails = new ArrayList<>();

    /**
     * True only when the user is on the {@link Role#TRIAL} plan and
     * the trial window has passed. {@code PREMIUM} and {@code ADMIN}
     * users never see a trial-expired state — even if a stale
     * {@code trialEndDate} is left over from a previous role change.
     */
    public boolean isTrialExpired() {
        return role == Role.TRIAL
            && trialEndDate != null
            && LocalDateTime.now().isAfter(trialEndDate);
    }
}
