package com.athenyx.backend.dto;

import com.athenyx.backend.entity.Role;

public record UserSearchResult(
    Long id,
    String name,
    String email,
    String pictureUrl,
    Role role,
    boolean isActive
) {}
