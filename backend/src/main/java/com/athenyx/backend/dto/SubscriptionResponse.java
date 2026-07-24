package com.athenyx.backend.dto;

import com.athenyx.backend.entity.BillingCycle;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionResponse(
    String planTier,
    String status,
    LocalDateTime startedAt,
    LocalDateTime renewsAt,
    LocalDateTime canceledAt,
    String paymentMethod,
    boolean autoRenew,
    BillingCycle billingCycle,
    BigDecimal priceAmount,
    String priceCurrency,
    String annualSavingsPercent,
    String enabledProviders,
    boolean cancelAtPeriodEnd
) {}
