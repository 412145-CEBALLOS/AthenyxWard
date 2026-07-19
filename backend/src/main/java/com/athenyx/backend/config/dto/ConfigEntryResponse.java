package com.athenyx.backend.config.dto;

import com.athenyx.backend.config.ConfigCategory;
import com.athenyx.backend.config.ConfigType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConfigEntryResponse {
    private String key;
    private String value;
    private ConfigType type;
    private String description;
    private ConfigCategory category;
    private Integer minValue;
    private Integer maxValue;
    private boolean publiclyVisible;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
