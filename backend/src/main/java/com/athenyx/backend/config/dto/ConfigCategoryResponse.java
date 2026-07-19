package com.athenyx.backend.config.dto;

import com.athenyx.backend.config.ConfigCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConfigCategoryResponse {
    private ConfigCategory category;
    private String categoryLabel;
    private List<ConfigEntryResponse> entries;
}
