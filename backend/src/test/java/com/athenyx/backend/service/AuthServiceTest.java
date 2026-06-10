package com.athenyx.backend.service;

import com.athenyx.backend.dto.UserInfo;
import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .googleId("gid")
                .email("u@example.com")
                .name("User")
                .pictureUrl("p")
                .role(Role.PREMIUM)
                .accessibilityMode(true)
                .build();
    }

    @Test
    void getUserInfo_returnsMappedDto() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo info = service.getUserInfo(1L);

        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.name()).isEqualTo("User");
        assertThat(info.email()).isEqualTo("u@example.com");
        assertThat(info.pictureUrl()).isEqualTo("p");
        assertThat(info.role()).isEqualTo(Role.PREMIUM);
        assertThat(info.trialExpired()).isFalse();
        assertThat(info.accessibilityMode()).isTrue();
    }

    @Test
    void getUserInfo_trialExpiredFlagReflectsTrialEndDate() {
        user.setRole(Role.TRIAL);
        user.setTrialEndDate(LocalDateTime.now().minusDays(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo info = service.getUserInfo(1L);

        assertThat(info.trialExpired()).isTrue();
    }

    @Test
    void getUserInfo_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserInfo(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void updateAccessibilityMode_persistsAndReturnsUpdatedInfo() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo info = service.updateAccessibilityMode(1L, false);

        assertThat(user.isAccessibilityMode()).isFalse();
        assertThat(info.accessibilityMode()).isFalse();
        verify(userRepository, times(2)).findById(1L);
    }
}
