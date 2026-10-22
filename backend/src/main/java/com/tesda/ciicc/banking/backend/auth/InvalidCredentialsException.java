package com.tesda.ciicc.banking.backend.auth;

/** Login failed. The message is deliberately the same for unknown usernames and wrong passwords. */
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
