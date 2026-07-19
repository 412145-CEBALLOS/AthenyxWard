package com.athenyx.backend.dto;

import java.time.LocalDateTime;

public record ResetTrialResponse(
    LocalDateTime trialEndDate,
    int analysisCount
) {}
