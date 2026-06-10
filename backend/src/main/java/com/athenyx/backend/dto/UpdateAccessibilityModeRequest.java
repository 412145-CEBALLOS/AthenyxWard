package com.athenyx.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/auth/me/accessibility-mode}. The flag is
 * mandatory — null is rejected by Bean Validation.
 */
public record UpdateAccessibilityModeRequest(@NotNull Boolean accessibilityMode) {
}
