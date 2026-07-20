package com.athenyx.backend.service;

import com.athenyx.backend.audit.AuditEventPublisher;
import com.athenyx.backend.entity.RefreshToken;
import com.athenyx.backend.entity.RevokedReason;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.RefreshTokenRepository;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.security.RefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>The service is responsible for:
 * <ul>
 *     <li>Generating cryptographically random raw tokens, storing only
 *         their SHA-256 hash.</li>
 *     <li>Validating presented tokens against the database, treating
 *         revoked or replayed tokens as reuse-detection events.</li>
 *     <li>Revoking the entire family of a token when reuse is detected
 *         or when the absolute lifetime is exceeded.</li>
 *     <li>Bumping the user-side {@code tokenVersion} on every rotation
 *         so the JWT filter rejects stale access tokens.</li>
 * </ul>
 *
 * <p>All public mutating methods are {@code @Transactional}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int RAW_TOKEN_BYTES = 32;
    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final AuditEventPublisher auditEventPublisher;

    @Autowired
    private ApplicationContext applicationContext;

    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private RefreshTokenService self() {
        return applicationContext.getBean(RefreshTokenService.class);
    }

    @Value("${app.jwt.refresh-expiration-ms:2592000000}")
    private long refreshExpirationMs;

    @Value("${app.jwt.refresh-absolute-expiration-ms:7776000000}")
    private long refreshAbsoluteExpirationMs;

    public record IssuedToken(String raw, RefreshToken row, long newTokenVersion) {}

    @Transactional
    public IssuedToken issue(User user, HttpServletRequest request, long tokenVersion) {
        return issueInternal(user, RefreshToken.newFamilyId(), null, request, tokenVersion);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        byte[] hash = sha256(rawToken);
        Optional<RefreshToken> opt = repository.findByTokenHash(hash);
        if (opt.isEmpty()) {
            log.debug("revoke(): token not found, ignoring");
            return;
        }
        RefreshToken row = opt.get();
        if (row.getRevokedAt() == null) {
            row.setRevokedAt(LocalDateTime.now());
            row.setRevokedReason(RevokedReason.LOGOUT);
            repository.save(row);
        }
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        return repository.revokeAllForUser(userId, RevokedReason.LOGOUT, LocalDateTime.now());
    }

    public User findUserByRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RefreshTokenException(RefreshTokenException.Kind.MISSING, "Refresh token is missing");
        }
        byte[] hash = sha256(rawToken);
        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> new RefreshTokenException(
                        RefreshTokenException.Kind.MISSING, "Refresh token not recognised"));
        return existing.getUser();
    }

    public byte[] hashToken(String rawToken) {
        return sha256(rawToken);
    }

    public Optional<RefreshToken> findByTokenHash(byte[] hash) {
        return repository.findByTokenHash(hash);
    }

    public List<RefreshToken> listActiveSessions(Long userId) {
        return repository.findAllByUserIdAndRevokedAtIsNull(userId);
    }

    @Transactional
    public int revokeFamily(String familyId, Long userId) {
        int count = repository.revokeFamily(familyId, RevokedReason.ADMIN, LocalDateTime.now());
        if (count > 0) {
            userRepository.incrementTokenVersion(userId);
        }
        return count;
    }

    @Transactional
    public User resolveUserForRotation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            self().logTokenRefreshFailed(null, null, "MISSING");
            throw new RefreshTokenException(RefreshTokenException.Kind.MISSING, "Refresh token is missing");
        }
        byte[] hash = sha256(rawToken);
        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> {
                    self().logTokenRefreshFailed(null, null, "NOT_RECOGNISED");
                    return new RefreshTokenException(
                            RefreshTokenException.Kind.MISSING, "Refresh token not recognised");
                });

        LocalDateTime now = LocalDateTime.now();
        User user = existing.getUser();

        if (existing.getRevokedAt() != null) {
            repository.revokeFamily(existing.getFamilyId(), RevokedReason.REUSE_DETECTED, now);
            log.warn("Refresh token reuse detected for user={} family={}",
                    user.getId(), existing.getFamilyId());
            self().logTokenRefreshFailed(user.getId(), user.getEmail(), "REUSE_DETECTED");
            throw new RefreshTokenException(RefreshTokenException.Kind.REUSE_DETECTED,
                    "Refresh token reuse detected; all sessions for this device revoked");
        }

        if (!existing.getAbsoluteExpiresAt().isAfter(now)) {
            existing.setRevokedAt(now);
            existing.setRevokedReason(RevokedReason.EXPIRED);
            repository.save(existing);
            repository.revokeFamily(existing.getFamilyId(), RevokedReason.EXPIRED, now);
            self().logTokenRefreshFailed(user.getId(), user.getEmail(), "ABSOLUTE_EXPIRED");
            throw new RefreshTokenException(RefreshTokenException.Kind.EXPIRED,
                    "Refresh token absolute lifetime exceeded");
        }

        if (!existing.getExpiresAt().isAfter(now)) {
            existing.setRevokedAt(now);
            existing.setRevokedReason(RevokedReason.EXPIRED);
            repository.save(existing);
            self().logTokenRefreshFailed(user.getId(), user.getEmail(), "EXPIRED");
            throw new RefreshTokenException(RefreshTokenException.Kind.EXPIRED,
                    "Refresh token expired");
        }

        if (user.getDeletedAt() != null || !user.isActive()) {
            self().logTokenRefreshFailed(user.getId(), user.getEmail(), "ACCOUNT_DISABLED");
            throw new RefreshTokenException(RefreshTokenException.Kind.ACCOUNT_DISABLED,
                    "User account is disabled or deleted");
        }

        return user;
    }

    @Transactional
    public IssuedToken rotate(String rawToken, User user, HttpServletRequest request) {
        byte[] hash = sha256(rawToken);
        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> new RefreshTokenException(
                        RefreshTokenException.Kind.MISSING, "Refresh token not recognised"));

        existing.setRevokedAt(LocalDateTime.now());
        existing.setRevokedReason(RevokedReason.REPLACED);
        existing.setLastUsedAt(LocalDateTime.now());
        repository.save(existing);

        userRepository.incrementTokenVersion(user.getId());
        long newTokenVersion = userRepository.findTokenVersionById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found after token version increment"));

        IssuedToken issued = issueInternal(user, existing.getFamilyId(), existing.getId(), request, newTokenVersion);
        existing.setReplacedById(issued.row().getId());
        repository.save(existing);

        return issued;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenRefreshFailed(Long actorId, String actorEmail, String kind) {
        auditEventPublisher.publishTokenRefreshFailed(actorId, actorEmail, kind);
    }

    private IssuedToken issueInternal(User user, String familyId, Long replacedById, HttpServletRequest request, long tokenVersion) {
        String raw = generateRawToken();
        byte[] hash = sha256(raw);
        LocalDateTime now = LocalDateTime.now();
        RefreshToken row = RefreshToken.builder()
                .user(user)
                .familyId(familyId)
                .tokenHash(hash)
                .issuedAt(now)
                .expiresAt(now.plusNanos(refreshExpirationMs * 1_000_000L))
                .absoluteExpiresAt(now.plusNanos(refreshAbsoluteExpirationMs * 1_000_000L))
                .replacedById(replacedById)
                .userAgent(truncate(extractUserAgent(request), 255))
                .ip(truncate(extractIp(request), 64))
                .build();
        row = repository.save(row);
        return new IssuedToken(raw, row, tokenVersion);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String extractUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        return request.getHeader("User-Agent");
    }

    private static String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return comma > 0 ? fwd.substring(0, comma).trim() : fwd.trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
