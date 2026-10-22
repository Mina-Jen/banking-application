package com.tesda.ciicc.banking.backend.web;

import com.tesda.ciicc.banking.backend.exception.BankingException;

/** Work handed to {@link IdempotencyService#execute}, allowed to throw the domain exceptions. */
@FunctionalInterface
public interface BankingCall<T> {

    T call() throws BankingException;
}
