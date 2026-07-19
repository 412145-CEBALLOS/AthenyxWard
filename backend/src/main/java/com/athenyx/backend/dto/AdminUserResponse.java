package com.athenyx.backend.dto;

import com.athenyx.backend.entity.Role;

import java.time.LocalDateTime;

public record AdminUserResponse(
    Long id,
    String name,
    String email,
    String pictureUrl,
    Role role,
    LocalDateTime trialEndDate,
    int analysisCount,
    LocalDateTime lastLogin,
    boolean isActive,
    LocalDateTime createdAt
) {}
