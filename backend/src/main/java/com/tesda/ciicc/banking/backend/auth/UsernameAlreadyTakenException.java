package com.tesda.ciicc.banking.backend.auth;

/** Registration with a username that is already in use. */
public class UsernameAlreadyTakenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UsernameAlreadyTakenException(String message) {
        super(message);
    }
}
