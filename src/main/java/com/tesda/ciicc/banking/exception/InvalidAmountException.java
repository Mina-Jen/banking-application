package com.tesda.ciicc.banking.exception;

public class InvalidAmountException extends BankingException {
    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String message) {
        super(message);
    }
}