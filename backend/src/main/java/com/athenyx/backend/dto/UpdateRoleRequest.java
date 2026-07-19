package com.athenyx.backend.dto;

import com.athenyx.backend.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @NotNull Role role
) {}
