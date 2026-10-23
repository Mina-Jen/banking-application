package com.tesda.ciicc.banking.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blunts password guessing: after {@code maxFailures} failed logins for the
 * same username + IP within the sliding window, further attempts are rejected
 * with 429 until the window slides on — even if the next password is correct.
 * A successful login clears the caller's failures, because knowing the
 * password is the whole point.
 *
 * <p>Counters live in memory, so they reset on restart and are per instance:
 * enough to slow down a single client, while a multi-node deployment would
 * move this into the shared database. Time comes from an injected
 * {@link Clock} so tests can advance it deterministically.</p>
 */
@Component
public class LoginRateLimiter {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.auth.max-failed-logins:5}") int maxFailures,
                            @Value("${app.auth.failure-window-minutes:15}") long windowMinutes,
                            Clock clock) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
        this.clock = clock;
    }

    /**
     * @throws RateLimitExceededException when the caller must wait; the
     *                                    exception carries the seconds until
     *                                    the oldest failure leaves the window.
     */
    public void checkAllowed(String username, String ip) {
        ArrayDeque<Instant> bucket = failures.get(bucketKey(username, ip));
        if (bucket == null) {
            return;
        }
        synchronized (bucket) {
            prune(bucket);
            if (bucket.size() >= maxFailures) {
                Instant oldest = bucket.peekFirst();
                Instant now = Instant.now(clock);
                long retryAfterSeconds = Math.max(1,
                        window.toSeconds() - Duration.between(oldest, now).toSeconds());
                throw new RateLimitExceededException(retryAfterSeconds);
            }
        }
    }

    public void recordFailure(String username, String ip) {
        ArrayDeque<Instant> bucket = failures.computeIfAbsent(bucketKey(username, ip), key -> new ArrayDeque<>());
        synchronized (bucket) {
            prune(bucket);
            bucket.addLast(Instant.now(clock));
        }
    }

    public void recordSuccess(String username, String ip) {
        failures.remove(bucketKey(username, ip));
    }

    /** Drops failures that are older than the window. */
    private void prune(ArrayDeque<Instant> bucket) {
        Instant cutoff = Instant.now(clock).minus(window);
        while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
            bucket.pollFirst();
        }
    }

    private static String bucketKey(String username, String ip) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return name + "|" + (ip == null ? "?" : ip);
    }
}
