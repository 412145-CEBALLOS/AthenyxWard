package com.athenyx.backend.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SenderTrustLevelTest {

    @Test
    void fromScore_trusted() {
        assertThat(SenderTrustLevel.fromScore(70)).isEqualTo(SenderTrustLevel.TRUSTED);
        assertThat(SenderTrustLevel.fromScore(100)).isEqualTo(SenderTrustLevel.TRUSTED);
        assertThat(SenderTrustLevel.fromScore(85)).isEqualTo(SenderTrustLevel.TRUSTED);
    }

    @Test
    void fromScore_unknown() {
        assertThat(SenderTrustLevel.fromScore(40)).isEqualTo(SenderTrustLevel.UNKNOWN);
        assertThat(SenderTrustLevel.fromScore(69)).isEqualTo(SenderTrustLevel.UNKNOWN);
        assertThat(SenderTrustLevel.fromScore(55)).isEqualTo(SenderTrustLevel.UNKNOWN);
    }

    @Test
    void fromScore_suspicious() {
        assertThat(SenderTrustLevel.fromScore(39)).isEqualTo(SenderTrustLevel.SUSPICIOUS);
        assertThat(SenderTrustLevel.fromScore(0)).isEqualTo(SenderTrustLevel.SUSPICIOUS);
        assertThat(SenderTrustLevel.fromScore(20)).isEqualTo(SenderTrustLevel.SUSPICIOUS);
    }
}
