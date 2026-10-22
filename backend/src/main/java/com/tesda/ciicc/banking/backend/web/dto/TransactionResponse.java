package com.tesda.ciicc.banking.backend.web.dto;

import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.web.Money;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One entry of an account's transaction history")
public record TransactionResponse(

        int id,

        @Schema(description = "Unique transaction reference", example = "TXN-lz3k9x00a1b2")
        String reference,

        @Schema(description = "Account number exactly as the request spelled it", example = "100001")
        String accountNumber,

        @Schema(description = "DEPOSIT, WITHDRAW, TRANSFER_OUT (source side) or TRANSFER_IN (destination side)",
                example = "TRANSFER_OUT")
        String type,

        @Schema(description = "Moved amount as a decimal string", type = "string", example = "2000.00")
        String amount,

        @Schema(description = "Balance of the account AFTER this transaction", type = "string", example = "3500.00")
        String balanceAfter,

        @Schema(description = "Other account of a transfer, absent for deposit and withdraw", example = "100002")
        String referenceAccount,

        @Schema(description = "When the transaction was recorded, ISO-8601", example = "2026-10-21T17:05:00Z")
        String createdAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionReference(),
                transaction.getAccountNumber(),
                transaction.getTransactionType().name(),
                Money.format(transaction.getAmount()),
                Money.format(transaction.getBalance()),
                transaction.getReferenceAccount(),
                transaction.getCreatedAt().toInstant().toString());
    }
}
