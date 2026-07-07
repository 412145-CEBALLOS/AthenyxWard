package com.athenyx.backend.dto;

import com.athenyx.backend.ai.AiOrigin;
import jakarta.annotation.Nullable;

import java.time.LocalDateTime;

public record AiExplanationResponse(
        Long id,
        String text,
        AiOrigin origin,
        @Nullable String modelName,
        LocalDateTime generatedAt
) {}
