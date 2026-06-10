package com.athenyx.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenEncryptionServiceTest {

    @Test
    void encrypt_thenDecrypt_roundTripsPlaintext() {
        TokenEncryptionService svc = new TokenEncryptionService("any-secret-value-for-test");
        svc.init();

        String encrypted = svc.encrypt("hello-world");
        String decrypted = svc.decrypt(encrypted);

        assertThat(decrypted).isEqualTo("hello-world");
        assertThat(encrypted).startsWith("v1:").doesNotContain("hello-world");
    }

    @Test
    void encrypt_handlesNullInput() {
        TokenEncryptionService svc = new TokenEncryptionService("any-secret-value-for-test");
        svc.init();

        assertThat(svc.encrypt(null)).isNull();
        assertThat(svc.decrypt(null)).isNull();
    }

    @Test
    void encrypt_producesUniqueOutput_forSamePlaintext() {
        TokenEncryptionService svc = new TokenEncryptionService("any-secret-value-for-test");
        svc.init();

        String a = svc.encrypt("repeat");
        String b = svc.encrypt("repeat");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void decrypt_rejectsNonV1Format() {
        TokenEncryptionService svc = new TokenEncryptionService("any-secret-value-for-test");
        svc.init();

        assertThatThrownBy(() -> svc.decrypt("plaintext-legacy"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy plaintext");
    }

    @Test
    void decrypt_throwsOnTamperedCiphertext() {
        TokenEncryptionService svc = new TokenEncryptionService("any-secret-value-for-test");
        svc.init();

        String encrypted = svc.encrypt("payload");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        assertThatThrownBy(() -> svc.decrypt(tampered))
                .isInstanceOf(Exception.class);
    }
}
