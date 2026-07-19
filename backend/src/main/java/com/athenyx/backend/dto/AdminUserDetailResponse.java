package com.athenyx.backend.dto;

import com.athenyx.backend.entity.Role;

import java.time.LocalDateTime;

public record AdminUserDetailResponse(
    Long id,
    String name,
    String email,
    String pictureUrl,
    String googleId,
    Role role,
    LocalDateTime trialEndDate,
    int analysisCount,
    LocalDateTime lastLogin,
    LocalDateTime createdAt,
    boolean isActive,
    LocalDateTime deletedAt,
    long emailCount,
    long reminderCount
) {}
