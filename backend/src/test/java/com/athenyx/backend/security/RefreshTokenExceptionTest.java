package com.athenyx.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenExceptionTest {

    @Test
    void messageAndKindArePreserved() {
        RefreshTokenException ex = new RefreshTokenException(
                RefreshTokenException.Kind.REUSE_DETECTED, "boom");

        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getKind()).isEqualTo(RefreshTokenException.Kind.REUSE_DETECTED);
    }

    @Test
    void allKindsAreAvailable() {
        for (RefreshTokenException.Kind kind : RefreshTokenException.Kind.values()) {
            RefreshTokenException ex = new RefreshTokenException(kind, "msg-" + kind);
            assertThat(ex.getKind()).isEqualTo(kind);
            assertThat(ex.getMessage()).isEqualTo("msg-" + kind);
        }
    }
}
