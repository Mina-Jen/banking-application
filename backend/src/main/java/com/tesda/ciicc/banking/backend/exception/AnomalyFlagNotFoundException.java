package com.tesda.ciicc.banking.backend.exception;

/** No anomaly flag has that id. */
public class AnomalyFlagNotFoundException extends BankingException {
    private static final long serialVersionUID = 1L;

    public AnomalyFlagNotFoundException(String message) {
        super(message);
    }
}
