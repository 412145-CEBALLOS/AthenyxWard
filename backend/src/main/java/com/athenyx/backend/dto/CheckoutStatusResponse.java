package com.athenyx.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CheckoutStatusResponse(
    Long paymentId,
    String status,
    String provider,
    BigDecimal amount,
    String currency,
    String billingCycle,
    LocalDateTime createdAt,
    LocalDateTime expiresAt
) {}
