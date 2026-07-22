package com.athenyx.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_user_status", columnList = "user_id, status"),
    @Index(name = "idx_payment_status_expires", columnList = "status, expires_at"),
    @Index(name = "idx_payment_provider_ref", columnList = "provider, provider_ref"),
    @Index(name = "idx_payment_claim_token", columnList = "claim_token")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BillingCycle billingCycle;

    @Column(nullable = false, length = 32)
    private String planTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 8, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProvider provider;

    @Column(length = 256)
    private String providerRef;

    @Column(length = 100)
    private String externalReference;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime canceledAt;

    private String failureReason;

    @Column(length = 64, unique = true)
    private String claimToken;
}
