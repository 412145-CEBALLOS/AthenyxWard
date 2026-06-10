package com.athenyx.backend.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption helper for sensitive tokens stored in the database
 * (notably the Google OAuth access/refresh tokens on {@code User}).
 *
 * <p>The AES key is derived from {@code app.jwt.secret} via SHA-256, so
 * rotating the JWT secret also rotates the encryption key. Stored
 * values are prefixed with {@code v1:} to make a future key-rotation
 * scheme forward-compatible.</p>
 */
@Service
@Slf4j
public class TokenEncryptionService {

    private static final String VERSION_PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    // private static final int KEY_LENGTH_BITS = 256;
    private static final String DEV_JWT_SECRET_MARKER = "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=";

    private final String jwtSecret;
    private SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenEncryptionService(@Value("${app.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @PostConstruct
    void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret is required to derive the token encryption key");
        }
        try {
            this.key = deriveKey(jwtSecret);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive token encryption key", e);
        }
        if (DEV_JWT_SECRET_MARKER.equals(jwtSecret)) {
            log.warn("app.jwt.secret is the default development value. DO NOT use in production.");
        }
    }

    private SecretKey deriveKey(String secret) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (!stored.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("Stored value is not in v1: format — likely legacy plaintext");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] ciphertext = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token", e);
        }
    }
}
