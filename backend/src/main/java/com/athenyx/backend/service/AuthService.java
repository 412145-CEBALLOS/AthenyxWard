package com.athenyx.backend.service;

import com.athenyx.backend.dto.UserInfo;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service for the current user's profile. Persists the
 * accessibility-mode toggle in a single transactional update.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;

    public UserInfo getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return new UserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPictureUrl(),
                user.getRole(),
                user.getTrialEndDate(),
                user.isTrialExpired(),
                user.isAccessibilityMode()
        );
    }

    @Transactional
    public UserInfo updateAccessibilityMode(Long userId, boolean accessibilityMode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setAccessibilityMode(accessibilityMode);
        return getUserInfo(userId);
    }
}
