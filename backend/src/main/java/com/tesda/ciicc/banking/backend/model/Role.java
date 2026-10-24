package com.tesda.ciicc.banking.backend.model;

/**
 * Who a user of the API is. CUSTOMER may only touch accounts listed in
 * {@code account_owners}; ADMIN is the operator role and may read every
 * account but never deposit, withdraw or transfer. The checks live in
 * {@code BankingService}, not in the web layer, so every caller gets the same rule.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
