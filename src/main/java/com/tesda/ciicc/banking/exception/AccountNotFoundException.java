package com.tesda.ciicc.banking.exception;

public class AccountNotFoundException extends BankingException {
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String message) {
        super(message);
    }
}