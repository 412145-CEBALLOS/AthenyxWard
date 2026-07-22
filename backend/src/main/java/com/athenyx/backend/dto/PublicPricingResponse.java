package com.athenyx.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record PublicPricingResponse(
    BigDecimal monthlyPrice,
    BigDecimal annualPrice,
    String currency,
    int annualSavingsPercent,
    List<String> enabledProviders
) {}
