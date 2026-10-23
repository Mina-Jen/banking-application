package com.tesda.ciicc.banking.backend.auth;

/** The caller must wait before trying again; the 429 carries a Retry-After. */
public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    /** Login is blocked until the failure window slides on. */
    public RateLimitExceededException(long retryAfterSeconds) {
        this(retryAfterSeconds, "Too many failed login attempts. Try again in "
                + retryAfterSeconds + " seconds.");
    }

    /** Any other per-caller budget, such as the per-user API request limit. */
    public RateLimitExceededException(long retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
