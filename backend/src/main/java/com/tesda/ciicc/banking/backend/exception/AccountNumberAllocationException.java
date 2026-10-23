package com.tesda.ciicc.banking.backend.exception;

/**
 * The bank could not hand out an account number after its allotted tries. This
 * is not the customer's fault and carries nothing to correct, so it answers 503
 * with a retry rather than the "already exists" 409 a customer must never see.
 */
public class AccountNumberAllocationException extends BankingException {
    private static final long serialVersionUID = 1L;

    public AccountNumberAllocationException(String message) {
        super(message);
    }
}
