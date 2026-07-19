package com.athenyx.backend.config.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResponse {
    private String error;
    private String field;
    private String rejectedValue;
}
