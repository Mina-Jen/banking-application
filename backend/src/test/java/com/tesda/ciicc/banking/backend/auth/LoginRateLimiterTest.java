package com.tesda.ciicc.banking.backend.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The limiter's own rules, with a clock the test moves forward manually, so
 * the window behaviour is deterministic.
 */
class LoginRateLimiterTest {

    /** A clock the tests can advance by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-22T08:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(5, 15, clock);

    @Test
    void fiveFailuresBlockTheCallerEvenWithTheRightPassword() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("carol", "1.2.3.4");
        }

        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed("carol", "1.2.3.4"));
    }

    @Test
    void blockedCallerMayTryAgainAfterTheWindowSlidesOn() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("carol", "1.2.3.4");
        }
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed("carol", "1.2.3.4"));

        clock.advance(Duration.ofMinutes(16));   // the failures age out of the 15-minute window
        assertDoesNotThrow(() -> limiter.checkAllowed("carol", "1.2.3.4"));
    }

    @Test
    void successClearsTheFailures() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("carol", "1.2.3.4");
        }

        limiter.recordSuccess("carol", "1.2.3.4");

        assertDoesNotThrow(() -> limiter.checkAllowed("carol", "1.2.3.4"));
    }

    @Test
    void bucketsAreSeparatePerUsernameAndIp() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("carol", "1.2.3.4");
        }

        assertDoesNotThrow(() -> limiter.checkAllowed("carol", "5.6.7.8"));
        assertDoesNotThrow(() -> limiter.checkAllowed("dan", "1.2.3.4"));
    }

    @Test
    void retryAfterCountsDownFromTheOldestFailure() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("carol", "1.2.3.4");
        }
        clock.advance(Duration.ofMinutes(10));

        RateLimitExceededException e = assertThrows(RateLimitExceededException.class,
                () -> limiter.checkAllowed("carol", "1.2.3.4"));

        // Oldest failure is 10 minutes old in a 15-minute window: 5 minutes left.
        assertTrue(e.getRetryAfterSeconds() > 4 * 60 && e.getRetryAfterSeconds() <= 5 * 60,
                "expected about 5 minutes, was " + e.getRetryAfterSeconds() + "s");
    }
}
