package com.tesda.ciicc.banking.exception;

public class InsufficientFundsException extends BankingException {
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String message) {
        super(message);
    }
}