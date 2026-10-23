package com.tesda.ciicc.banking.backend.exception;

/** The flag was already closed by an earlier review; decisions are one-way. */
public class AnomalyFlagAlreadyReviewedException extends BankingException {
    private static final long serialVersionUID = 1L;

    public AnomalyFlagAlreadyReviewedException(String message) {
        super(message);
    }
}
