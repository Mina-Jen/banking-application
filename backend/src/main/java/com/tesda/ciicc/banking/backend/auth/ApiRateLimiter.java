package com.tesda.ciicc.banking.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps how fast one signed-in user can call the money endpoints: 60 requests
 * per rolling minute by default, then 429 with a Retry-After until the oldest
 * request leaves the window.
 *
 * <p>Counting is per user id, not per IP or per token: a client that refreshes
 * its token does not get a fresh budget, and two customers behind one address
 * do not share one. The window starts at a user's first request and lasts
 * {@code window}; when it elapses the count starts over.</p>
 *
 * <p>Counters live in memory, so they reset on restart and are per instance —
 * enough to keep one script from hammering the API, while a multi-node
 * deployment would move this into shared storage. Time comes from an injected
 * {@link Clock} so tests can move the window deterministically.</p>
 */
@Component
public class ApiRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<Integer, Counter> counters = new ConcurrentHashMap<>();

    public ApiRateLimiter(@Value("${app.api.max-requests:60}") int maxRequests,
                          @Value("${app.api.window-minutes:1}") long windowMinutes,
                          Clock clock) {
        this.maxRequests = maxRequests;
        this.window = Duration.ofMinutes(windowMinutes);
        this.clock = clock;
    }

    /**
     * Counts one request for the user.
     *
     * @throws RateLimitExceededException when the budget for this window is
     *                                    spent; it carries the seconds until
     *                                    the window resets. A rejected request
     *                                    does not extend the window.
     */
    public void checkAllowed(int userId) {
        Instant now = Instant.now(clock);
        Counter counter = counters.computeIfAbsent(userId, id -> new Counter(now));

        synchronized (counter) {
            if (Duration.between(counter.windowStart, now).compareTo(window) >= 0) {
                counter.windowStart = now;
                counter.count = 0;
            }
            if (counter.count >= maxRequests) {
                long retryAfterSeconds = Math.max(1,
                        window.toSeconds() - Duration.between(counter.windowStart, now).toSeconds());
                throw new RateLimitExceededException(retryAfterSeconds,
                        "Too many requests. Try again in " + retryAfterSeconds + " seconds.");
            }
            counter.count++;
        }

        forgetOldWindows(now);
    }

    /** Keeps the map from growing with every user who ever called the API. */
    private void forgetOldWindows(Instant now) {
        if (counters.size() > 2048) {
            counters.entrySet().removeIf(entry -> entry.getValue().isStale(now, window));
        }
    }

    private static final class Counter {
        private Instant windowStart;
        private int count;

        private Counter(Instant windowStart) {
            this.windowStart = windowStart;
        }

        private synchronized boolean isStale(Instant now, Duration window) {
            return Duration.between(windowStart, now).compareTo(window) >= 0;
        }
    }
}
