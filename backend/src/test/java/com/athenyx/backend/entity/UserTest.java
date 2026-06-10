package com.athenyx.backend.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void isTrialExpired_returnsFalse_whenTrialEndDateIsNull() {
        User user = User.builder().trialEndDate(null).build();

        assertThat(user.isTrialExpired()).isFalse();
    }

    @Test
    void isTrialExpired_returnsFalse_whenTrialEndDateInFuture() {
        User user = User.builder()
                .trialEndDate(LocalDateTime.now().plusDays(1))
                .build();

        assertThat(user.isTrialExpired()).isFalse();
    }

    @Test
    void isTrialExpired_returnsTrue_whenTrialEndDateInPast() {
        User user = User.builder()
                .trialEndDate(LocalDateTime.now().minusSeconds(1))
                .build();

        assertThat(user.isTrialExpired()).isTrue();
    }

    @Test
    void defaultRole_isTrial() {
        User user = User.builder().build();

        assertThat(user.getRole()).isEqualTo(Role.TRIAL);
    }

    @Test
    void defaults_areAppliedByBuilder() {
        User user = User.builder().build();

        assertThat(user.isAccessibilityMode()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(0L);
        assertThat(user.getAnalysisCount()).isZero();
        assertThat(user.getEmails()).isEmpty();
    }
}
