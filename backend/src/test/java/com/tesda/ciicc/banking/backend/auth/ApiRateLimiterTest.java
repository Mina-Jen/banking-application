package com.tesda.ciicc.banking.backend.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-user request budget, with a clock the test moves by hand so the
 * window boundary is deterministic rather than a race against real time.
 */
class ApiRateLimiterTest {

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
    private final ApiRateLimiter limiter = new ApiRateLimiter(60, 1, clock);

    private void useBudget(int userId, int requests) {
        for (int i = 0; i < requests; i++) {
            limiter.checkAllowed(userId);
        }
    }

    @Test
    void sixtyRequestsAreAllowedAndTheSixtyFirstIsRejected() {
        useBudget(1, 60);

        RateLimitExceededException e = assertThrows(RateLimitExceededException.class,
                () -> limiter.checkAllowed(1));

        assertEquals("Too many requests. Try again in 60 seconds.", e.getMessage());
        assertEquals(60, e.getRetryAfterSeconds());
    }

    @Test
    void theBudgetStartsOverWhenTheWindowEnds() {
        useBudget(1, 60);
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1));

        clock.advance(Duration.ofMinutes(1));

        useBudget(1, 60);   // a full fresh minute
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1));
    }

    @Test
    void rejectedRequestsDoNotExtendTheWindow() {
        useBudget(1, 60);
        clock.advance(Duration.ofSeconds(30));
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1));
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1));

        clock.advance(Duration.ofSeconds(30));   // one minute after the window opened
        assertDoesNotThrow(() -> limiter.checkAllowed(1));
    }

    @Test
    void eachUserHasTheirOwnBudget() {
        useBudget(7, 60);
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(7));

        assertDoesNotThrow(() -> limiter.checkAllowed(8));
    }

    @Test
    void retryAfterCountsDownToTheEndOfTheWindow() {
        useBudget(1, 60);
        clock.advance(Duration.ofSeconds(45));

        RateLimitExceededException e = assertThrows(RateLimitExceededException.class,
                () -> limiter.checkAllowed(1));

        assertEquals(15, e.getRetryAfterSeconds());
    }

    @Test
    void retryAfterIsNeverZero() {
        useBudget(1, 60);
        // A fractional millisecond before the window ends still rounds up to 1.
        clock.advance(Duration.ofMillis(59_999));

        RateLimitExceededException e = assertThrows(RateLimitExceededException.class,
                () -> limiter.checkAllowed(1));

        assertTrue(e.getRetryAfterSeconds() >= 1, "was " + e.getRetryAfterSeconds());
    }
}
