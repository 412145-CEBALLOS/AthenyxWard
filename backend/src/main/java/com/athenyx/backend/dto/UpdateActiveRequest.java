package com.athenyx.backend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateActiveRequest(
    @NotNull Boolean active
) {}
