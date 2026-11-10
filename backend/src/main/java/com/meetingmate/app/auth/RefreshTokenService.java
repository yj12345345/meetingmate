package com.meetingmate.app.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Refresh tokens with rotation and reuse detection.
 *
 * <ul>
 *   <li>Opaque 256-bit random value (not a JWT): the server keeps state anyway, so a
 *       signature adds nothing, and revocation is a row update.</li>
 *   <li>Every use rotates: the presented token is revoked and a new one is issued in
 *       the same family.</li>
 *   <li>Reuse detection: presenting an already-rotated token means two parties hold
 *       the same token (one of them stole it), so the whole family is revoked.</li>
 *   <li>Grace window: two tabs refreshing at the same moment would look like reuse; a
 *       token rotated less than {@code grace} ago gets a fresh token instead of a
 *       family-wide logout.</li>
 * </ul>
 */
@Slf4j
@Service
public class RefreshTokenService {

    public record Issued(String rawToken, Long userId, Duration ttl) {
    }

    private static final int PURGE_BATCH = 1000;

    private final RefreshTokenRepository repository;
    private final Duration ttl;
    private final Duration reuseGrace;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${auth.refresh-token.ttl:14d}") Duration ttl,
                               @Value("${auth.refresh-token.reuse-grace:10s}") Duration reuseGrace,
                               Clock clock) {
        this.repository = repository;
        this.ttl = ttl;
        this.reuseGrace = reuseGrace;
        this.clock = clock;
    }

    /** New login: starts a new token family. */
    @Transactional
    public Issued issue(Long userId) {
        return issueInFamily(userId, UUID.randomUUID().toString());
    }

    /**
     * Exchanges a refresh token for a new one. Not read-only on failure paths:
     * reuse detection must commit the family revocation even though we then reject.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Issued rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("missing");
        }
        Instant now = clock.instant();
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("unknown"));

        if (token.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException("expired");
        }

        if (token.getRevokedAt() == null
                && repository.revokeIfActive(token.getId(), now, RefreshToken.RevokeReason.ROTATED) == 1) {
            return issueInFamily(token.getUserId(), token.getFamilyId());
        }

        // Already revoked (possibly by a concurrent request that won the update above).
        RefreshToken current = repository.findById(token.getId()).orElseThrow();
        boolean justRotated = current.getRevokeReason() == RefreshToken.RevokeReason.ROTATED
                && current.getRevokedAt() != null
                && current.getRevokedAt().isAfter(now.minus(reuseGrace));
        if (justRotated) {
            return issueInFamily(current.getUserId(), current.getFamilyId());
        }
        if (current.getRevokeReason() == RefreshToken.RevokeReason.ROTATED) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now, RefreshToken.RevokeReason.REUSE_DETECTED);
            log.warn("Refresh token reuse detected: userId={} family={} revokedActive={}",
                    current.getUserId(), current.getFamilyId(), revoked);
            throw new InvalidRefreshTokenException("reused");
        }
        throw new InvalidRefreshTokenException("revoked");
    }

    /** Logout: revoke every token of this login (family); unknown tokens are ignored. */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(t ->
                repository.revokeFamily(t.getFamilyId(), clock.instant(), RefreshToken.RevokeReason.LOGOUT));
    }

    /** Account deletion: no device of this user may obtain a new access token. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, clock.instant(), RefreshToken.RevokeReason.ACCOUNT_DELETED);
    }

    /**
     * Daily cleanup of tokens expired for more than a day, in small batches: a single
     * huge DELETE is what OOM-killed our 512MB MySQL during the load-test cleanup.
     */
    @Scheduled(cron = "${auth.refresh-token.purge-cron:0 30 4 * * *}")
    public void purgeExpired() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(1));
        int total = 0;
        List<Long> ids;
        while (!(ids = repository.findExpiredIds(cutoff, PageRequest.of(0, PURGE_BATCH))).isEmpty()) {
            total += repository.deleteByIds(ids); // one short transaction per batch
        }
        if (total > 0) {
            log.info("Purged {} expired refresh tokens", total);
        }
    }

    private Issued issueInFamily(Long userId, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        repository.save(new RefreshToken(userId, hash(raw), familyId, now, now.plus(ttl)));
        return new Issued(raw, userId, ttl);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
