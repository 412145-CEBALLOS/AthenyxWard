package com.athenyx.backend.payment;

public record PaymentResult(
    boolean success,
    String providerRef,
    String failureReason
) {}
