package com.athenyx.backend.payment;

public record CheckoutSession(
    String providerRef,
    String externalReference,
    String redirectUrl,
    java.time.LocalDateTime expiresAt
) {}
