package com.athenyx.backend.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateConfigRequest {
    @NotBlank(message = "El valor no puede estar vacío")
    private String value;
}
