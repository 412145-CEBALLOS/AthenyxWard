package com.athenyx.backend.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MpMerchantOrderSummary(
        Long id,
        List<Long> paymentIds
) {
    public MpMerchantOrderSummary {
        if (id == null) {
            throw new MpApiException("MP merchant_order response missing required field: id");
        }
        if (paymentIds == null) {
            paymentIds = List.of();
        }
    }
}
