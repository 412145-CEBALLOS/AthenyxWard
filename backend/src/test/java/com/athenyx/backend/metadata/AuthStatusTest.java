package com.athenyx.backend.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthStatusTest {

    @Test
    void fromString_returnsCorrectEnum() {
        assertThat(AuthStatus.fromString("pass")).isEqualTo(AuthStatus.PASS);
        assertThat(AuthStatus.fromString("PASS")).isEqualTo(AuthStatus.PASS);
        assertThat(AuthStatus.fromString("fail")).isEqualTo(AuthStatus.FAIL);
        assertThat(AuthStatus.fromString("neutral")).isEqualTo(AuthStatus.NEUTRAL);
        assertThat(AuthStatus.fromString("none")).isEqualTo(AuthStatus.NONE);
        assertThat(AuthStatus.fromString("softfail")).isEqualTo(AuthStatus.SOFTFAIL);
    }

    @Test
    void fromString_returnsNoneForNull() {
        assertThat(AuthStatus.fromString(null)).isEqualTo(AuthStatus.NONE);
        assertThat(AuthStatus.fromString("")).isEqualTo(AuthStatus.NONE);
        assertThat(AuthStatus.fromString("   ")).isEqualTo(AuthStatus.NONE);
    }

    @Test
    void fromString_returnsNoneForUnknown() {
        assertThat(AuthStatus.fromString("unknown_value")).isEqualTo(AuthStatus.NONE);
    }
}
