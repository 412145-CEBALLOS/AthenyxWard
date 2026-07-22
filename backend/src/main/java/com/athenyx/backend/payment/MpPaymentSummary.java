package com.athenyx.backend.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MpPaymentSummary(
        Long id,
        String status,
        @JsonProperty("external_reference") String externalReference
) {
    public MpPaymentSummary {
        if (id == null) {
            throw new MpApiException("MP payment response missing required field: id");
        }
    }
}
