package com.athenyx.backend.dto;

import com.athenyx.backend.entity.BillingCycle;
import com.athenyx.backend.entity.PaymentProvider;
import com.athenyx.backend.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
    Long id,
    String planTier,
    PaymentStatus status,
    BigDecimal amount,
    String currency,
    PaymentProvider provider,
    String providerRef,
    BillingCycle billingCycle,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    LocalDateTime expiresAt,
    LocalDateTime canceledAt,
    String failureReason
) {}
