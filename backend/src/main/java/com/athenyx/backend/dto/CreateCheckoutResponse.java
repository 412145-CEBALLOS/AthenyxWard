package com.athenyx.backend.dto;

import java.time.LocalDateTime;

public record CreateCheckoutResponse(
    Long paymentId,
    String redirectUrl,
    LocalDateTime expiresAt,
    String claimToken
) {}
