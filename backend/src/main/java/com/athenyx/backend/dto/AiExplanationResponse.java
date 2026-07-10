package com.athenyx.backend.dto;

import com.athenyx.backend.ai.AiOrigin;
import jakarta.annotation.Nullable;

import java.time.LocalDateTime;

public record AiExplanationResponse(
        Long id,
        @Nullable String summary,
        @Nullable String heuristicExplanation,
        @Nullable String secondOpinion,
        AiOrigin origin,
        @Nullable String modelName,
        LocalDateTime generatedAt
) {}
