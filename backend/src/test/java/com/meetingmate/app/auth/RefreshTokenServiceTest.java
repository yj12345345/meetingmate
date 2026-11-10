package com.meetingmate.app.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenServiceTest {

    /** Clock the tests can move forward. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-10T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }

    @Autowired RefreshTokenService service;
    @Autowired RefreshTokenRepository repository;
    @Autowired MutableClock clock;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        clock.now = Instant.parse("2026-11-10T00:00:00Z");
    }

    @Test
    void rotationIssuesNewTokenAndRevokesTheOldOne() {
        var first = service.issue(1L);
        var second = service.rotate(first.rawToken());

        assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
        assertThat(second.userId()).isEqualTo(1L);
        RefreshToken old = repository.findByTokenHash(RefreshTokenService.hash(first.rawToken())).orElseThrow();
        assertThat(old.getRevokeReason()).isEqualTo(RefreshToken.RevokeReason.ROTATED);
        // the stored value is a hash, never the raw token
        assertThat(repository.findAll()).noneMatch(t -> t.getTokenHash().equals(first.rawToken()));
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        var stolen = service.issue(1L);
        var legit = service.rotate(stolen.rawToken());   // real user refreshes
        clock.advance(Duration.ofMinutes(5));            // well past the grace window

        assertThatThrownBy(() -> service.rotate(stolen.rawToken()))   // attacker replays
                .isInstanceOf(InvalidRefreshTokenException.class).hasMessage("reused");
        // ...and the legitimate user's current token is dead too: both must log in again
        assertThatThrownBy(() -> service.rotate(legit.rawToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void concurrentRefreshWithinGraceWindowDoesNotLogTheUserOut() {
        var token = service.issue(1L);
        var tabA = service.rotate(token.rawToken());
        clock.advance(Duration.ofSeconds(2));
        var tabB = service.rotate(token.rawToken());     // second tab, same old cookie

        assertThat(tabB.rawToken()).isNotEqualTo(tabA.rawToken());
        assertThat(service.rotate(tabA.rawToken()).userId()).isEqualTo(1L); // family still alive
    }

    @Test
    void expiredUnknownAndMissingTokensAreRejected() {
        var token = service.issue(1L);
        clock.advance(Duration.ofDays(15));
        assertThatThrownBy(() -> service.rotate(token.rawToken())).hasMessage("expired");
        assertThatThrownBy(() -> service.rotate("not-a-real-token")).hasMessage("unknown");
        assertThatThrownBy(() -> service.rotate(null)).hasMessage("missing");
    }

    @Test
    void logoutRevokesTheFamilyAndAccountDeletionRevokesEveryDevice() {
        var laptop = service.issue(1L);
        var phone = service.issue(1L);
        var other = service.issue(2L);

        service.revokeFamilyOf(laptop.rawToken());
        assertThatThrownBy(() -> service.rotate(laptop.rawToken())).hasMessage("revoked");
        assertThat(service.rotate(phone.rawToken()).userId()).isEqualTo(1L);

        service.revokeAllForUser(1L);
        assertThat(repository.findAll()).filteredOn(t -> t.getUserId() == 1L)
                .allMatch(t -> t.getRevokedAt() != null);
        assertThat(service.rotate(other.rawToken()).userId()).isEqualTo(2L);
    }

    @Test
    void purgeDeletesOnlyLongExpiredTokens() {
        service.issue(1L);
        clock.advance(Duration.ofDays(16));
        service.issue(2L);

        service.purgeExpired();

        assertThat(repository.findAll()).extracting(RefreshToken::getUserId).containsExactly(2L);
    }
}
