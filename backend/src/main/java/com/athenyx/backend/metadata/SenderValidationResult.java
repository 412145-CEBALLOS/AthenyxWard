package com.athenyx.backend.metadata;

public record SenderValidationResult(
    boolean returnPathMismatch,
    boolean replyToMismatch,
    boolean displayMismatch
) {}
