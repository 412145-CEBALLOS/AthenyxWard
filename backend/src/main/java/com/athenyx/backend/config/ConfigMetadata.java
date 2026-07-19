package com.athenyx.backend.config;

import java.util.Optional;

public record ConfigMetadata(
    ConfigCategory category,
    ConfigType type,
    String defaultValue,
    String description,
    Integer minValue,
    Integer maxValue,
    boolean publiclyVisible
) {
    public Optional<Integer> min() {
        return Optional.ofNullable(minValue);
    }

    public Optional<Integer> max() {
        return Optional.ofNullable(maxValue);
    }
}
