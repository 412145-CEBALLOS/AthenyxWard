package com.athenyx.backend.metadata;

import java.util.List;

public record MetadataAnalysisResult(
    ExtractedHeaders headers,
    SenderValidationResult senderValidation,
    TimestampAnalysisResult timestampAnalysis,
    int trustScore,
    SenderTrustLevel trustLevel,
    List<SenderTrustSignal> signals,
    List<String> anomalies
) {}
