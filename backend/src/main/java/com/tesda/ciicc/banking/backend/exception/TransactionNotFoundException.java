package com.tesda.ciicc.banking.backend.exception;

/** The account exists and is the caller's, but it has no transaction with that id. */
public class TransactionNotFoundException extends BankingException {
    private static final long serialVersionUID = 1L;

    public TransactionNotFoundException(String message) {
        super(message);
    }
}
