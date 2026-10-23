package com.tesda.ciicc.banking.backend.web;

/**
 * The Idempotency-Key was already used for a different request. Replaying the
 * same request with the same key is not a conflict; it returns the stored
 * response.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
