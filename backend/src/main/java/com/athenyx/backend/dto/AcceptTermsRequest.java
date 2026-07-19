package com.athenyx.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/accept-terms}.
 * The version field is mandatory and must not exceed 32 characters.
 */
public record AcceptTermsRequest(
    @NotBlank(message = "La versi\u00f3n no puede estar vac\u00eda")
    @Size(max = 32, message = "La versi\u00f3n no puede superar los 32 caracteres")
    String version
) {}
