package com.athenyx.backend.config.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PurgeResultResponse {
    private long purgedCount;
    private Long skippedDueToReminders;
    private LocalDateTime executedAt;
    private long durationMs;
}
