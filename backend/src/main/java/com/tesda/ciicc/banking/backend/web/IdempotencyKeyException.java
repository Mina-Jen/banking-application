package com.tesda.ciicc.banking.backend.web;

/** The Idempotency-Key header is missing, blank, or too long. */
public class IdempotencyKeyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyException(String message) {
        super(message);
    }
}
