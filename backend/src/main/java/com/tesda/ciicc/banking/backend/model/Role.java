package com.tesda.ciicc.banking.backend.model;

/**
 * Who a user of the API is. CUSTOMER may only touch accounts listed in
 * {@code account_owners}; ADMIN is the operator role and may touch every
 * account. The check itself lives in {@code BankingService}, not in the web
 * layer, so every caller gets the same rule.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
