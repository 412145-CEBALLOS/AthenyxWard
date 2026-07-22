package com.athenyx.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(
    @NotNull Long paymentId,
    String token,
    String claimToken
) {}
