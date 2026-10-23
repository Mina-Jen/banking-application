package com.tesda.ciicc.banking.backend.auth;

/** The refresh token was missing, invalid, expired, or belongs to a disabled user. */
public class InvalidRefreshTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
