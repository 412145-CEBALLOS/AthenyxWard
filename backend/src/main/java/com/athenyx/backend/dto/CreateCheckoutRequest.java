package com.athenyx.backend.dto;

import com.athenyx.backend.entity.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCheckoutRequest(
    @NotBlank String provider,
    @NotNull BillingCycle billingCycle,
    String planTier
) {}
