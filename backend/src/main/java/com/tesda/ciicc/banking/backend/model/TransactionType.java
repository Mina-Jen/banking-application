package com.tesda.ciicc.banking.backend.model;

public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER_OUT,   // source account's side of a transfer
    TRANSFER_IN     // destination account's side of a transfer
}
