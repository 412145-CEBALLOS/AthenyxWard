package com.athenyx.backend.metadata;

public record MassMailingResult(boolean isMassMailing, MassMailingProvider provider, String reason) {}
